# USO_DE_IA.md

# Uso de IA no Processo de Engenharia — Grain Weighing Platform MVP

Este documento descreve **como utilizei IA durante o desafio**, deixando explícita a separação entre:

```text
meu raciocínio e minhas decisões
              ≠
sugestões produzidas pela IA
```

> **Eu fui responsável pela arquitetura, pelas hipóteses, pelos trade-offs e pela validação da solução.**

Usei IA como ferramenta de apoio para expandir alternativas, desafiar decisões que eu já estava considerando, encontrar riscos e acelerar tarefas mecânicas.

---

# 1. Meu modelo de uso

Meu processo foi:

```text
EU
identifico o problema
        ↓
EU
formulo uma hipótese
        ↓
IA
questiona / compara / amplia
        ↓
EU
avalio contra requisitos e escopo
        ↓
EU
tomo a decisão
        ↓
testes / simulator / benchmark
validam a decisão
```

A IA não determina o que é requisito.

O enunciado do desafio permanece a fonte de verdade.

---

# 2. Para que usei IA

Utilizei IA principalmente em cinco papéis.

## 2.1 Design review

Usei IA para confrontar escolhas arquiteturais que eu estava avaliando.

Exemplos:

- background workers versus processamento síncrono;
- estado em memória versus Redis;
- mensageria no MVP versus apenas evolução futura;
- persistência de raw data versus estado transitório.

---

## 2.2 Adversarial review

Usei a IA para tentar encontrar situações em que minhas decisões falhariam.

Exemplos:

- restart durante uma pesagem;
- duas finalizações concorrentes;
- troca de placa no meio da sessão;
- PostgreSQL indisponível;
- overload;
- leitura duplicada;
- ausência de `readingId`.

---

## 2.3 Exploração de alternativas

Quando havia mais de uma implementação plausível, usei IA para tornar os trade-offs explícitos antes de decidir.

Exemplo:

```text
synchronous hot path
vs
in-memory background workers
vs
durable queue + workers
```

---

## 2.4 Revisão de escopo

Usei IA para revisar se conhecimento de domínio ou infraestrutura estava começando a transformar o exercício em um produto maior do que o solicitado.

Isso levou à remoção ou adiamento de itens como:

- workflow operacional completo;
- metrologia como feature;
- processamento de LPR;
- inventory ledger;
- SQS/Redis/AWS no MVP.

---

## 2.5 Assistência de implementação

IA também pode ser utilizada para:

- boilerplate;
- testes;
- refactoring;
- documentação;
- código repetitivo.

Todo código gerado deve passar por revisão humana e validação automatizada antes de ser aceito.

---

# 3. Exemplos reais do processo

## AI-001 — Arquitetura event-driven / background workers

### Minha hipótese

Eu considerei inicialmente uma arquitetura orientada a eventos com background workers.

Meu raciocínio era que o domínio possui:

- telemetria contínua;
- comportamento fire-and-forget;
- múltiplos produtores;
- processamento temporal;
- características comuns em sistemas IoT.

### Prompt / discussão

> "sim a arquitetura que eu imaginei inicialmente seria a de background workers, mas isso podia ser uma instancia normal, e em relação ao codigo fazer toda essa pipeline de ingerir raw data, validation, normalization, computation and so on. regarding code and arqueture patterns, a event driven system would make sense..."

### Como usei a IA

Pedi que a arquitetura fosse confrontada com:

- escopo do MVP;
- custo real por reading;
- necessidade ou não de comunicação assíncrona interna;
- riscos de uma queue em memória.

### Minha conclusão

Mantive a **modelagem conceitual orientada a eventos**, mas não considerei necessário criar transporte assíncrono interno no MVP.

O processamento de uma leitura é pequeno o suficiente para continuar no request thread inicialmente.

### Minha responsabilidade técnica

Eu tomei essa decisão porque uma queue interna adicionaria:

- backlog;
- shutdown semantics;
- ordering;
- estado intermediário;
- novos failure modes;

sem um requisito atual que exigisse esses custos.

### Como pretendo validar

Load test.

Se throughput ou backpressure se tornarem problemas reais, a arquitetura evolui para workers/mensageria.

---

# AI-002 — Real-time versus near-real-time

### Pergunta que eu levantei

> "o usuário precisa do resultado em tempo real? em que momento com base nas especificações do teste? isso ficou em aberto, como será consumido?"

### Minha análise

Observei que o desafio não define:

- usuário interativo;
- dashboard;
- SLA;
- WebSocket;
- SSE;
- callback.

### Como usei a IA

Usei IA para separar:

```text
alta frequência de telemetria
```

de:

```text
necessidade de entregar resultado em real-time
```

### Minha conclusão

O sistema precisa processar telemetria rapidamente, mas não existe evidência de um requisito formal de real-time.

Uma pesagem é naturalmente um resultado eventual da observação de várias amostras.

### Minha decisão

O MVP oferece o resultado consolidado através da API e dos relatórios.

Não adicionei push para UI sem um consumidor explicitamente definido.

---

# AI-003 — Processamento síncrono ou assíncrono

### Pergunta que eu fiz

> "certo, nesse contexto de produto para um MVP, gostariamos de ter esse dado sincrono ou assincrono? A computação não é tão longa, apenas preciso tratar o numero massivo de requests da telemetry..."

### Minha hipótese

Se cada reading for barato, processamento síncrono pode ser uma solução melhor para o MVP.

### Como usei IA

Pedi para avaliar se background workers ainda ofereciam benefício suficiente diante de:

- computação curta;
- ausência de DB por reading;
- necessidade de simplicidade;
- volume de requests.

### Minha decisão

Começar com um hot path síncrono.

Isso não significa que a operação de negócio seja síncrona em relação ao primeiro reading.

