# LOG_DECISOES_TECNICAS.md

# Log de Decisões Técnicas — Grain Weighing Platform MVP

Este documento registra **como conduzi as principais decisões técnicas do projeto**, incluindo hipóteses iniciais, alternativas consideradas, revisões e critérios de validação.

A IA foi utilizada como ferramenta de apoio para questionar decisões, explorar alternativas e identificar riscos. **A responsabilidade técnica pelas escolhas descritas aqui é minha.**

---

## 1. Meu processo de decisão

Minha abordagem foi:

```text
entender o requisito
        ↓
identificar o risco técnico principal
        ↓
formular uma hipótese
        ↓
comparar alternativas
        ↓
usar IA para desafiar a hipótese
        ↓
decidir com base em escopo e trade-offs
        ↓
validar com testes, simulação ou benchmark
```

Eu não tratei sugestões de IA como requisitos ou decisões prontas.

Sempre que uma alternativa adicionava complexidade, usei a seguinte pergunta:

> **Qual problema concreto do desafio justifica essa complexidade agora?**

Se a resposta não fosse clara, a solução seria simplificada ou documentada apenas como evolução futura.

### Índice

- LOG-001 — Identificação do core do problema
- LOG-002 — Arquitetura inicialmente orientada a eventos
- LOG-003 — Resultado em tempo real versus processamento assíncrono
- LOG-004 — Hot path de telemetria
- LOG-005 — Estado por balança em memória
- LOG-006 — Persistir somente o resultado estabilizado (revisado)
- LOG-007 — Estratégia de estabilização (revisado e detalhado)
- LOG-008 — Idempotência no nível correto
- LOG-009 — Boundary transacional
- LOG-010 — Modelo mínimo de estoque
- LOG-011 — Arquitetura distribuída como evolução, não requisito inicial
- LOG-012 — Estratégia de validação
- LOG-013 — Fórmula da margem de lucro
- LOG-014 — Autenticação das balanças
- LOG-015 — Relatórios administrativos
- LOG-016 — Reset de sessão após SAVE e troca de placa mid-window

---

# LOG-001 — Identificação do core do problema

## Minha leitura inicial

O desafio contém vários CRUDs, mas interpretei que o principal risco técnico não está nos cadastros.

O problema central é transformar uma sequência de leituras físicas ruidosas e concorrentes em **uma única pesagem de negócio confiável**.

Por isso, defini como foco da implementação:

```text
telemetria
    ↓
validação / normalização
    ↓
estado temporário por balança
    ↓
detecção de estabilidade
    ↓
finalização transacional
```

## Minha decisão

O `Stabilization Engine` deve receber a maior atenção de implementação e testes.

Os CRUDs devem ser corretos e simples, sem competir por complexidade com o problema principal.

## Como usei IA

Usei IA para testar se essa interpretação deixava algum requisito importante de fora e para levantar edge cases de estabilização e concorrência.

## Resultado

Mantive a estabilização como principal diferencial técnico do projeto.

---

# LOG-002 — Arquitetura inicialmente orientada a eventos

## Minha hipótese inicial

Minha primeira inclinação arquitetural foi tratar as leituras como eventos e separar ingestão de processamento através de **background workers**.

Isso surgiu da própria natureza do domínio:

- o dispositivo produz telemetria continuamente;
- o ESP32 é fire-and-forget;
- várias balanças podem produzir simultaneamente;
- uma leitura individual não representa uma operação de negócio concluída.

O desenho conceitual inicial era:

```text
HTTP Ingress
    ↓
ReadingReceived
    ↓
Background Worker
    ↓
Validation
    ↓
Normalization
    ↓
Stabilization
    ↓
StableWeightDetected
    ↓
Business Finalization
```

## Por que considerei event-driven

Event-driven é uma opção natural quando existe:

- comunicação assíncrona;
- produtores independentes;
- grande quantidade de eventos;
- necessidade de baixo acoplamento;
- processamento que pode evoluir separadamente da ingestão;
- contexto de telemetria/IoT.

## Revisão que fiz

Ao aprofundar o workload do MVP, percebi que **a computação individual de cada reading é muito curta**:

```text
deserialize
validate
normalize
lookup session
append sample
evaluate stability
```

Não existe I/O de banco para cada leitura.

Portanto, introduzir uma queue e workers apenas para tornar o fluxo assíncrono criaria novos failure modes antes de existir um problema de processamento que os justificasse.

## Minha decisão atual

O domínio continua sendo naturalmente orientado a eventos, mas **o transporte interno desses eventos não precisa ser assíncrono no MVP**.

Cada request pode ser processado sincronamente na instância:

```text
HTTP Reading
    ↓
Validation
    ↓
Normalization
    ↓
ScaleSession
    ↓
StabilityAlgorithm
    ↓
return
```

Somente quando houver estabilidade:

```text
StableWeightDetected
    ↓
CompleteWeighingUseCase
    ↓
PostgreSQL
```

## Trade-off aceito

