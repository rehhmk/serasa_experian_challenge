# CLAUDE.md

# Contexto de Engenharia — Grain Weighing Platform

Este repositório é um **take-home técnico de backend**.

Você está atuando como **implementation assistant, pair programmer e reviewer**.

A arquitetura, as decisões de produto, os trade-offs e a responsabilidade técnica pertencem ao engenheiro responsável pelo projeto.

Seu papel é:
- ajudar a implementar decisões já tomadas;
- apontar inconsistências;
- sugerir alternativas quando houver risco real;
- encontrar edge cases;
- escrever e revisar testes;
- reduzir trabalho mecânico;
- ajudar a manter documentação e código coerentes.

Seu papel **não é decidir a arquitetura autonomamente**.

---

## 1. Fontes de verdade

Antes de implementar qualquer mudança relevante, leia nesta ordem:

1. `desafio-tecnico-backend_ia.pdf` — requisito original;
2. `BLUEPRINT.md` — desenho atual da solução;
3. `LOG_DECISOES_TECNICAS.md` — decisões e trade-offs do engenheiro;
4. `USO_DE_IA.md` — registro de uso de IA;
5. `README.md` — contrato operacional do projeto.

Regra de precedência:

```text
REQUISITO ORIGINAL
        ↓
DECISÕES EXPLÍCITAS DO ENGENHEIRO
        ↓
BLUEPRINT / README
        ↓
IMPLEMENTAÇÃO ATUAL
        ↓
SUGESTÕES DA IA
```

Se dois documentos entrarem em conflito, **não escolha silenciosamente um deles**. Mostre a divergência e peça uma decisão.

---

## 2. Problema

A empresa possui diversas filiais e balanças rodoviárias utilizadas para pesar caminhões carregados com grãos.

Cada balança possui um ESP32 integrado ao equipamento e uma câmera LPR.

Enquanto um caminhão está presente, o dispositivo envia leituras aproximadamente a cada 100 ms.

Payload externo:

```json
{
  "id": "scale-01",
  "plate": "ABC1D23",
  "weight": 32010
}
```

O backend precisa:

```text
receber muitas leituras concorrentes
        ↓
validar
        ↓
normalizar
        ↓
manter estado temporário por balança
        ↓
detectar estabilidade
        ↓
produzir uma única pesagem confiável
        ↓
calcular informações de negócio
        ↓
persistir
```

A aplicação também possui CRUDs e relatórios administrativos.

---

## 3. Arquitetura atual

A solução do MVP é deliberadamente simples.

```text
ESP32 / LPR
     ↓
HTTP API
     ↓
Validation / Normalization
     ↓
ScaleSessionManager
     ↓
Stabilization Engine
     ↓
if STABLE
     ↓
CompleteWeighingUseCase
     ↓
PostgreSQL
```

É um **modular monolith**.

Não introduzir microservices ou infraestrutura distribuída sem aprovação explícita.

---

## 4. Hot path de telemetria

O caminho de uma leitura deve permanecer extremamente barato.

```text
deserialize
→ basic validation
→ normalize
→ locate ScaleSession
→ append bounded sample
→ evaluate stability
→ return
```

**Não acessar PostgreSQL para cada reading.**

A maior parte das leituras deve executar somente operações de CPU/memória. O banco entra no caminho apenas quando houver um resultado de negócio consolidado.

---

## 5. Síncrono versus assíncrono

A hipótese inicial do engenheiro considerava background workers e event-driven processing.

Após análise do workload, a decisão atual para o MVP é:

> processar cada reading sincronamente dentro da instância enquanto o hot path permanecer barato.

Isso não significa que a pesagem final é conhecida imediatamente.

```text
reading
reading
reading
reading
...
      ↓
STABLE
      ↓
Weighing
```

Event-driven continua sendo uma boa forma de **modelar os fatos do domínio**, mas não é necessário introduzir uma fila interna apenas para chamar a solução de event-driven.

Não adicionar sem aprovação:
- `@Async`;
- executor/worker pool;
- `BlockingQueue`;
- SQS;
- Kafka;
- RabbitMQ.

---

## 6. Estado por balança