O resultado final continua surgindo apenas quando a sequência se torna estável.

### Trigger definido por mim

Migrar para processamento assíncrono se benchmark demonstrar:

- saturação;
- backpressure;
- erro crescente;
- latência incompatível;
- necessidade de entrega durável.

---

# AI-004 — Otimização do hot path

### Pergunta que eu fiz

> "Existe algum metodo para melhorar a performance ou o processamento de varias requests de telemetria?"

### Meu objetivo

Antes de distribuir o sistema, eu queria reduzir o custo de cada evento.

### Como usei IA

Usei IA para revisar possíveis fontes de overhead.

### Decisões que adotei

#### Sem banco por reading

A maior parte da telemetria é processada apenas em memória.

#### Estado por balança

```text
scaleId → ScaleSession
```

#### Janela limitada

Um buffer de tamanho fixo evita crescimento de memória.

#### Logging controlado

Não gerar `INFO` para cada reading.

#### Lock local

Concorrência da Scale A não bloqueia Scale B.

#### Benchmark antes de otimização sofisticada

Não implementei estruturas complexas de mediana antes de medir necessidade.

### Minha responsabilidade técnica

A decisão principal foi minha:

> **otimizar primeiro o custo por evento e medir antes de adicionar infraestrutura distribuída.**

---

# AI-005 — Idempotência

### Problema que identifiquei

O protocolo não fornece identidade do evento.

Portanto, deduplicação por payload seria semanticamente incorreta.

### Como usei IA

Usei IA para tentar encontrar uma forma segura de request-level deduplication e explorar cenários de retry.

### Minha conclusão

Não existe informação suficiente no payload atual para diferenciar:

```text
reading legítima repetida
```

de:

```text
retry do mesmo evento
```

### Minha decisão

Proteger o efeito de negócio:

```text
TransportTransaction
      ↓
at most one Weighing
```

com:

- state;
- constraint no banco;
- transaction.

Essa garantia é mais importante do que fingir uma idempotência que o protocolo não suporta.

---

# AI-006 — Estratégia de estabilização

### Minha premissa

Uma única amostra nunca é evidência suficiente de estabilidade.

### Como usei IA

Usei IA para explorar:

- média versus mediana;
- outliers;
- dispersão;
- tendência;
- múltiplas janelas;
- false stable.

### Minha decisão

Utilizar uma estratégia determinística e configurável baseada em:

```text
sliding window
+
median
+
spread
+
trend
+
consecutive stable windows
```

### Minha responsabilidade técnica

Os parâmetros do algoritmo são assumptions que eu preciso validar.

Eu não os apresento como verdades físicas.

### Critério que adotei

> Prefiro atrasar ou não concluir uma pesagem automática a produzir um falso resultado estabilizado.

---

# AI-007 — Revisão de overengineering

### Minha preocupação

Eu percebi que a exploração de mercado e produção estava começando a introduzir funcionalidades não solicitadas.

### Prompt

> "Sim, concordo que essa solução faz sentido, mas não quero sair do escopo do projeto. Consegue analisar divergencias?"

### Como usei IA

Pedi uma revisão explícita entre:

```text
o que o desafio exige
```

e:

```text
o que estávamos adicionando por conhecimento externo
```

### Minha decisão

Removi do MVP:

- shadow mode;
- workflow operacional adicional;
- classificação de grãos;
- segunda pesagem;
- processamento LPR;
- inventory ledger;
- SQS;
- Redis;
- IoT Core.

### Minha responsabilidade técnica

O objetivo não foi produzir a arquitetura mais extensa.

Foi produzir a menor arquitetura que resolve corretamente o problema apresentado.

---

## AI-008 — Estratégia de estabilização (algoritmo detalhado)

### Minha hipótese antes do prompt

Uma leitura isolada nunca é evidência de estabilidade; eu já sabia que precisaria de alguma forma de janela + critério estatístico antes de pedir ajuda à IA — não parti de zero.

### Prompt

> "Você trabalha em uma empresa de transporte de grãos, com diversas filiais pelo Brasil, que possui um parque de balanças para pesagem de caminhões carregados com grãos. Como parte da digitalização e o@mização de seus processos, sua empresa contratou uma terceira para automa@zar tais balanças. Essa empresa u@lizou um ESP32, integrados a cada balança e com câmera LPR, responsáveis por enviar leituras de peso automa@camente para um servidor central da sua empresa. Porém, o sistema de estabilização das balanças ainda não foi aprimorado, resultando em oscilações constantes nos valores de peso reportados. Neste contexto, a empresa precisa receber, processar e armazenar os dados das balanças de modo eficiente e confiável, possibilitando calcular custos e iden@ficar oportunidades de lucro no transporte de grãos. Qual o melhor algoritmo computacional, formula matematica e estrategia para processar e computar esse problema? Vou fazer um teste tecnico em java, e quero saber como o core algorithm vai funcionar, para ter um kg final."

### Como usei a IA

Colei o enunciado completo do desafio no prompt, propositalmente, para que qualquer alternativa sugerida já viesse ancorada no problema real (fire-and-forget, múltiplas balanças, ESP32) em vez de um "algoritmo de estabilização" genérico de livro-texto.

### O que recebi vs. o que decidi

A resposta trouxe: remoção de outlier por mediana+MAD, critérios de range/desvio padrão/slope, uma máquina de estados para confirmar estabilidade no tempo, Kalman Filter como alternativa, e uma modelagem inicial em Java.

Minha avaliação, registrada em detalhe no LOG-007 do `LOG_DECISOES_TECNICAS.md`:

- **ACCEPTED** — mediana + MAD para outlier removal; a máquina de estados `COLLECTING → STABILIZING → STABLE`.
- **MODIFIED** — os thresholds numéricos sugeridos (`MAX_STD_DEV`, `MAX_RANGE`, `MAX_SLOPE` etc.) foram mantidos como *assumptions* explicitamente marcadas para calibração futura, não como valores prontos para produção.
- **REJECTED** (como solução principal do MVP) — Kalman Filter. Ele suaviza sinal, mas não resolve sozinho a decisão "o caminhão ainda está entrando ou já parou" — isso continua exigindo o critério de slope. Mantive Kalman Filter apenas como possível evolução futura de suavização, não como substituto do core algorithm.

### Minha responsabilidade técnica

A IA gerou o conjunto de técnicas e as fórmulas candidatas. A decisão de quais compor no pipeline final, quais marcar como assumption a calibrar, e por que rejeitar Kalman Filter como algoritmo principal — com a justificativa técnica de que ele não decide sozinho o problema central — foi minha.

### Validação prevista

Ver seção "Testes que valem a pena escrever" do LOG-007.

---

## AI-009 — Portfólio de relatórios administrativos (pesquisa comparativa de mercado)

### Minha hipótese antes do prompt

Eu já sabia, pelo enunciado, que precisava "validar quais dados são importantes" e desenhar relatórios — mas sem um ponto de comparação eu corria o risco de propor uma lista arbitrária, ou de ir longe demais e reproduzir um ERP agrícola completo (o mesmo risco que já havia identificado no AI-007).

### Prompt

> Pedi uma pesquisa (deep research) comparando soluções reais de recebimento/pesagem de grãos no Brasil e internacionalmente (Siagri, Senior/Mega Agro, Rech/SIGER, Rice Lake, Vertical Software, GMS Grain Management), para levantar quais relatórios essas soluções efetivamente oferecem, e cruzar isso com os dados que meu modelo (`Weighing`, `TransportTransaction`, `GrainStock`, `GrainType`, `Branch`, `Scale`, `raw_readings`) realmente possui — evitando propor relatórios que dependem de dados que meu protocolo não recebe (ex: umidade, classificação do grão).

### O que recebi vs. o que decidi

A pesquisa trouxe 8 relatórios candidatos, priorizados como MUST/SHOULD/COULD, com stakeholders, SQL de referência, contrato de API, KPIs e estratégia de retenção. Também trouxe um ponto que eu não tinha registrado em nenhum LOG anterior: `plate` pode ser dado pessoal sob a LGPD quando vinculável a uma pessoa natural, o que implica não expor `plate` em relatórios agregados.

Minha avaliação, registrada em detalhe no LOG-015:

- **ACCEPTED** — os 4 relatórios MUST (Livro de Pesagens, Volume/Custo por Grão, Estoque/Oportunidade de Margem, Desempenho por Filial): cada um mapeia diretamente para um requisito explícito do enunciado.
- **ACCEPTED** — não expor `plate` em relatórios agregados/dashboard, só no Livro de Pesagens para papel autorizado. Não estava em nenhuma decisão minha anterior; virou parte do LOG-015.
- **DEFERRED** — os 2 relatórios SHOULD (Saúde das Balanças, Transações Pendentes): fazem sentido e usam dado que meu design já produz, mas só entram depois que os 4 MUST e os testes do core (estabilização, idempotência, transação) estiverem prontos.
- **REJECTED (nesta fase)** — os 2 relatórios COULD (Qualidade da Estabilização, Auditoria Técnica): dependem de `raw_readings` e de trabalho de UI/analytics não essencial para responder ao requisito do desafio agora. Ficam documentados como evolução, não como código.
- **REJECTED** — os relatórios "fora de escopo" listados na pesquisa (umidade, impurezas, classificação, quebra técnica, produtor, contratos, faturamento, frete, motorista, fila): meu protocolo (`{id, plate, weight}`) e meu modelo não recebem esses dados. Mesmo critério do AI-007.

### Minha responsabilidade técnica

A pesquisa gerou o mapeamento de mercado e as alternativas priorizadas. A decisão de qual subconjunto entra nesta fase — dado o prazo real até a entrevista técnica, não uma preferência estética — foi minha, assim como a decisão de tratar SHOULD/COULD como roadmap documentado em vez de código.

### Validação prevista

Ver "Decisão final" e schema adicional necessário no LOG-015: cada relatório MUST leva endpoint REST, filtros from/to/branch/grain, query testada, integration test e exemplo de JSON.

---

# 4. Como classifico uma sugestão de IA

Para cada sugestão relevante, aplico uma das seguintes classificações:

```text
ACCEPTED
```

A sugestão é coerente com requisito e design.

```text
MODIFIED
```

A ideia é válida, mas ajustei implementação ou escopo.

```text
DEFERRED
```

Pode ser útil futuramente, mas não pertence ao MVP.

```text
REJECTED
```

Não resolve um requisito atual ou adiciona custo injustificado.

---

# 5. Registro de sugestões importantes