Abro mão do buffering assíncrono no MVP em troca de:

- menos infraestrutura;
- menos estado intermediário;
- menos failure modes;
- semântica HTTP mais simples;
- maior facilidade de benchmark.

## Trigger para revisitar

Eu migraria para:

```text
Ingress
   ↓
Durable Queue
   ↓
Workers
```

quando existir evidência de:

- backpressure;
- bursts maiores que a capacidade de processamento;
- perda de readings inaceitável;
- necessidade de retry durável;
- API e processamento precisarem escalar independentemente.

## Como usei IA

Usei IA para confrontar minha arquitetura inicial de background workers com a alternativa síncrona e analisar o custo real de cada leitura.

A decisão final de **não adicionar mensageria agora** foi minha, baseada no workload atual e no princípio de evitar complexidade prematura.

---

# LOG-003 — Resultado em tempo real versus processamento assíncrono

## Problema que identifiquei

O desafio não define:

- dashboard em tempo real;
- WebSocket;
- SSE;
- polling obrigatório;
- callback;
- usuário aguardando a pesagem;
- SLA formal de latência.

Portanto, eu não poderia justificar a arquitetura afirmando que existe um requisito de "real-time".

## Minha interpretação de produto

O que existe é:

```text
telemetria de alta frequência
        ↓
resultado de negócio eventual
```

Uma pesagem só pode existir depois de observar várias leituras.

Mesmo que uma leitura seja processada em microssegundos, o sistema precisa de tempo suficiente para confirmar estabilidade.

## Minha decisão

Tratar o sistema como **processamento de telemetria próximo de tempo real**, sem criar um requisito de real-time que não está no enunciado.

O consumo do resultado do MVP será feito por:

- consulta da `TransportTransaction`;
- consulta de `Weighing`;
- relatórios administrativos.

Não implementei push para UI porque não existe ator ou interface que o exija.

## Trigger para revisitar

Se o produto incluir futuramente um operador que precise receber imediatamente a conclusão de uma pesagem, posso adicionar:

- SSE;
- WebSocket;
- eventos externos;
- integração com outro sistema.

---

# LOG-004 — Hot path de telemetria

## Problema que identifiquei

A frequência do protocolo é aproximadamente:

```text
1 balança ativa ≈ 10 requests/s
```

Portanto, o maior ganho de performance não é necessariamente adicionar threads ou serviços.

É tornar cada reading **extremamente barato**.

## Minha decisão

Criar um hot path exclusivamente em memória:

```text
HTTP
 ↓
deserialize
 ↓
cheap validation
 ↓
normalize
 ↓
lookup ScaleSession
 ↓
append em buffer limitado
 ↓
evaluate stability
 ↓
return
```

O PostgreSQL **não participa desse fluxo normal**.

## Consequência

Mesmo que existam muitas leituras:

```text
1.000 readings/s
```

isso não significa:

```text
1.000 inserts/s
```

A maior parte do tráfego resulta apenas em pequenas operações CPU/memória.

## Otimizações que escolhi

- `ConcurrentHashMap` para localizar sessões;
- estado isolado por `scaleId`;
- buffer de tamanho fixo;
- nenhuma lista crescendo indefinidamente;
- evitar log `INFO` para toda leitura;
- cálculo de estatísticas apenas quando houver amostras suficientes;
- banco apenas na estabilização.

## O que deliberadamente não otimizei ainda

Não implementei estruturas mais sofisticadas para mediana incremental antes de medir.

Com uma janela pequena, ordenar algumas dezenas de números continua barato e muito mais simples de manter.

## Validação

Esse desenho deve ser validado com load test reproduzível.

Não pretendo declarar capacidade sem benchmark.

---

# LOG-005 — Estado por balança em memória

## Minha decisão

Manter uma sessão independente por balança:

```text
scaleId → ScaleSession
```

A sessão contém apenas o estado transitório necessário para estabilização.

Exemplo conceitual:

```text
ScaleSession
- scaleId
- plate
- recentReadings
- state
- consecutiveStableWindows
- firstReadingAt
- lastReadingAt
```

## Por que escolhi isso

O estado:

- é pequeno;
- dura poucos segundos;
- muda em alta frequência;
- não precisa ser historicamente persistido para atender ao MVP.

## Concorrência

Um `ConcurrentHashMap` protege a estrutura do mapa, mas não torna automaticamente cada `ScaleSession` thread-safe.

Por isso, a sincronização deve ocorrer **por sessão/balança**, nunca através de um lock global.

Objetivo:

```text
Scale A ──► Session A
Scale B ──► Session B
Scale C ──► Session C
```

A atividade de uma balança não deve bloquear as demais.

## Trade-off aceito

Restart perde a janela ativa.

Dados de negócio já consolidados permanecem no PostgreSQL.

## Trigger para revisitar

Externalizar state quando:

- múltiplas instâncias forem necessárias;
- HA for requisito;
- perda da janela após restart deixar de ser aceitável.

---

