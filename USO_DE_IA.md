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

# 7. Exemplo de registro futuro de código

> ⚠️ **Pendente antes do envio.** O registro abaixo é apenas o template preenchido como exemplo, não um `CODE-AI-XXX` real. Antes de enviar, substituir por registros reais dos arquivos efetivamente gerados/modificados com apoio de IA — arquivo real, prompt exato, trecho de código gerado e minha revisão.

```md
## CODE-AI-001 — StabilityAlgorithm

### Problema que eu queria resolver

Implementar a estratégia de estabilidade que defini no design.

### Minha direção de implementação

Eu já havia decidido usar:

- janela limitada;
- mediana;
- spread;
- trend;
- confirmação consecutiva.

### Prompt

> Implemente esta estratégia seguindo estes critérios...

### Código gerado

`StabilityAlgorithm.java`

### Minha revisão

MODIFIED

Eu alterei:
- nomes;
- limites;
- comportamento em insufficient samples;
- configuração;
- tratamento de outliers.

### Validação

`StabilityAlgorithmTest`
```

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