O estado transitório de estabilização é mantido por `scaleId`.

```text
ConcurrentHashMap<ScaleId, ScaleSession>
```

Uma `ScaleSession` pode conter:

```text
scaleId
plate
bounded recent readings
session state
consecutive stable windows
firstReadingAt
lastReadingAt
```

`ConcurrentHashMap` protege operações do mapa, mas **não torna `ScaleSession` automaticamente thread-safe**.

A sincronização deve ser local à sessão/balança. Nunca usar lock global.

```text
Scale A → Session A
Scale B → Session B
Scale C → Session C
```

---

## 7. Buffer

A janela de readings deve possuir tamanho limitado.

Preferir estrutura circular/ring buffer ou outra implementação bounded.

Evitar:

```text
lista crescendo indefinidamente
remove(0) em ArrayList no hot path
armazenamento ilimitado de telemetry
```

---

## 8. Stabilization Engine

O algoritmo deve ser:

- determinístico;
- explicável;
- testável;
- configurável.

Estratégia atual:

```text
sliding window
      ↓
robust central value
      ↓
dispersion
      ↓
trend
      ↓
consecutive confirmation
      ↓
STABLE
```

Elementos considerados:
- median;
- spread / range;
- trend / slope;
- tratamento de outliers;
- consecutive stable windows.

Os thresholds são **assumptions configuráveis do MVP**.

Não tratá-los como tolerâncias regulatórias ou verdades físicas.

Não substituir por machine learning, Kalman Filter ou algoritmo estatístico complexo sem decisão explícita.

Princípio:

> Em caso de dúvida, é preferível continuar medindo a produzir uma falsa estabilização.

---

## 9. Estado de sessão

Modelo conceitual:

```text
EMPTY
  ↓
MEASURING
  ↓
CANDIDATE
  ↓
STABLE
  ↓
RECORDED
```

Inatividade pode encerrar/resetar uma sessão porque o protocolo transmite apenas enquanto o caminhão está presente.

Mudança de placa dentro da mesma sessão é conflito de consistência. Não misturar readings de placas diferentes silenciosamente.

---

## 10. Domínio de negócio

Entidades principais:

```text
Truck
GrainType
Branch
Scale
TransportTransaction
Weighing
GrainStock
```

`TransportTransaction` representa uma operação de transporte/pesagem de um tipo de grão.

Status:

```text
OPEN
COMPLETED
CANCELLED
```

A transaction guarda snapshot do preço de compra aplicável à operação.

---

## 11. Finalização da pesagem

Quando estabilidade for detectada:

```text
StableWeightDetected
        ↓
CompleteWeighingUseCase
```

Responsabilidades:

```text
resolver Truck / TransportTransaction
obter tare
gross - tare = net
converter kg → toneladas
calcular custo
persistir Weighing
atualizar GrainStock
completar TransportTransaction
```

Dinheiro deve usar `BigDecimal`.

---

## 12. Boundary transacional

Somente a finalização do evento de negócio deve abrir transaction de banco.

```java
@Transactional
public void completeWeighing(...) {
    ...
}
```

Nunca manter DB transaction aberta durante os segundos de estabilização.

A finalização deve ser atômica:

```text
Weighing
+
stock update
+
TransportTransaction COMPLETED
```

ou nenhuma alteração deve ser confirmada.

---

## 13. Idempotência

O protocolo externo não fornece:

```text
readingId
sequence
deviceTimestamp confiável
```

Portanto, **não implementar deduplicação por payload**.

Especialmente não usar:

```text
scaleId + plate + weight
```

Leituras idênticas consecutivas podem ser amostras legítimas.

A garantia deve existir no boundary de negócio:

> uma `TransportTransaction` produz no máximo uma `Weighing`.

Usar quando apropriado:

```text
session state RECORDED
transaction status
UNIQUE(transport_transaction_id)
DB transaction
```

---

## 14. Persistência de telemetry

Por padrão, **não introduzir persistência de cada raw reading no banco transacional**.

Se a implementação atual possuir uma decisão posterior diferente documentada em `LOG_DECISOES_TECNICAS.md`, sinalize a divergência antes de alterar código.

---