# LOG-006 — Persistir somente o resultado estabilizado (revisado)

## Decisão original

Não persistir cada raw reading no banco transacional.

Fluxo original:

```text
raw readings
    ↓
memory only
    ↓
STABLE
    ↓
Weighing
    ↓
PostgreSQL
```

Racional original: o banco deve armazenar o estado consolidado de negócio, não necessariamente cada amostra física produzida pelo sensor. Isso reduz I/O, mantém o modelo relacional simples e melhora throughput.

## Por que revisei

Ao detalhar o algoritmo de estabilização (LOG-007), a decisão de descartar completamente os raw readings passou a conflitar com dois objetivos que também são meus:

- rastreabilidade suficiente para recalcular uma pesagem se um parâmetro do algoritmo mudar no futuro;
- auditabilidade das leituras que geraram um resultado usado em decisão financeira (custo/receita).

## Decisão revisada

Persistir os raw readings, fora do hot path transacional síncrono:

```text
raw_readings
- scale_id
- timestamp
- weight_kg
- device_id
- plate_number (recebido no payload, já resolvido pelo LPR no device)
```

Isso não contradiz o LOG-004 (hot path barato): a escrita de `raw_readings` é assíncrona/bufferizada (ex: append em lote, ou stream para storage dedicado), sem bloquear o caminho síncrono de estabilização.

## O que mudou em relação à decisão original

| | Decisão original | Decisão revisada |
|---|---|---|
| Raw readings | Não persistidos | Persistidos, fora do hot path síncrono |
| Motivo da escolha | Reduzir I/O por reading | Auditabilidade + recomputabilidade |
| `Weighing` | Único registro de negócio | Continua único registro de negócio, agora referenciável aos raw readings que o originaram |

## Trade-off aceito

Mais volume total de escrita, mas não no caminho crítico de latência — desde que a escrita de `raw_readings` não faça parte da mesma transação que grava a `Weighing`.

## Trigger para revisitar

Se o volume de `raw_readings` virar problema de custo de armazenamento, aplicar retenção (ex: manter raw só dos últimos N dias) em vez de reverter para "não persistir".

## Como usei IA

Usei IA para confrontar a decisão original com o cenário "preciso reprocessar uma pesagem porque um parâmetro do algoritmo estava errado" — foi esse cenário concreto que motivou a reversão.

---

# LOG-007 — Estratégia de estabilização (revisado e detalhado)

## Problema que identifiquei

Não posso confiar em uma única leitura da balança: os valores oscilam por vibração do caminhão, movimentação de carga, características mecânicas da balança e ruído do sensor. Uma leitura isolada nunca é evidência suficiente de estabilidade.

## Pipeline completo

```text
ESP32 envia leitura
    ↓
adiciona à sliding window da balança (scaleId)
    ↓
calcula mediana + MAD
    ↓
remove outliers
    ↓
calcula média, amplitude, desvio padrão, slope (sobre as amostras limpas)
    ↓
verifica critérios de estabilidade
    ↓
exige estabilidade contínua por um intervalo mínimo
    ↓
calcula peso final (média ou trimmed mean)
    ↓
arredonda pela resolução da balança
    ↓
salva
```

### 1. Remoção de outliers — mediana + MAD

```text
M = median(x1, ..., xn)
di = |xi - M|
MAD = median(di)
robustSigma = 1.4826 × MAD
threshold = max(3 × robustSigma, toleranciaMinima)
outlier se |xi - M| > threshold
```

A tolerância mínima (ex: 20 kg) evita falso positivo quando o MAD é muito pequeno ou zero — sem ela, uma janela muito estável ficaria hipersensível e rejeitaria leituras legítimas.

### 2. Critérios de estabilidade — sobre as amostras já limpas

```text
range = max(x) - min(x)
stdDev = sqrt(Σ(xi - mean)² / n)
slope = (lastWeight - firstWeight) / (lastTimestamp - firstTimestamp)   [kg/s]
```

Janela candidata a estável quando:

```text
stdDev <= MAX_STD_DEV
AND range <= MAX_RANGE
AND abs(slope) <= MAX_SLOPE
```

O `slope` resolve o caso que `range`/`stdDev` sozinhos não pegam: uma sequência subindo suavemente (caminhão ainda entrando na balança) pode ter dispersão baixa e mesmo assim não estar parada.

### 3. Máquina de estados — confirma estabilidade no tempo, não só na janela

```text
COLLECTING -> STABILIZING -> STABLE -> SAVE
```

- `COLLECTING`: recebendo leituras, ainda sem janela válida.
- `STABILIZING`: critérios passaram, mas ainda não pelo tempo mínimo (`STABILITY_DURATION`, ex: 2–3s). Qualquer violação volta para `COLLECTING`.
- `STABLE`: critérios mantidos pelo tempo mínimo → calcula e persiste o peso final.

### 4. Peso final

```text
finalWeight = round(mean(cleanSamples) / resolution) × resolution
```

