# BLUEPRINT — Grain Weighing Platform MVP

> Este é o documento de entrada. Justificativa completa, fórmulas, alternativas
> descartadas e processo de decisão estão em `LOG_DECISOES_TECNICAS.md`. Uso de
> IA e prompts reais estão em `USO_DE_IA.md`. Este blueprint resume as duas
> coisas e linka para os detalhes.

---

## 1. Contexto e objetivo

Balanças com ESP32 (fire-and-forget, sem aguardar resposta) enviam leituras de
peso a cada 100ms enquanto houver caminhão presente. O sinal é ruidoso — o
sistema precisa decidir, de forma confiável e sem intervenção humana, o
momento em que o peso está estabilizado, persistir esse resultado como uma
`Weighing`, e usá-lo para calcular custo e oportunidade de lucro por tipo de
grão.

O risco técnico central não está nos cadastros — está em transformar uma
sequência de leituras físicas ruidosas e concorrentes (múltiplas balanças, ao
mesmo tempo) numa única pesagem de negócio confiável.

## 2. Arquitetura final

```text
ESP32 (fire-and-forget)
    ↓
HTTP POST /readings  { id, plate, weight }
    ↓
Auth filter (X-Scale-Key)                              [LOG-014]
    ↓ inválido → 401, fim
    ↓ válido
Hot path síncrono                                       [LOG-004]
    deserialize → cheap validation → normalize
    ↓
ScaleSession (scaleId → estado em memória, isolado)      [LOG-005]
    ↓
Stabilization Engine                                     [LOG-007]
    mediana+MAD → outlier removal
    → range / stdDev / slope
    → COLLECTING → STABILIZING → STABLE
    ↓
    ├─ não estável ainda → return, mantém em memória
    ↓
    └─ STABLE
        ↓
        CompleteWeighingUseCase  (@Transactional)         [LOG-009]
            cria Weighing
            calcula net weight e custo
            atualiza GrainStock
            completa TransportTransaction
            UNIQUE(transport_transaction_id)               [LOG-008]
        ↓
        PostgreSQL
        ↓
    Reset da ScaleSession (peso cai a zero, ou troca      [LOG-016]
    de placa mid-window → descarta janela e reinicia)

(fora do hot path, assíncrono/bufferizado)
raw_readings — toda leitura, para auditoria e recálculo   [LOG-006, revisado]
```

Não há fila/mensageria interna no MVP. O custo por reading é baixo o
suficiente (sem I/O de banco no caminho normal) para processar de forma
síncrona na própria instância. Fila e workers são evolução condicionada a
evidência de backpressure — ver seção 7.

## 3. Modelo de dados

**Cadastros:**
`Caminhão` (placa, tara) · `TipoDeGrão` (nome, preço de compra/ton,
estoque de referência kg — LOG-013/LOG-015) · `Filial` · `Balança` (id,
filialId, apiKeyHash) · `TransportTransaction` (caminhão, tipo de grão,
filial, status OPEN/COMPLETED, início/fim)

**Núcleo de negócio:**

```text
Weighing
- id
- transportTransactionId  (UNIQUE — garante idempotência, LOG-008)
- scaleId
- plate
- grossWeight, tareWeight, netWeight
- grainTypeId
- cost                    (preço de compra × netWeight)
- recordedAt
- samplesUsed, standardDeviation   (rastreabilidade da estabilização)
```

```text
GrainStock                          [LOG-010]
- branchId
- grainTypeId
- availableQuantityKg               (base para a margem — LOG-013)
```

```text
raw_readings                        [LOG-006, revisado]
- scaleId, timestamp, weightKg, deviceId, plate
```

## 4. Core algorithm (resumo)

1. Remove outlier por **mediana + MAD** (`threshold = max(3×1.4826×MAD, tolerância mínima)`).
2. Sobre as amostras limpas, exige `stdDev ≤ MAX_STD_DEV`, `range ≤ MAX_RANGE`,
   `|slope| ≤ MAX_SLOPE` — o slope é o que pega um caminhão ainda entrando na
   balança, que range/stdDev sozinhos não pegam.
3. Confirma estabilidade por um tempo mínimo (`STABILITY_DURATION`) antes de
   aceitar — máquina de estados `COLLECTING → STABILIZING → STABLE`.
4. Peso final = média (ou trimmed mean) das amostras limpas, arredondado pela
   resolução real da balança.

Kalman Filter foi avaliado e descartado como algoritmo principal do MVP: ele
suaviza sinal, mas não decide sozinho se o caminhão ainda está entrando —
isso continua exigindo o critério de slope. Fica como possível camada de
suavização futura, não substituto.

Fórmulas completas, thresholds de exemplo e cenários de teste: `LOG-007`.

## 5. Idempotência, concorrência e autenticação (resumo)