| Sugestão / alternativa | Minha avaliação | Resultado |
|---|---|---|
| Modular monolith | Adequado ao escopo | ACCEPTED |
| Background workers desde o início | Válido, mas não necessário antes do benchmark | DEFERRED |
| Event-driven como modelo de domínio | Representa bem a telemetria | ACCEPTED |
| SQS no MVP | Sem necessidade comprovada | DEFERRED |
| Redis | Sem state distribuído no MVP | DEFERRED |
| Persistir raw readings | Necessário para auditoria e recomputabilidade — decisão revisada, ver LOG-006 | MODIFIED |
| Mediana para outliers | Simples e robusta | ACCEPTED |
| Mediana + MAD para outlier removal (leituras de balança) | Mais robusto que mediana isolada; ver LOG-007 | ACCEPTED |
| Janelas estáveis consecutivas | Reduz falso positivo | ACCEPTED |
| Máquina de estados COLLECTING→STABILIZING→STABLE | Substitui noção vaga de "janelas consecutivas" por timer testável | ACCEPTED |
| Kalman Filter como algoritmo principal de estabilização | Não resolve sozinho a detecção de "caminhão ainda entrando"; ver LOG-007 | REJECTED |
| Deduplicar por peso | Semanticamente incorreto | REJECTED |
| Idempotência na finalização | Protege efeito financeiro | ACCEPTED |
| Inventory ledger | Escopo maior que o necessário | REJECTED |
| GrainStock simples | Atende a regra atual | ACCEPTED |
| Margem via interpolação linear com base no estoque | Leitura mais direta e testável do enunciado; ver LOG-013 | ACCEPTED |
| API key estática por balança | Suficiente para o MVP, evolução para HMAC/mTLS mapeada; ver LOG-014 | ACCEPTED |
| WebSocket/SSE | Sem consumidor definido | DEFERRED |
| ML para estabilidade | Sem dataset ou justificativa | REJECTED |
| 4 relatórios MUST (Livro de Pesagens, Volume/Custo por Grão, Estoque/Margem, Desempenho por Filial) | Mapeiam direto a requisito do enunciado; ver LOG-015 | ACCEPTED |
| Relatórios SHOULD (Saúde das Balanças, Transações Pendentes) | Válidos, mas dependem do core estar pronto e testado primeiro | DEFERRED |
| Relatórios COULD (Qualidade da Estabilização, Auditoria Técnica) | Dependem de raw_readings; não essenciais ao requisito atual | REJECTED (nesta fase) |
| Não expor `plate` em relatórios agregados/dashboard | Achado de LGPD; necessidade mínima de dado pessoal | ACCEPTED |
| Relatórios de umidade/classificação/contratos/frete/etc. | Dado não existe no protocolo/modelo | REJECTED |
| `outlierToleranceKg` como campo configurável | Gap real entre LOG-007 e o record de config; ver LOG-017 | ACCEPTED |
| Conflito de placa verificado também em STABLE/RECORDED | LOG-016 só cobre "antes de STABLE"; risco de reabrir transaction já completada; ver LOG-018 | REJECTED |
| Sem retry automático se a finalização falhar pós-STABLE | Trade-off aceito do MVP, peças dependentes ainda não existiam; ver LOG-018 | ACCEPTED (nesta fase) |
| `findByTruckIdAndBranchIdAndStatus` (query já filtrada por filial) | Colapsa "sem transaction" e "transaction na filial errada" no mesmo resultado | MODIFIED (trocado por findByTruckIdAndStatus + checagem explícita) |
| Clock injetável no ScaleReadingController só para testabilidade | Custo no hot path sem necessidade real; testei com config de threshold rápido em vez | REJECTED |
| Render como alvo de deploy do dev environment | Free tier sem cartão, reaproveita Dockerfile existente; ver comparação Railway/Fly.io/VM | ACCEPTED |

---

# 6. Uso de IA para código

O desafio solicita compartilhamento do código gerado por IA.

Por isso, qualquer arquivo de produção criado ou significativamente modificado com IA deve ser registrado.

## Template

```md
## CODE-AI-XXX — Nome

### Problema que eu queria resolver

Descrição.

### Minha direção de implementação

O que eu já havia decidido antes de usar IA.

### Prompt

> Prompt exato utilizado.

### Código gerado

Arquivos:
- `...`

Trechos:
```java
...
```

### Minha revisão

O que eu:

- aceitei;
- modifiquei;
- removi;
- reescrevi.

### Por que

Justificativa técnica.

### Validação

- unit test;
- integration test;
- simulator;
- benchmark;
- revisão manual.
```

---

# 7. Registro real de código gerado por IA

A partir daqui a IA (Claude Code) deixou de ser só interlocutor de design (seções 3 e AI-001–AI-009) e passou a implementar diretamente — sempre em modo agente, nunca copiando/colando de um chat. Meu papel nessa fase mudou de forma, não desapareceu: para cada componente eu (1) exigi um plano por escrito antes de qualquer código — arquivos, desenho, testes, dúvidas em aberto — conforme o protocolo que eu mesmo defini no `CLAUDE.md` seção 24; (2) fui interrompido explicitamente sempre que a IA encontrou uma decisão de engenharia ou de produto não coberta pelos LOGs existentes, e decidi cada uma dessas vezes via pergunta direta, não a IA sozinha; (3) só aceitei um componente como pronto depois de `mvn clean verify` e, nos casos de integração (Postgres real via Testcontainers, deploy), validação contra infraestrutura real, não só teste unitário.

Os 8 registros abaixo cobrem os componentes que faltavam do MVP (`StabilizationEngine` → relatórios), o deploy do dev environment, e uma revisão final pré-entrevista — nessa ordem, PRs #16 a #24 do repositório mais a revisão registrada em LOG-019.

## CODE-AI-001 — StabilizationEngine (LOG-007)

### Problema que eu queria resolver

Implementar o algoritmo puro de estabilização — mediana+MAD para remoção de outlier, critérios de range/stdDev/slope, peso final arredondado — que eu já havia desenhado no LOG-007, mas que ainda era um stub (`UnsupportedOperationException`).

### Minha direção de implementação

Pipeline já decidido no LOG-007: mediana+MAD → threshold de outlier → range/stdDev/slope sobre amostras limpas → peso final. O que faltava era a implementação Java em si.

### Prompt

> "vamos seguir para a implementação da stabilizationEngine. mesmo processo do nosso contexto"

Pedi explicitamente o mesmo processo já usado nas decisões de arquitetura anteriores: plano por escrito, minhas dúvidas resolvidas, só depois código.

### Código gerado

Arquivos: `StabilizationEngine.java`, `StabilizationProperties.java`, `StabilizationEngineTest.java`, `application.yml`, `application-test.yml`, `LOG_DECISOES_TECNICAS.md` (novo LOG-017).