Uso a média (ou trimmed mean) das amostras limpas da janela estável — nunca a última leitura isolada. O arredondamento pela resolução real da balança (ex: 20 kg) evita apresentar precisão que o hardware não tem.

## Por que não Kalman Filter no MVP

Considerei Kalman Filter como evolução futura para suavização de sinal, mas ele não resolve sozinho o problema central: se o caminhão ainda está entrando na balança (sequência claramente ascendente), o Kalman Filter suaviza a curva, mas não decide que aquilo ainda não é uma leitura parada — isso continua exigindo a checagem de slope. Prefiro, para o MVP, uma solução determinística e fácil de testar/justificar a um filtro estatístico que resolve só parte do problema e adiciona mais parâmetros para calibrar.

## Assumptions

```text
MIN_SAMPLES = 20
MIN_VALID_RATIO = 0.8
MAX_STD_DEV = 30 kg
MAX_RANGE = 100 kg
MAX_SLOPE = 10 kg/s
STABILITY_DURATION = 3s
SCALE_RESOLUTION = 20 kg
```

Exemplos, não valores de produção — calibráveis por modelo de balança.

## Minha regra de segurança

> Quando houver dúvida, prefiro continuar medindo a produzir uma pesagem estabilizada incorreta.

## Modelagem (Java)

```java
record WeightSample(long timestampMs, double weightKg) {}

record WeightResult(
    boolean stable,
    double weightKg,
    double standardDeviation,
    int samplesUsed
) {}
```

Processamento concentrado numa classe `WeightProcessor`, recebendo a janela de `WeightSample` e retornando um `WeightResult` — algoritmo isolado e testável sem depender de banco/HTTP.

## Testes que valem a pena escrever

1. peso completamente estável;
2. estável com pequeno ruído;
3. um outlier isolado enorme;
4. caminhão entrando na balança (trend claro);
5. caminhão saindo da balança;
6. oscilação além do limite;
7. amostras insuficientes;
8. estável por menos tempo que o exigido (não deve salvar);
9. estabilização completa (deve salvar);
10. arredondamento pela resolução da balança.

## Como usei IA

Colei o enunciado completo do desafio e pedi algoritmo, fórmula matemática e estratégia para chegar num peso final em Java (prompt completo registrado como AI-008 em `USO_DE_IA.md`). A resposta trouxe mediana+MAD, range/stdDev/slope, a máquina de estados, Kalman Filter como alternativa e uma modelagem inicial em Java. Usei IA também para contestar se range+stdDev isoladamente eram suficientes — foi essa contestação que me levou a incluir slope como terceiro critério obrigatório, e para avaliar Kalman Filter, concluindo que ele não substitui a decisão "está parado ou não" sozinho.

## Trigger para revisitar

- Calibrar os thresholds com dados reais assim que disponíveis.
- Migrar slope de "primeiro/último ponto" para regressão linear sobre toda a janela, se o slope simples se mostrar ruidoso demais em produção.
- Considerar Kalman Filter como camada de suavização adicional (não substituta) se o ruído do sensor for maior do que o esperado.

---

# LOG-008 — Idempotência no nível correto

## Problema que identifiquei

O protocolo não fornece:

- `readingId`;
- sequence number;
- device timestamp.

Portanto, duas leituras:

```text
32010
32010
```

podem ser duas amostras legítimas consecutivas.

## Decisão que descartei

Não vou deduplicar por:

```text
scaleId + plate + weight
```

porque isso alteraria o próprio sinal de estabilidade.

## Minha decisão

Garantir idempotência onde existe uma operação de negócio identificável:

> **Uma `TransportTransaction` pode produzir no máximo uma `Weighing` final.**

Defesas:

```text
state RECORDED
+
status da transaction
+
UNIQUE(transport_transaction_id)
+
DB transaction
```

## Evolução possível

Uma versão futura do protocolo poderia adicionar:

- `readingId`;
- `sequence`;
- `deviceTimestamp`.

Aí request-level idempotency passaria a ser tecnicamente possível.

---

# LOG-009 — Boundary transacional

## Problema

Ao detectar estabilidade, várias mudanças precisam ocorrer de forma consistente:

```text
create Weighing
calculate net
calculate cost
update GrainStock
complete TransportTransaction
```

## Minha decisão

Executar a finalização dentro de uma transaction curta:

```java
@Transactional
```

## O que não faço

Não mantenho uma transaction aberta durante os segundos de estabilização.

O banco só entra no fluxo quando já existe um evento de negócio consolidado.

## Benefício

Evito estados parciais como:

```text
Weighing criada
+
TransportTransaction ainda OPEN
```

## Falhas

Crash antes do commit:

```text
ROLLBACK
```

Crash após commit:

```text
dado persistido
+
idempotência bloqueia nova finalização
```

---

# LOG-010 — Modelo mínimo de estoque

## Minha decisão inicial

Considerei modelar movimentos de estoque através de um ledger.

## Reavaliação

Percebi que isso introduziria regras que o desafio não fornece:

- saídas;
- ajustes;
- diferentes tipos de movimento;
- reconciliação.

## Minha decisão final

Representar apenas o que o requisito atual precisa:

```text
GrainStock
- branchId
- grainTypeId
- availableQuantityKg
```

Isso é suficiente para calcular uma margem dependente de disponibilidade.

## Trigger para revisitar

Migrar para movimentos/ledger se o produto passar a exigir:

- vendas;
- ajustes;
- histórico completo;
- auditoria de estoque.

---

# LOG-011 — Arquitetura distribuída como evolução, não requisito inicial

## Minha visão

A arquitetura do código deve deixar um caminho claro para evolução, mas não preciso instalar hoje a infraestrutura de amanhã.

MVP:

```text
ESP32
  ↓
Spring Boot
  ↓
in-memory ScaleSession
  ↓
PostgreSQL
```

Possível evolução:

```text
ESP32
  ↓
Ingress
  ↓
Durable Queue
  ↓
Workers
  ↓
RDS
```

## Minha decisão

Não implementar agora:

- Kafka;
- SQS;
- Redis;
- Kinesis;
- ECS;
- Kubernetes;
- IoT Core.

## Racional

Cada componente futuro deve entrar por um motivo mensurável.

Exemplos:

### Queue

Adicionar quando existir backpressure ou requisito de entrega durável.

### Redis

Adicionar quando state precisar ser compartilhado.

### Multiple workers

Adicionar quando benchmark mostrar que uma instância não atende ao workload.

### IoT platform

Adicionar quando lifecycle/identity de dispositivos se tornar parte relevante do produto.

---

# LOG-012 — Estratégia de validação

Eu quero provar comportamento, não apenas apresentar diagramas.

## Testes de algoritmo

- estável;
- instável;
- convergência;
- outlier;
- múltiplos outliers;
- estabilidade temporária;
- thresholds.

## Testes de estado

- isolamento entre escalas;
- plate conflict;
- timeout;
- `RECORDED`;
- cleanup.

## Testes de negócio

- gross/tare/net;
- custo;
- price snapshot;
- estoque;
- margem.

## Integration test

```text
OPEN TransportTransaction
        ↓
sequence of readings
        ↓
STABLE
        ↓
exactly one Weighing
        ↓
COMPLETED
```

## Concorrência

Múltiplas escalas produzindo simultaneamente.

## Performance

Benchmark incremental:

```text
10 scales
50 scales
100 scales
250 scales
...
```

Medir:

- throughput;
- p50/p95/p99;
- error rate;
- CPU;
- heap/GC;
- contenção.

A capacidade declarada no README deve vir de medição, não de estimativa.

---

# LOG-013 — Fórmula da margem de lucro (5%–20%, inversamente proporcional ao estoque)

## Problema que identifiquei

O enunciado define dois limites (mínimo 5%, máximo 20%) e uma relação qualitativa ("quanto mais escasso o grão, maior a margem"), mas não define os pontos de referência que caracterizam "escasso" e "abundante". Sem esses pontos, a regra não é computável.

## Minha hipótese

Uma relação inversa e linear entre estoque disponível e margem é suficiente para o MVP e mantém a mesma prioridade que já apliquei na estabilização: determinismo e explicabilidade antes de sofisticação.

## Minha decisão

Definir, por tipo de grão, uma quantidade de referência (`referenceStock`) que representa "abundância" — pode ser a capacidade típica da doca para aquele grão, ou uma média histórica configurável.

```text
margin(grainType) =
  maxMargin - (maxMargin - minMargin) × clamp(currentStock / referenceStock, 0, 1)
```

- `currentStock = 0` → margin = maxMargin (20%) — grão totalmente escasso
- `currentStock ≥ referenceStock` → margin = minMargin (5%) — grão abundante
- entre os dois extremos → interpolação linear

## Onde essa margem é usada

Não persisto a margem no momento da pesagem. O campo obrigatório da `Weighing` é `custo da carga` (preço de compra × peso líquido), que não depende de margem.

A margem é calculada sob demanda — nos relatórios de oportunidade de lucro (LOG-015) — porque é função do estoque *atual*, que muda a cada transação. Gravá-la no momento da pesagem a tornaria obsoleta assim que o estoque mudasse.

## Assumptions

`referenceStock` é uma configuração por tipo de grão, não uma verdade de negócio que decidi sozinho. Documento isso explicitamente como suposição a validar com o time de produto/operação — mesmo espírito dos thresholds de estabilização no LOG-007.

## Trigger para revisitar

- Se o negócio fornecer uma definição real de "abundante" vs. "escasso" (ex: capacidade contratual da doca, estoque de segurança), substituo a suposição pelo valor real.
- Se a curva linear não refletir o comportamento de mercado desejado (ex: margem devendo subir mais rápido perto do zero), evoluo para uma curva não linear — mas só com dado real de apoio, não por estética.

## Como usei IA