- **Idempotência** no nível de negócio, não de payload: o protocolo não dá
  `readingId`, então deduplicar por `scaleId+plate+weight` corromperia o
  próprio sinal de estabilidade. A garantia real é
  `TransportTransaction → no máximo uma Weighing`, via `UNIQUE` constraint +
  transação curta. *(LOG-008, LOG-009)*
- **Concorrência** isolada por balança (`ConcurrentHashMap` + lock por
  sessão, nunca lock global) — atividade da Balança A nunca bloqueia a
  Balança B. *(LOG-005)*
- **Autenticação** por API key estática por balança, enviada em header HTTP e
  validada antes do hot path. mTLS/HMAC ficam como evolução se houver
  evidência real de fraude. *(LOG-014)*

## 6. Relatórios propostos (fechado — ver LOG-015)

Implementados nesta fase (MUST — cada um resolve diretamente um requisito
explícito do enunciado):

| Relatório | Por que entra | Endpoint |
|---|---|---|
| Livro de Pesagens | fonte operacional/auditável de cada carga | `GET /api/reports/weighings` |
| Volume e Custo por Grão | responde "calcular custos" | `GET /api/reports/cost-by-grain` |
| Estoque e Oportunidade de Margem | responde "identificar oportunidades de lucro" | `GET /api/reports/inventory-opportunities` |
| Desempenho por Filial | enunciado é explícito em múltiplas filiais | `GET /api/reports/branches/performance` |

Contrato uniforme: `{ period: {from, to}, filters: {...}, data: [...] }`.
Endpoint de detalhe (Livro de Pesagens) exige paginação; agregados não.
`plate` não é exposta em nenhum relatório agregado nem no dashboard — só no
Livro de Pesagens, para papel autorizado (LGPD, princípio da necessidade).

Adiado para depois dos 4 MUST + testes do core: Saúde das Balanças,
Transações Pendentes. Documentado mas não implementado nesta fase (depende
de `raw_readings`): Qualidade da Estabilização, Auditoria Técnica.

Detalhes completos, SQL de referência, KPIs e justificativa por relatório:
`LOG-015`.

## 7. Roadmap de evolução

| Componente | Adicionar quando |
|---|---|
| Fila durável + workers | backpressure real, burst > capacidade, perda inaceitável de readings, ou API e processamento precisarem escalar independentemente *(LOG-002)* |
| Redis / state compartilhado | múltiplas instâncias, HA vira requisito *(LOG-005)* |
| HMAC assinado / mTLS | evidência real de fraude/tampering nas balanças *(LOG-014)* |
| Rotação de API key automática | número de balanças/filiais tornar rotação manual inviável *(LOG-014)* |
| Ledger de estoque | vendas, ajustes, histórico completo, auditoria de estoque *(LOG-010)* |
| Curva de margem não linear | dado real de mercado justificar mudar da interpolação linear *(LOG-013)* |
| Retenção de raw_readings | volume virar custo de armazenamento relevante *(LOG-006)* |
| SSE/WebSocket | operador precisar de push em tempo real — hoje não há esse ator definido *(LOG-003)* |

## 8. Uso de IA (resumo)

IA foi usada como peer técnico de design review: contestar hipóteses,
levantar failure modes, comparar alternativas, e revisar escopo — nunca como
autoridade que decide arquitetura. Toda decisão final, incluindo as vezes em
que rejeitei a sugestão da IA (ex: Kalman Filter como algoritmo principal,
mensageria no MVP), está justificada tecnicamente no `LOG_DECISOES_TECNICAS.md`.
Prompts reais e classificação completa (ACCEPTED/MODIFIED/DEFERRED/REJECTED)
de cada sugestão: `USO_DE_IA.md`.

## 9. Checklist de conformidade com o desafio

| # | Requisito | Onde | Status |
|---|---|---|---|
| 1 | Cadastros | LOG-001 | ✅ |
| 2 | Recepção HTTP, balanças concorrentes | LOG-002, 004, 005 | ✅ |
| 3 | Estabilização + persistência (8 campos) | LOG-005, 006(rev), 007(rev), 009, 016 | ✅ implementado e testado (PRs #16, #17, #19) |
| 4 | Relatórios/Estatísticas | LOG-015, seção 6 | ✅ 4 MUST implementados e testados (PR #21); SHOULD/COULD documentados como roadmap |
| 5a | Arquitetura/desenho | LOG-002, 004, 011 + este blueprint | ✅ |
| 5b | Autenticação das balanças | LOG-014 | ✅ implementado e testado (PR #18) |
| 5c | Retentativa e idempotência | LOG-008, 016 | ✅ implementado e testado (PRs #17, #19) |
| 5d | Sugestão de expansão | seção 7 | ✅ |
| 6 | Uso de IA + prompt + código gerado | USO_DE_IA.md, AI-008, CODE-AI-001 a 007 | ✅ |