### Minha revisão

Antes de aceitar o plano, a IA identificou um gap real entre o LOG-007 e o código existente: o LOG-007 já especificava um piso mínimo de tolerância para outlier (`threshold = max(3×robustSigma, toleranciaMinima)`), mas o `StabilizationProperties` não tinha campo para isso. Me foi apresentada a escolha via pergunta direta:

- **ACCEPTED** — adicionar `outlierToleranceKg` como campo configurável (mesmo padrão dos outros 9 thresholds), em vez da alternativa de reusar `scaleResolutionKg` como proxy — decidi que são conceitos ortogonais (resolução de hardware ≠ piso estatístico de outlier) e acoplá-los criaria efeito colateral oculto.
- **ACCEPTED** — registrar essa decisão como `LOG-017`, mesmo formato dos outros logs, já nesta PR (em vez de só mencionar na descrição da PR).
- **ACCEPTED** — o pipeline do algoritmo em si (mediana+MAD, critérios, arredondamento) exatamente como eu já tinha desenhado no LOG-007, sem mudança.

### Por que

O gap do `outlierToleranceKg` é exatamente o tipo de dívida entre documentação e código que eu não quero deixar passar silenciosamente — melhor formalizar como decisão pequena (LOG-017) do que deixar como comentário perdido numa PR.

### Validação