Pedi para a IA listar formas de interpretar "inversamente proporcional" de forma computável e comparar alternativas (linear, exponencial, degraus fixos por faixa de estoque). Mantive a linear por ser a leitura mais direta do enunciado, sem introduzir parâmetros extras que ele não pede.

## Validação

- teste de margem no limite inferior (estoque = 0 → 20%);
- teste de margem no limite superior (estoque ≥ referência → 5%);
- teste de ponto médio (estoque = 50% da referência → 12,5%);
- teste de clamp (estoque negativo ou acima da referência não pode extrapolar 5%/20%).

---

# LOG-014 — Autenticação das balanças

## Problema que identifiquei

O payload do protocolo (`id`, `plate`, `weight`) não carrega nenhuma credencial. Qualquer client que conheça o `id` de uma balança cadastrada pode, hoje, enviar leituras fabricadas em nome dela — o que corromperia diretamente o `Stabilization Engine` e, por consequência, o custo calculado.

## Alternativas consideradas

```text
API key estática por balança (header HTTP)
vs
HMAC sobre o payload com segredo por dispositivo
vs
mTLS por dispositivo
vs
allowlist de IP
```

- **mTLS**: forte, mas caro de provisionar/rotacionar num ESP32 e desproporcional ao MVP.
- **HMAC por payload**: mitiga replay e tampering melhor que uma key estática, mas exige sincronismo de relógio ou nonce — complexidade que o protocolo atual (fire-and-forget, sem campo de sequência) não tem onde guardar.
- **Allowlist de IP**: frágil — dispositivos de campo tendem a estar atrás de NAT compartilhado ou IP dinâmico.
- **API key estática por balança**: simples, suficiente para provar que a origem conhece um segredo associado àquela balança específica, alinhado ao princípio que venho aplicando no resto do documento.

## Minha decisão

Cada `Balança` cadastrada recebe uma chave (`apiKey`), armazenada como hash (nunca em texto puro) no banco. O ESP32 envia essa chave num header HTTP:

```text
X-Scale-Key: <apiKey>
```

Um filtro/interceptor valida a chave **antes** do request tocar o hot path de estabilização. Se inválida ou ausente, retorna `401` genérico — sem revelar se o `id` da balança existe ou não — e a requisição nunca chega ao `ScaleSession`.

## Onde essa validação acontece no fluxo

```text
HTTP request
    ↓
Auth filter (valida X-Scale-Key contra Balança)
    ↓ (falha → 401, fim)
    ↓ (sucesso)
Hot path (LOG-004)
```

## Trade-off aceito

Uma key estática não protege contra replay (alguém capturando o header e reenviando). Para o MVP, aceito esse risco: um replay ainda precisaria ser uma leitura plausível para influenciar uma pesagem real, e o pior caso é uma leitura espúria dentro de uma janela de estabilização — que o critério do LOG-007 ("na dúvida, não estabilizo") já mitiga parcialmente.

## Trigger para revisitar

- Migrar para HMAC assinado (ou mTLS) se houver evidência real de tentativa de fraude/tampering nas balanças.
- Adicionar rotação de chave automática se o número de balanças/filiais crescer a ponto de rotação manual deixar de ser operacionalmente viável.

## Como usei IA

Pedi para a IA listar mecanismos de autenticação viáveis para dispositivos de baixo poder de processamento (ESP32) e comparar custo de implementação versus proteção real ganha. A escolha da API key estática como piso mínimo aceitável para o MVP foi minha, seguindo o mesmo critério de "qual problema concreto justifica a complexidade agora" aplicado no resto do documento.

## Validação

- teste de request sem header → 401;
- teste de request com key inválida → 401;
- teste de request com key válida de outra balança → 401 (não autentica cruzado);
- teste de que o hot path/`ScaleSession` não é tocado em request não autenticado.

---

# LOG-015 — Relatórios administrativos

## Problema que identifiquei

O desafio pede para validar quais dados são relevantes para análise administrativa e desenhar relatórios com base nisso, sem definir quais.

## Critério que apliquei

O mesmo do resto do documento: qual problema concreto do enunciado cada relatório resolve. Uma primeira versão desta entrada era um rascunho genérico (custo/volume por grão, oportunidade de lucro, saúde de balança, throughput). Revisei essa lista depois de confrontá-la com uma pesquisa comparativa de soluções reais do setor (AI-009 em `USO_DE_IA.md`), para não propor relatórios nem arbitrários demais nem além do que meu modelo realmente sustenta.

## Decisão final — implementados nesta fase (MUST)

| Relatório | Por que entra | Endpoint | Fonte |
|---|---|---|---|
| Livro de Pesagens | Fonte operacional e auditável de cada carga — o resultado do core algorithm vira registro consultável | `GET /api/reports/weighings` | `Weighing` → `TransportTransaction`, `Branch`, `Scale`, `GrainType` |
| Volume e Custo por Grão | Responde diretamente a "calcular custos" | `GET /api/reports/cost-by-grain` | `Weighing.cost`, `netWeight`, `grainTypeId` |
| Estoque e Oportunidade de Margem | Responde diretamente a "identificar oportunidades de lucro"; conecta `GrainStock` e a fórmula do LOG-013 a um consumidor real | `GET /api/reports/inventory-opportunities` | `GrainStock`, `GrainType`, fórmula do LOG-013 |
| Desempenho por Filial | O enunciado é explícito em "diversas filiais pelo Brasil" | `GET /api/reports/branches/performance` | `Weighing`, `TransportTransaction`, `Branch` |