## 15. Relatórios

Relatórios pertencem ao read-side administrativo e não devem aumentar o custo do hot path.

Prioridade atual:

```text
MUST
- livro/listagem de pesagens
- volume e custo por tipo de grão
- estoque e oportunidade de margem
- desempenho por filial

SHOULD
- saúde operacional das balanças
- transações abertas / exceções

COULD
- qualidade detalhada da estabilização
- auditoria técnica de readings, somente se os dados existirem
```

Não inventar dados que o domínio não possui.

Fora do escopo atual:

```text
umidade
impurezas
classificação física do grão
frete
motorista
contratos comerciais completos
faturamento
tempo de fila na portaria
```

---

## 16. Reports ≠ Observability

Separar:

```text
BUSINESS / ADMIN REPORTING
volume
cost
inventory
margin
branch performance
weighings
```

de:

```text
TECHNICAL OBSERVABILITY
HTTP latency
request rate
error rate
CPU
heap
GC
lock contention
```

Métricas do algoritmo podem auxiliar operação técnica, mas não devem ser apresentadas automaticamente como KPIs administrativos.

---

## 17. Performance

Não declarar capacidade sem medição.

Carga derivada do protocolo:

```text
1 scale ≈ 10 req/s
10 scales ≈ 100 req/s
100 scales ≈ 1,000 req/s
250 scales ≈ 2,500 req/s
```

São workloads de teste, não capacidade garantida.

Benchmark deve medir quando possível:

```text
throughput
p50
p95
p99
error rate
CPU
heap
GC
lock contention
```

Antes de sugerir mensageria ou horizontal scaling:

1. identificar gargalo;
2. mostrar evidência;
3. sugerir a menor mudança;
4. informar trade-offs.

---

## 18. Logging

Não gerar `INFO` para cada telemetry reading.

Preferir eventos relevantes:

```text
session started
candidate stability
stable
session expired
plate conflict
weighing completed
error
```

Use métricas para contagens de alta frequência.

---

## 19. Stack

```text
Java 17+
Spring Boot 3
Spring Web
Spring Data JPA
Bean Validation
PostgreSQL
Flyway
JUnit 5
Mockito
Testcontainers
Docker Compose
OpenAPI
```

Não substituir stack sem aprovação.

---

## 20. Estilo de código

Priorizar:
- simplicidade;
- nomes explícitos;
- funções pequenas;
- domínio compreensível;
- poucas abstrações;
- testabilidade.

Evitar abstrações sem necessidade real:

```text
FactoryFactory
CommandBus
Mediator
AbstractProcessorChain
generic event framework
custom dependency injection
```

Não aplicar patterns apenas para demonstrar conhecimento.

O código deve ser fácil de explicar durante uma entrevista.

---

## 21. Testes do core

Ao alterar estabilização ou sessões, considerar:

```text
stable sequence
unstable sequence
gradual convergence
single outlier
multiple outliers
temporarily stable sequence
insufficient samples
threshold boundary
plate change
session timeout
same-scale concurrency
different-scale concurrency
double finalization
```

Integração principal:

```text
OPEN TransportTransaction
        ↓
sequence of readings
        ↓
STABLE
        ↓
exactly one Weighing
        ↓
TransportTransaction COMPLETED
        ↓
correct net / cost / stock
```

---

## 22. Non-goals do MVP

Não adicionar sem autorização:

```text
microservices
Kafka
SQS
RabbitMQ
Kinesis
Redis
ElastiCache
DynamoDB
Kubernetes
EKS
ECS infrastructure
Terraform
AWS IoT Core
event sourcing
CQRS framework
full inventory ledger
LPR image processing
operator dashboard
grain quality classification
ML stabilization
```

Esses itens podem ser discutidos como evolução de produção.

---

## 23. Evolução para produção

Quando um requisito quebrar uma assumption do MVP, propor evolução incremental.

```text
MVP
HTTP
 ↓
Spring Boot
 ↓
ScaleSession memory
 ↓
PostgreSQL
```

Se passar a existir entrega durável:

```text
HTTP
 ↓
durable queue
 ↓
workers
 ↓
PostgreSQL
```