8 testes unitários com datasets calculados à mão (ex: outlier isolado, tendência de subida, oscilação sistemática — cada um isolando um critério específico do algoritmo). `mvn clean verify` completo (34 testes) antes do merge; CI verde na PR antes do squash merge (#16).

---

## CODE-AI-002 — ScaleSession, máquina de estados (LOG-007/LOG-016)

### Problema que eu queria resolver

A confirmação de estabilidade no tempo (`COLLECTING → STABILIZING → STABLE`), troca de placa mid-window, e reset da sessão depois que o peso cai perto de zero pós-SAVE — tudo já decidido conceitualmente no LOG-016, faltando a implementação.

### Minha direção de implementação

LOG-016 já definia os dois cenários (placa muda antes de STABLE → descarta janela; peso cai perto de zero pós-SAVE → reset), mas não cobria um sub-caso: o que fazer se a placa mudar **depois** de STABLE, enquanto a sessão só está esperando o caminhão sair.

### Prompt

Sequência de "sim por favor" (retomando o trabalho após o merge do `StabilizationEngine`) seguida de "continue until project is finished please" — pedi para a IA seguir de forma mais autônoma pelas próximas peças, mas isso não significou menos checkpoints de decisão, só menos pausas para eu confirmar cada passo trivial.

### Código gerado

Arquivos: `ScaleSession.java`, `ScaleSessionManagerTest.java`, `StabilizationEngineTest.java` (2 testes de duração), `LOG_DECISOES_TECNICAS.md` (novo LOG-018).

### Minha revisão

- **ACCEPTED** — conflito de placa checado só em `COLLECTING`/`STABILIZING`; uma vez `STABLE`/`RECORDED`, uma leitura com placa diferente é ignorada para identidade da sessão (só o gate de peso-quase-zero fecha a sessão). A IA me apresentou o argumento a favor (evita reabrir uma `TransportTransaction` já `COMPLETED` por 1 frame de LPR errado) e o argumento contra (reage mais devagar a um caminhão novo genuíno) — decidi pelo primeiro, com o gate de peso como rede de segurança.
- **ACCEPTED** — registrar como `LOG-018` um gap descoberto durante o design, não durante o código: se `STABLE` for atingido mas a finalização de negócio (peça ainda não implementada nesse momento) falhar, a sessão fica presa sem retry automático. Decidi aceitar isso como trade-off do MVP em vez de bloquear a PR por uma peça que ainda nem existia.
- **MODIFIED** — a IA propôs um bound fixo (`MAX_WINDOW_SAMPLES = 40`) para a janela; pedi (e aceitei) a correção para `Math.max(40, config.minSamples())`, porque um bound fixo desacoplado do `minSamples` configurável criaria um bug silencioso se alguém recalibrasse o threshold no futuro.

### Por que

O ponto do conflito de placa pós-STABLE não estava, a rigor, coberto por nenhuma decisão minha anterior — é exatamente o tipo de ambiguidade que eu quero decidir explicitamente, não deixar a IA resolver sozinha por inferência.

### Validação

5 testes reativados (2 de duração no `StabilizationEngineTest`, 3 de comportamento de sessão no `ScaleSessionManagerTest`, incluindo concorrência com 16 threads). `mvn clean verify`, CI verde (#17).

---

## CODE-AI-003 — ScaleAuthFilter (LOG-014)

### Problema que eu queria resolver

Validar o header `X-Scale-Key` antes do hot path, sem tocar o banco por reading — já decidido no LOG-014, faltando a implementação do filtro.

### Minha direção de implementação

SHA-256 contra `ScaleCredentialsCache` em memória (LOG-014), 401 genérico sem revelar se o `scaleId` existe.

### Prompt

Continuação do mesmo fluxo autônomo ("continue until project is finished please").

### Código gerado

Arquivos: `ScaleAuthFilter.java` (implementação + duas classes internas para tornar o corpo da request relível depois do filtro ler o `id`), `IngestionWebConfig.java`, `ScaleAuthFilterTest.java`.

### Minha revisão

- **ACCEPTED** — usar `ContentCachingRequestWrapper` + um wrapper próprio para "devolver" os bytes já lidos ao controller depois do filtro espiar o campo `id` do corpo — é o idiom padrão do Spring para esse problema específico (stream de single-consumo lido por dois consumidores diferentes), não uma abstração inventada.
- **ACCEPTED** — reusar o record `ScaleReadingRequest` (via `ObjectMapper`) para extrair o `id`, em vez de parsear um JSON node cru — mais simples e reaproveita um tipo que já existia.
- **ACCEPTED** — usar `MockHttpServletRequest`/`MockFilterChain` do `spring-test` (já era dependência) em vez de mocks manuais do Servlet API para os testes.

### Por que

Não tinha alternativa mais simples para o problema do stream single-consumo — validei que era o padrão real do Spring, não uma solução ad-hoc.

### Validação

7 testes (incluindo um que criei a mais, além dos 4 já fixados como `@Disabled`, para provar que o corpo realmente chega intacto no controller depois do filtro). CI verde (#18).

---

## CODE-AI-004 — CompleteWeighingUseCase (LOG-008/009/011)

### Problema que eu queria resolver

O único ponto de finalização de uma pesagem: resolver a `TransportTransaction` OPEN do caminhão, calcular net/custo em `BigDecimal`, persistir, atualizar estoque, completar a transaction — tudo dentro de uma única `@Transactional`.

### Minha direção de implementação

LOG-008 (idempotência via `UNIQUE(transport_transaction_id)`), LOG-009 (boundary transacional único), LOG-011 (arquitetura). O repositório já tinha um método `findByTruckIdAndBranchIdAndStatus`, mas com um comentário sinalizando que a resolução de "zero ou mais de uma transaction OPEN" era uma assumption ainda não validada.

### Prompt

Continuação do fluxo autônomo.

### Código gerado

Arquivos: `CompleteWeighingUseCase.java`, `TransportTransactionRepository.java` (método trocado), `CompleteWeighingUseCaseTest.java`.

### Minha revisão

- **MODIFIED** — troquei `findByTruckIdAndBranchIdAndStatus` (filtra por branch na query) por `findByTruckIdAndStatus` (sem filtro de branch) + checagem explícita de branch depois, no use case. Motivo: com o método antigo, "sem transaction aberta" e "transaction aberta na filial errada" produziam o mesmo resultado (não encontrado) — e os 2 testes já fixados no scaffold (`ambiguousOpenTransactionMatchThrowsBusinessRuleViolation` e `mismatchedScaleBranchAndTransactionBranchIsRejected`) exigem que sejam distinguíveis.
- **ACCEPTED** — reusar `NotFoundException`/`BusinessRuleViolationException` + `GlobalExceptionHandler` já existentes, em vez de criar tipos de exceção novos.
- **ACCEPTED** — pre-check via `existsByTransportTransactionId` para dar erro claro no caso comum, mas mantendo a constraint `UNIQUE` do banco como a garantia real contra uma race de verdade — não tentei substituir uma pela outra.

### Por que

A mudança do método de repositório é a decisão mais significativa desta PR — sem ela, um dos dois testes de contrato já fixados no scaffold seria impossível de satisfazer com sentido (o cenário que ele descreve nunca aconteceria).

### Validação

5 testes com Mockito. CI verde (#19).

---

## CODE-AI-005 — ScaleReadingController (orquestração)

### Problema que eu queria resolver

Ligar todas as peças anteriores no endpoint `POST /api/readings`: buffer de auditoria, sessão + engine, delegar para `CompleteWeighingUseCase` quando `STABLE`.

### Minha direção de implementação

Hot path síncrono (LOG-004), sempre responder 202 rápido — a balança é fire-and-forget.

### Prompt

Continuação do fluxo autônomo.

### Código gerado

Arquivos: `ScaleReadingController.java`, `ScaleReadingControllerTest.java` (novo — não existia stub `@Disabled` para este componente no scaffold original).

### Minha revisão

- **ACCEPTED** — se `CompleteWeighingUseCase.complete()` lançar exceção, capturar, logar em `ERROR`, e mesmo assim devolver 202 — consistente com LOG-018 (sem retry automático nesta versão) e com o comentário original do stub ("sempre retornar 202/204 rápido").
- **ACCEPTED** — não injetar um `Clock` no hot path só para facilitar teste; usar `System.currentTimeMillis()` direto (mesmo padrão já usado em outras partes do código) e testar o caminho `STABLE` com uma config de thresholds mais rápida em vez de mockar o relógio.

### Por que

Adicionar abstração de `Clock` só para testabilidade, num caminho que roda a cada ~100ms por balança, ia contra o princípio de manter o hot path barato — testei de outro jeito em vez de mudar produção por causa do teste.

### Validação

5 testes (mock só nas duas dependências que precisam de banco: `CompleteWeighingUseCase` e `RawReadingBuffer`; `ScaleSessionManager`/`StabilizationEngine` reais). CI verde (#20).

---

## CODE-AI-006 — Relatórios administrativos (LOG-015)

### Problema que eu queria resolver

Os 4 relatórios MUST já fechados no LOG-015 (Livro de Pesagens, Volume/Custo por Grão, Estoque/Oportunidade de Margem, Desempenho por Filial), ainda como stubs.

### Minha direção de implementação

Contrato de resposta já definido (LOG-015): `{period, filters, data}`. Cálculo de margem no domínio, não em SQL (LOG-013).

### Prompt

Continuação do fluxo autônomo.

### Código gerado

Arquivos: os 4 `*ReportService`, `WeighingRepository` (3 queries JPQL novas), `GrainStockRepository` (1 query nova), 3 records de projeção novos (`GrainTypeCostAggregate`, `BranchCostAggregate`, `GrainStockDetail`), `ReportsIntegrationTest.java` (novo, Testcontainers), `WeighingFlowIntegrationTest.java` (reativado).

### Minha revisão

- **ACCEPTED, estendido** — o princípio "margem calculada no domínio, não em SQL" que eu já tinha fixado no LOG-013 foi aplicado a **toda** razão derivada (custo médio/ton, participação por filial), não só à margem — decisão da IA que aceitei porque é a mesma lógica já validada por mim para outro caso.
- **ACCEPTED** — `MIN_MARGIN`/`MAX_MARGIN` (5%–20%) como constantes fixas no código, não configuráveis via `application.yml` — diferente dos thresholds de estabilização, esses dois números vêm literalmente do enunciado do desafio, não são uma assumption calibrável.
- **REJECTED e corrigido depois via CI** — a primeira tentativa de `WeighingFlowIntegrationTest` não checava o status HTTP de cada chamada de setup, e um teste de integração com Testcontainers apresentava um bug real de infraestrutura de teste (container Postgres compartilhado sendo parado entre classes de teste — gotcha conhecido do Testcontainers com container `static` numa superclasse). Corrigi isso pedindo o padrão oficial "singleton container" do Testcontainers em vez de aceitar um retry/wait como paliativo (a primeira tentativa de correção, baseada num diagnóstico errado de "blip transitório", não teria funcionado — o log do CI mostrou que a indisponibilidade durava o teste inteiro, não segundos).
- **ACCEPTED** — depois de corrigir a infraestrutura de teste, o CI revelou um segundo problema real: o teste criava uma `Branch`/`GrainType` novos mas nunca provisionava a linha `GrainStock` correspondente (não existe endpoint REST para isso — estoque é provisionado fora de banda). Corrigi o teste, não o código de produção, que já se comportava corretamente ao exigir a linha pré-existente.

### Por que

O ciclo de correção via CI (não local — Testcontainers não funcionava no ambiente local desta sessão) é o exemplo mais concreto de "só aceito quando testado de verdade": duas rodadas de bug real encontradas e corrigidas, nenhuma delas visível rodando só os testes unitários.

### Validação

`ReportsIntegrationTest` (4 testes, dataset de 2 filiais/2 grãos calculado à mão) e `WeighingFlowIntegrationTest` (fluxo HTTP completo, cadastro → readings → STABLE → relatório) contra Postgres real via Testcontainers — 4 rodadas de CI até ficar verde, cada uma corrigindo um problema real e distinto. CI verde (#21).

---

## CODE-AI-007 — Deploy do dev environment (Render)

### Problema que eu queria resolver

Com a lógica core pronta, decidir e executar o deploy do dev environment que eu tinha deliberadamente adiado no início ("Implementar a lógica core primeiro, deploy depois").

### Minha direção de implementação

Nenhuma alvo de hospedagem escolhido ainda; só a containerização (Dockerfile/docker-compose) já validada numa fase anterior.

### Prompt

> "ajude a decidir agora (Railway/Render/Fly.io/VM, com trade-offs de cada um" — pedi uma comparação explícita antes de decidir, não uma escolha pronta da IA.

Depois, já com o `render.yaml` escrito mas não testado (sem acesso a conta Render na sessão anterior): "I'm logged into render via the cli, auxiliate on deployment and test".

### Código gerado

Arquivos: `render.yaml`, `application-render.yml` (novo profile Spring), `application.yml` (`server.port` mudou de fixo para `${PORT:8080}`), seção "Deploy" no `README.md`.

### Minha revisão

- **ACCEPTED** — Render entre as 4 opções apresentadas (Railway, Fly.io, VM), depois de comparar trade-offs reais: free tier sem cartão, reaproveita o `Dockerfile` que já existia, Postgres gerenciado no mesmo Blueprint. Decisão minha, não a mais "impressionante" tecnicamente (uma VM seria mais parecida com produção) — a mais adequada ao prazo real até a entrevista.
- **ACCEPTED** — `application-render.yml` interpolando a URL JDBC a partir de `PGHOST`/`PGPORT`/`PGDATABASE` em vez de usar a connection string pronta do Render — o Render Blueprint só expõe propriedades separadas, e a IA validou isso contra a API real (`render blueprints validate`) antes de eu criar qualquer recurso de verdade.
- **ACCEPTED** — deploy feito via `render` CLI (Postgres + web service criados diretamente, sem precisar do dashboard) depois que percebi que a CLI já estava autenticada — mudança de plano em relação ao README original (que só descrevia passos manuais no dashboard, escritos numa sessão sem acesso à minha conta).
- **Validação que eu exigi antes de aceitar como "pronto"**: não bastou o deploy subir — pedi teste manual do fluxo completo contra a instância real (leituras até `STABLE`, transaction completando, os 4 relatórios com valores conferidos à mão), porque um deploy que só "sobe" sem processar nada de verdade não prova nada.

### Por que

Documentar a decisão do alvo de deploy era importante para mim porque é uma decisão de produto/custo, não só técnica — não queria que a IA escolhesse sozinha algo que eu teria que sustentar/pagar depois.

### Validação

`render blueprints validate` (schema correto antes de criar qualquer recurso); deploy real via CLI; teste manual completo contra `https://grainweighing.onrender.com` — cadastro, readings até STABLE, transaction COMPLETED, 4 relatórios com valores corretos (net=23000kg, cost=R$41.400, margem=13,55%); logs de deploy conferidos (migrations aplicadas, porta correta, sem warnings).

---

## CODE-AI-008 — Correções cirúrgicas pré-entrevista: lost update e gross ≤ tare (LOG-019)

### Problema que eu queria resolver

Numa revisão final antes da entrevista, dois riscos de consistência reais no caminho de finalização: `GrainStockService.increaseAvailableQuantity` fazia read-modify-write via entidade gerenciada (lost update possível entre duas balanças da mesma filial/grão finalizando quase ao mesmo tempo), e `CompleteWeighingUseCase` não rejeitava `grossWeight <= tareWeight` antes de persistir.

### Minha direção de implementação

Já cheguei com o diagnóstico e a correção esperada definidos (documentei em LOG-019 antes de pedir código): trocar o incremento por `UPDATE` atômico no Postgres, e adicionar uma guarda de `BusinessRuleViolationException` antes de qualquer persistência quando `grossWeight <= tareWeight`.

### Prompt

Plano escrito por mim, cobrindo os dois riscos concretos, o resultado esperado de cada correção, e o que deveria continuar documentado como limitação (retry, buffer de raw_readings, autenticação de relatórios) em vez de "resolvido" nesta rodada.

### Código gerado

Arquivos: `GrainStockRepository.java` (`@Modifying @Query` com `UPDATE ... SET x = x + :delta`), `GrainStockService.java` (usa o retorno de linhas afetadas em vez de reler a entidade), `GrainStock.java` (removido `increase()`, sem uso depois da mudança), `CompleteWeighingUseCase.java` (guarda de gross/tare), `CompleteWeighingUseCaseTest.java` (novo teste), `GrainStockConcurrencyIntegrationTest.java` (novo, Testcontainers), `LOG_DECISOES_TECNICAS.md` (LOG-019), `BLUEPRINT.md` (seções 2, 3, 6, 9, 10 novo).

### Minha revisão

- **ACCEPTED** — antes de qualquer edição, pedi para a IA confirmar os dois problemas lendo o código atual linha a linha (não assumir a partir da minha descrição) — ela apontou exatamente `GrainStockService.java:21-26` e a ausência de checagem em `CompleteWeighingUseCase.java:74-88`, batendo com o que eu já tinha identificado.
- **ACCEPTED** — remover `GrainStock.increase()` em vez de deixar como código morto — a IA perguntou se havia outro caller antes de remover (não havia).
- **ACCEPTED** — teste de concorrência via `TransactionTemplate` manual (simulando duas chamadas HTTP concorrentes, cada uma com sua própria transação curta) em vez de anotar o método do repositório com `@Transactional` — isso teria violado a regra que eu mesmo escrevi no CLAUDE.md §12 (só a finalização de negócio abre transaction).
- **MODIFIED (processo, não código)** — a IA encontrou uma incompatibilidade real entre `Testcontainers 1.20.1` (fixado no `pom.xml`) e a versão do Docker Desktop instalada nesta máquina, que impedia validar a suíte de integração completa. Em vez de contornar silenciosamente (ex: fazer bump de versão major sem eu decidir), ela apresentou o diagnóstico (log de estratégias do Testcontainers, erro HTTP 400 na negociação com o daemon) e propôs alternativas; eu decidi não fazer o bump de versão agora — fora de escopo pra véspera de entrevista — e aceitei a prova alternativa (concorrência real via `psql` direto contra o Postgres do `docker-compose`) como evidência suficiente de que o mecanismo de `UPDATE` atômico funciona, complementando os testes unitários (que rodaram limpos sob JDK 17).

### Por que

Essas duas correções são exatamente o tipo de bug que uma revisão de consistência tem que pegar antes de uma entrevista técnica que avalia justamente concorrência e correção de dados — silenciar ou não testar teria sido pior do que documentar a limitação de ambiente que impediu a validação mais forte (Testcontainers) e compensar com uma prova direta no banco.

### Validação

`CompleteWeighingUseCaseTest` (6 testes, incluindo o novo `grossWeightNotGreaterThanTareWeightIsRejected`) e os outros 3 arquivos de teste unitário — 41 testes, `mvn test` verde sob JDK 17. `GrainStockConcurrencyIntegrationTest` escrito e correto por revisão de código, mas não executado nesta máquina (Testcontainers/Docker incompatível — ver acima); prova equivalente feita com 20 processos `psql` concorrentes contra o Postgres real do `docker-compose` (1000 → 1200 exato, sem perda). Pendência explícita: confirmar `GrainStockConcurrencyIntegrationTest` verde em CI ou numa máquina com Testcontainers/Docker compatíveis antes de considerar o teste definitivamente validado em execução, não só em leitura.

---

# 8. O que eu não delego à IA

Não considero IA responsável por:

- entender o requisito final;
- escolher a arquitetura final;
- aceitar trade-offs;
- definir garantias do sistema;
- declarar performance;
- decidir se uma failure mode é aceitável;
- aprovar código para entrega.

Essas responsabilidades permanecem minhas.

---

# 9. Critério de aceitação de código assistido por IA

Código gerado só entra no projeto se eu conseguir:

1. explicar o que ele faz;
2. justificar por que ele existe;
3. identificar seus failure modes principais;
4. alterar a implementação sem depender do prompt original;
5. criar ou revisar testes que provem seu comportamento.

Se eu não consigo fazer isso, o código não é considerado pronto para entrega.

---

# 10. Relação com o Log de Decisões Técnicas

Este arquivo responde:

> **Como utilizei IA?**

O arquivo `LOG_DECISOES_TECNICAS.md` responde:

> **Por que eu tomei cada decisão?**

A relação é:

```text
minha hipótese
      ↓
AI-assisted exploration
      ↓
minha decisão
      ↓
implementação
      ↓
validação
```

O Log de Decisões Técnicas é a fonte principal para a arquitetura.

Este documento existe para dar transparência ao uso de IA.

---

# Conclusão

Eu utilizei IA como uma ferramenta de engenharia, não como substituta do processo de engenharia.

O valor que busquei extrair foi:

- ampliar rapidamente o conjunto de alternativas;
- encontrar edge cases que eu poderia não considerar imediatamente;
- desafiar minhas próprias escolhas;
- reduzir trabalho mecânico;
- acelerar feedback durante design e implementação.

A responsabilidade pela solução permanece comigo:

> **eu defino a direção, a IA ajuda a pressioná-la, e testes/medição determinam se ela se sustenta.**