Contrato de resposta uniforme: `{ period: {from, to}, filters: {...}, data: [...] }`. Filtros comuns: `from`, `to`, `branchId`, `grainTypeId` onde aplicável. Política de paginação: endpoint de detalhe (Livro de Pesagens) exige paginação; endpoints agregados não.

Cálculo de margem (`GrainStock` + fórmula do LOG-013) permanece no domínio/application layer, não em SQL — é regra de negócio.

## Decisão adicional — exposição de `plate` (LGPD)

Achado que não estava coberto em nenhuma entrada anterior: `plate` pode ser considerada dado pessoal sob a LGPD quando razoavelmente vinculável a uma pessoa natural. Decisão: dashboard e os três relatórios agregados (Volume/Custo por Grão, Estoque/Margem, Desempenho por Filial) não expõem `plate`. Apenas o Livro de Pesagens expõe, e mediante papel autorizado — mesmo princípio de necessidade mínima que já aplico em outras partes do documento (ex: não logar `apiKey`/`apiKeyHash`, LOG-014).

## Schema adicional necessário

Os 4 relatórios MUST expõem uma dependência que antes só existia na fórmula, não no schema:

- `GrainType.referenceStockKg` — necessário para computar `margin` no relatório de Estoque/Oportunidade (a fórmula do LOG-013 já assumia essa referência, mas eu não tinha formalizado o campo).

Nenhum campo novo é necessário em `Weighing` para os 4 MUST. `samplesUsed`/`standardDeviation` (já previstos no LOG-007) só passam a ser consumidos quando o relatório de Saúde das Balanças (abaixo) for promovido.

## Adiado — implementar somente se sobrar tempo (SHOULD)

- **Saúde operacional das balanças** — usa `samplesUsed`/`standardDeviation` que o design já produz; transforma o Stabilization Engine em observabilidade de negócio.
- **Transações pendentes e exceções** — barato dado que `TransportTransaction.status`/`startedAt` já existem; mostra operações que precisam de atenção.

Ambos só entram depois que os 4 MUST e os testes do core (estabilização, idempotência, transação — LOG-007, LOG-008, LOG-009) estiverem prontos. Não são bloqueio para a entrega.

## Fora de escopo desta fase — apenas documentado (COULD)

- **Qualidade da estabilização** (diagnóstico agregado por scale) e **Auditoria técnica de uma pesagem** (raw readings que geraram um resultado) dependem de `raw_readings` (LOG-006) e adicionam trabalho de investigação/analytics que não é necessário para responder ao requisito do desafio agora. Ficam mapeados como evolução, não como código.

## Não implementarei (fora do MVP)

Relatório de umidade, impurezas, classificação de grão, quebra técnica, produtor, contratos de venda, faturamento, frete, motorista, tempo de fila na portaria — são recursos reais de ERPs agrícolas (Siagri, Senior, Rech), mas meu protocolo (`{id, plate, weight}`) e meu modelo não recebem esses dados. Inventá-los seria sair do escopo, mesmo critério já aplicado no AI-007.

## Como usei IA

Ver `AI-009` em `USO_DE_IA.md`. Pedi uma pesquisa comparativa com soluções reais de mercado (Siagri, Senior/Mega Agro, Rech/SIGER, Rice Lake, Vertical Software, GMS Grain Management) para validar quais relatórios são defensáveis para este domínio específico, em vez de propor uma lista arbitrária. A priorização MUST/SHOULD/COULD e o achado sobre LGPD/`plate` foram avaliados e aceitos por mim; a decisão de implementar só os 4 MUST nesta fase — dado o prazo real até a entrevista técnica, não uma preferência estética — foi minha.

## Trigger para revisitar

Promover os relatórios SHOULD para implementados se sobrar tempo depois dos 4 MUST e dos testes do core estarem prontos.

---

# LOG-016 — Reset de sessão após SAVE e troca de placa mid-window

## Problema que identifiquei

O LOG-007 decide *quando* salvar uma pesagem, mas não decide o que acontece com o estado da balança depois de salvar, nem o que fazer se a `plate` reportada mudar no meio de uma janela ainda não estabilizada. Os dois cenários já tinham aparecido na adversarial review (`USO_DE_IA.md`, seção 2.2), mas sem decisão registrada até agora.

## Cenário 1 — depois do SAVE

Após uma `Weighing` ser salva, o caminhão descarrega e sai da balança. O ESP32 continua enviando leituras a cada 100ms enquanto houver peso sobre a balança, então o peso eventualmente cai para próximo de zero.