Infraestrutura entra **depois do motivo**, nunca antes.

---

## 24. Protocolo de trabalho com IA

Para qualquer feature não trivial, antes de escrever código apresente:

```text
1. requisito relacionado
2. decisão existente relacionada
3. arquivos que pretende alterar
4. desenho da mudança
5. testes necessários
6. assumptions ou dúvidas abertas
```

Espere aprovação quando houver nova decisão de arquitetura ou produto.

Depois de implementar, apresente:

```text
1. arquivos criados/modificados
2. resumo do comportamento
3. decisões humanas utilizadas
4. assumptions feitas
5. testes executados
6. resultado dos testes
7. divergências encontradas
8. pontos que merecem revisão humana
```

---

## 25. Regra de escalonamento de decisão

Se encontrar uma questão não definida, não invente silenciosamente.

Classifique-a:

```text
IMPLEMENTATION DETAIL
```

Pode escolher a solução mais simples e explicar.

```text
ENGINEERING DECISION
```

Apresente opções e trade-offs; aguarde decisão.

```text
PRODUCT ASSUMPTION
```

Sinalize explicitamente; não trate como requisito.

```text
REQUIREMENT CONFLICT
```

Pare e mostre a divergência.

---

## 26. Registro de uso de IA

Após alteração significativa, forneça:

```md
### AI Assistance Record

**Goal**
...

**Human direction**
...

**Files assisted**
- ...

**What AI suggested/generated**
...

**Human changes/decisions**
...

**Validation**
...
```

Esse conteúdo poderá ser incorporado manualmente ao `USO_DE_IA.md`.

Nunca atribua à IA decisões já documentadas como humanas.

---

## 27. Modo reviewer

Quando solicitado a revisar, não altere código imediatamente.

Procure:

```text
correctness
race conditions
transactional bugs
false stabilization
unbounded memory
DB calls no hot path
duplicate finalization
hidden assumptions
overengineering
missing tests
documentation drift
```

Classifique findings como:

```text
BUG
RISK
MISSING TEST
ASSUMPTION
OPTIONAL IMPROVEMENT
```

---

## 28. Critério de qualidade

Uma alteração só está pronta quando o engenheiro responsável consegue:

```text
explicar
justificar
testar
modificar
defender o trade-off
```

Código impressionante, mas difícil de explicar, é pior para este projeto que código simples e correto.

---

## 29. Primeira instrução ao iniciar uma sessão

Ao iniciar uma nova sessão:

```text
read requirement
read architecture
read technical decisions
inspect current implementation
identify current task
```

Depois responda com:

```text
Current understanding
Relevant decisions
Files involved
Proposed plan
Questions/assumptions
```

Não comece reestruturando o projeto.

---

## 30. Convenção de commits e Pull Requests

Este projeto segue commits semânticos (Conventional Commits) e PRs pequenas, no mesmo espírito de "poucas abstrações, fácil de explicar" já aplicado ao código.

### Commits

Formato: `<tipo>(<escopo opcional>): <descrição no imperativo, em inglês>`

Tipos:
```text
feat      nova funcionalidade
fix       correção de bug
refactor  mudança de código sem alterar comportamento
test      adição/ajuste de testes
docs      mudança em documentação (BLUEPRINT.md, LOG_DECISOES_TECNICAS.md, etc.)
chore     configuração, dependências, build
perf      melhoria de performance
```

Exemplos:
```text
feat(ingestion): add StabilizationEngine median+MAD outlier removal
fix(stock): correct margin clamp when stock exceeds reference
test(weighing): cover double finalization in CompleteWeighingUseCase
docs(log-015): close report scope decision
```

### Pull Requests

Preferir PRs pequenas e focadas em uma decisão/feature por vez — mesmo critério de escopo já aplicado ao resto do projeto ("qual problema concreto justifica isso agora"). Exemplos de fatiamento: um PR por migration + entidade de cadastro, um PR pelo `StabilizationEngine` isolado com testes, um PR pelo `CompleteWeighingUseCase`, um PR por endpoint de relatório.

Evitar PR único "implementa tudo" — dificulta revisão e não demonstra processo de trabalho incremental.
