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
HTTP POST /api/readings  { id, plate, weight }
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
            rejeita se grossWeight <= tareWeight            [LOG-019]
            cria Weighing
            calcula net weight e custo
            atualiza GrainStock (UPDATE atômico)             [LOG-019]
            completa TransportTransaction
            UNIQUE(transport_transaction_id)               [LOG-008]
        ↓
        PostgreSQL
        ↓
    Reset da ScaleSession (peso cai a zero, ou troca      [LOG-016]
    de placa mid-window → descarta janela e reinicia)

(fora do hot path, bufferizado com flush síncrono por lote — não é uma fila
assíncrona; ver "Limitações conhecidas", seção 10)
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
- availableQuantityKg               (base para a margem — LOG-013; incremento via
                                      UPDATE atômico no banco, não read-modify-write — LOG-019)
```

```text
raw_readings                        [LOG-006, revisado]
- scaleId, timestamp, weightKg, plate
```

## 4. Core algorithm (resumo)

1. Remove outlier por **mediana + MAD** (`threshold = max(3×1.4826×MAD, tolerância mínima)`).
2. Sobre as amostras limpas, exige `stdDev ≤ MAX_STD_DEV`, `range ≤ MAX_RANGE`,
   `|slope| ≤ MAX_SLOPE` — o slope é o que pega um caminhão ainda entrando na
   balança, que range/stdDev sozinhos não pegam.
3. Confirma estabilidade por um tempo mínimo (`STABILITY_DURATION`) antes de
   aceitar — máquina de estados `COLLECTING → STABILIZING → STABLE`.
4. Peso final = média das amostras limpas, arredondado pela resolução real da
   balança.

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

Contrato uniforme: `{ period: {from, to}, filters: {...}, data: [...] }`, com
duas exceções documentadas:

- `inventory-opportunities` não recebe filtro de período (estoque é sempre a
  posição atual, não um agregado por intervalo) — `period` vem `null` na
  resposta, não um `{from, to}` vazio. É snapshot por definição, não um bug.
- Livro de Pesagens (`/weighings`) é paginado (`page`, `size`), mas a resposta
  não inclui contagem total nem `hasNext` — só a página pedida. Suficiente
  para o MVP (consumo manual/CLI), insuficiente para um componente de UI de
  paginação real; ver seção "Limitações conhecidas" abaixo.

Endpoint de detalhe (Livro de Pesagens) exige paginação; agregados não.
`plate` não é exposta em nenhum relatório agregado nem no dashboard — só no
Livro de Pesagens, para papel autorizado (LGPD, princípio da necessidade) —
**"papel autorizado" aqui é uma intenção de design, não um controle de acesso
implementado; ver seção "Limitações conhecidas" abaixo.**

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
| 3 | Estabilização + persistência (8 campos) | LOG-005, 006(rev), 007(rev), 009, 016, 019 | ✅ implementado e testado (PRs #16, #17, #19; correções LOG-019) |
| 4 | Relatórios/Estatísticas | LOG-015, seção 6 | ✅ 4 MUST implementados e testados (PR #21); SHOULD/COULD documentados como roadmap |
| 5a | Arquitetura/desenho | LOG-002, 004, 011 + este blueprint | ✅ |
| 5b | Autenticação das balanças | LOG-014 | ✅ implementado e testado (PR #18) |
| 5c-i | Idempotência (no máximo 1 Weighing por TransportTransaction) | LOG-008, 016 | ✅ implementado e testado (PRs #17, #19) |
| 5c-ii | Retentativa automática após falha | LOG-018 | ❌ não implementado — trade-off documentado do MVP, não confundir com 5c-i. Ver "Limitações conhecidas" |
| 5d | Sugestão de expansão | seção 7 | ✅ |
| 6 | Uso de IA + prompt + código gerado | USO_DE_IA.md, AI-008, CODE-AI-001 a 008 | ✅ |

## 10. Limitações conhecidas e evolução para produção

Trade-offs deliberados do MVP, para não surpreender ninguém durante a
entrevista — cada um já tem decisão registrada, nenhum é omissão silenciosa:

| Limitação | Onde documentado | Evolução (ver seção 7 e roteiro incremental) |
|---|---|---|
| Sem retry automático se a persistência falhar depois de `STABLE` | LOG-018 | Não transicionar `STABLE` como definitivo até a persistência confirmar, ou política de retry explícita no controller |
| `raw_readings` é bufferizado em memória (`ConcurrentLinkedQueue`) e flusha em lote síncrono ao atingir `flush-batch-size`; o lote residual (< batch size) em memória no momento de um crash/kill abrupto é perdido | LOG-006 (revisado) | Fila durável (ver seção 7) removeria essa janela de perda; fora de escopo sem evidência de que o volume de auditoria justifique |
| Relatórios administrativos (`/api/reports/*`) não têm autenticação nem controle de papel — qualquer request sem credencial acessa, incluindo o Livro de Pesagens com `plate` | Seção 6 (nota LGPD é intenção de design, não controle implementado) | Spring Security + papel administrativo dedicado; não adicionado no MVP para não introduzir stack nova sem aprovação (CLAUDE.md §19) |
| API key das balanças é estática por balança (LOG-014) — sem rotação automática, sem HMAC/mTLS | LOG-014 | Rotação de chave e/ou HMAC assinado quando houver evidência real de fraude/tampering (seção 7) |
| `/api/reports/weighings` pagina mas não retorna total de itens nem `hasNext` | Seção 6 | Adicionar `Page` completo (`totalElements`) se um consumidor de UI real precisar construir paginação, não apenas navegação sequencial |
| `/api/reports/inventory-opportunities` é sempre a posição atual de estoque — não aceita filtro de período, retorna `period: null` | Seção 6 | Não é limitação a resolver — estoque não é um agregado por intervalo; documentado para não ser lido como bug |
| O sandbox (`frontend/`) prevê estabilização **localmente**, no navegador, com os mesmos thresholds do `StabilizationEngine` — essa previsão nunca é a confirmação de que o backend persistiu a `Weighing`; a UI consulta o Livro de Pesagens (`GET /api/reports/weighings`) para confirmar | `frontend/README.md`, `frontend/src/simulation/stabilizationPredictor.ts` | N/A — distinção deliberada, mantida visualmente explícita na UI |