### Minha decisão

Tratar "peso próximo de zero por um tempo mínimo" como fim de sessão:

```text
STABLE (SAVE já ocorreu)
    ↓
peso cai abaixo de EMPTY_THRESHOLD por EMPTY_DURATION
    ↓
ScaleSession é resetada → volta para COLLECTING
```

`EMPTY_THRESHOLD` (ex: 200 kg) e `EMPTY_DURATION` (ex: 1s) evitam resetar a sessão por uma oscilação momentânea logo após o SAVE.

## Cenário 2 — troca de placa mid-window

Se a `plate` mudar antes de `STABLE` ser atingido, duas leituras consecutivas legítimas da mesma balança estão descrevendo situações físicas diferentes (caminhão trocou rápido, ou erro de leitura do LPR no device).

### Minha decisão

Tratar troca de placa como evento que invalida a janela atual — mesma lógica de "não fingir estabilidade que não existe" do LOG-007:

```text
leitura chega com plate diferente da ScaleSession atual
    ↓
descarta a janela acumulada (não é a mesma pesagem)
    ↓
reinicia ScaleSession com a nova plate
    ↓
volta para COLLECTING
```

Prefiro perder algumas leituras e recomeçar a arriscar misturar amostras de dois caminhões diferentes num único `finalWeight`.

## Assumptions

`EMPTY_THRESHOLD` e `EMPTY_DURATION` são exemplos, calibráveis por balança, mesmo espírito dos thresholds do LOG-007.

## Trigger para revisitar

Se troca de placa mid-window se mostrar comum em produção (não só erro raro), investigar causa raiz no LPR do device antes de tratar como caso normal do backend.

## Como usei IA

Usei IA para levantar esse cenário na adversarial review e para comparar "descartar e reiniciar" contra "tentar reconciliar as duas placas" — descartei a segunda opção por adicionar ambiguidade de negócio que o desafio não define.

---

# LOG-017 — Tolerância mínima de outlier como campo configurável

## Problema que identifiquei

Ao implementar `StabilizationEngine.process()`, encontrei um gap entre o `LOG-007` e o código: o doc especifica `threshold = max(3 × robustSigma, toleranciaMinima)`, com a tolerância mínima citada como exemplo de 20kg — mas o `StabilizationProperties` (o record de configuração calibrável do algoritmo) não tinha nenhum campo para esse valor. Sem ele, MAD muito pequeno ou zero (janela muito estável) deixaria o outlier removal hipersensível, rejeitando leituras legítimas.

## Minha decisão

Adicionar `outlierToleranceKg` ao `StabilizationProperties`, mesmo padrão dos outros 9 campos já configuráveis (`grainweighing.stabilization.outlier-tolerance-kg`, default 20kg — mesmo exemplo do LOG-007).

Descartei reusar `scaleResolutionKg` como proxy: apesar de os dois exemplos coincidirem em 20kg, são conceitos ortogonais — resolução física do hardware (granularidade que o sensor discrimina) versus piso estatístico de rejeição de outlier (distância mínima da mediana para ser descartado como ruído). Acoplar os dois criaria um efeito colateral oculto: recalibrar a resolução de uma balança apertaria sem querer o critério de outlier junto, sem forma de desacoplar depois sem migração.

## Assumptions

`outlierToleranceKg = 20 kg` é um exemplo, calibrável por modelo de balança — mesmo espírito dos demais thresholds do LOG-007.

## Como usei IA

Identifiquei o gap durante a implementação guiada por IA de `StabilizationEngine.process()`; usei IA para validar se a tolerância mínima era de fato ausente do record (confirmado via grep no código) e para comparar as duas alternativas (novo campo vs. reusar `scaleResolutionKg`) antes de decidir.

---

# Síntese das minhas decisões

Minha arquitetura final não foi escolhida porque é a mais sofisticada.

Foi escolhida porque considero que ela resolve melhor **o problema atual**:

```text
HTTP telemetry
      ↓
cheap synchronous hot path
      ↓
per-scale transient state
      ↓
deterministic stabilization
      ↓
transactional business finalization
      ↓
PostgreSQL
```

Os principais princípios que conduziram minhas escolhas foram:

1. proteger o hot path de I/O desnecessário;
2. manter state transitório fora do banco;
3. isolar concorrência por balança;
4. priorizar correção sobre falsa estabilidade;
5. garantir consistência no boundary de negócio;
6. não fingir garantias que o protocolo não permite;
7. medir performance antes de distribuir;
8. manter um caminho de evolução sem antecipar infraestrutura.

---

# Como a IA participou

A IA foi utilizada como eu utilizaria um peer técnico durante design review:

- desafiar hipóteses;
- perguntar por failure modes;
- sugerir alternativas;
- identificar edge cases;
- comparar trade-offs;
- revisar se eu estava saindo do escopo.

Ela **não foi usada como autoridade para determinar a arquitetura**.

As hipóteses, decisões finais e responsabilidade pela solução são minhas.
