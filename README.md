# Grain Weighing Platform — Desafio Técnico Backend

[![CI](https://github.com/rehhmk/serasa_experian_challenge/actions/workflows/ci.yml/badge.svg)](https://github.com/rehhmk/serasa_experian_challenge/actions/workflows/ci.yml)

Solução para o desafio técnico de backend: ingestão, estabilização e
armazenamento de leituras de peso de balanças rodoviárias (ESP32,
fire-and-forget) para uma empresa de transporte de grãos.

O scaffold completo (Maven, migrations, cadastros, pacote de ingestão, etc.)
já está em `main`, dividido em PRs pequenas seguindo a convenção descrita em
`CLAUDE.md` §30. Os três arquivos abaixo registram a arquitetura, as
decisões técnicas e o uso de IA que sustentam esse código.

## Como ler estes documentos

Existem três documentos, cada um respondendo a uma pergunta diferente. A
ordem de leitura recomendada é:

### 1. [`BLUEPRINT.md`](BLUEPRINT.md) — comece por aqui

> **O quê?** A solução final, resumida.

Documento de entrada: contexto do problema, diagrama da arquitetura final,
modelo de dados, resumo do algoritmo de estabilização, e um checklist de
conformidade com cada requisito do desafio (seção 9). Se você só puder ler
um arquivo, leia este — ele linka para os detalhes nos outros dois sempre
que preciso.

### 2. [`LOG_DECISOES_TECNICAS.md`](LOG_DECISOES_TECNICAS.md) — o porquê de cada escolha

> **Por que decidi assim?**

Log cronológico de cada decisão técnica relevante (`LOG-001` a `LOG-020`),
incluindo hipótese inicial, alternativas comparadas, revisões que fiz ao
longo do processo, e o critério usado para decidir. Inclui os pontos em que
rejeitei uma sugestão de IA (ex: Kalman Filter como algoritmo principal de
estabilização, mensageria no MVP) com a justificativa técnica.

Use este documento quando quiser entender **a razão** por trás de algo que
viu resumido no `BLUEPRINT.md` — cada seção do blueprint referencia o
`LOG-XXX` correspondente.

### 3. [`USO_DE_IA.md`](USO_DE_IA.md) — como usei IA (requisito obrigatório do desafio)

> **Como a IA participou?**

Descreve o papel da IA no processo (design review, adversarial review,
exploração de alternativas, revisão de escopo, assistência de
implementação), com prompts reais utilizados (seção 3, `AI-001` a `AI-009`)
e a classificação de cada sugestão recebida (`ACCEPTED` / `MODIFIED` /
`DEFERRED` / `REJECTED`, seção 5). A seção 6-7 define o template para
registrar código gerado por IA à medida que a implementação avançar.

## Relação entre os três documentos

```text
BLUEPRINT.md              → o quê foi decidido (resumo + arquitetura final)
LOG_DECISOES_TECNICAS.md  → por que foi decidido assim (hipótese → trade-off → decisão)
USO_DE_IA.md               → como a IA foi usada em cada etapa desse processo
```

O `BLUEPRINT.md` é a porta de entrada e aponta para `LOG-XXX` (decisão) e
`AI-XXX` (uso de IA) sempre que uma afirmação precisa de mais contexto.

## Status atual

Ver checklist completo na seção 9 do `BLUEPRINT.md`. Lógica core implementada
e testada de ponta a ponta (PRs #16–#21): `StabilizationEngine`, máquina de
estados de `ScaleSession`, `ScaleAuthFilter`, `CompleteWeighingUseCase` e os
4 relatórios administrativos MUST (Livro de Pesagens, Volume/Custo por Grão,
Estoque/Oportunidade de Margem, Desempenho por Filial). Fluxo completo
(leitura → estabilização → pesagem → relatório) validado via teste de
integração com Postgres real (Testcontainers).

Sem itens em aberto no checklist MUST. Registro de código gerado por IA
(`USO_DE_IA.md`, seção 7, `CODE-AI-001` a `009`) e ambiente de deploy externo
(seção "Deploy" abaixo) — ambos fechados. SHOULD/COULD continuam documentados
como roadmap (seção 7 do `BLUEPRINT.md`), não como pendência.

Revisão final pré-entrevista (`LOG-019`) corrigiu dois riscos de consistência
reais — lost update no incremento de `GrainStock` (agora `UPDATE` atômico) e
ausência de guarda contra `grossWeight <= tareWeight` na finalização — e
alinhou a documentação ao comportamento real do código (endpoint, algoritmo
de peso final, contrato dos relatórios). Uma segunda rodada (`LOG-020`)
corrigiu a causa raiz de pesagens do sandbox aparecendo como "Não
confirmado": nada impedia um caminhão de acumular mais de uma
`TransportTransaction` OPEN (reuso de caminhões `SB...` entre sessões do
sandbox deixando uma órfã), o que fazia `CompleteWeighingUseCase` recusar a
finalização por ambiguidade — agora impedido por um índice único parcial no
banco (`409 Conflict`, não uma constraint ingênua que quebraria o histórico
de transactions `COMPLETED`), com o sandbox se recuperando sozinho de uma
transaction órfã do próprio reuso de caminhões. Limitações conhecidas do MVP
(retentativa automática, buffer de `raw_readings`, autenticação dos
relatórios administrativos) estão listadas na seção 10 do `BLUEPRINT.md`.

## Stack

Java + Spring Boot, PostgreSQL. Detalhes de modelagem e endpoints em
`BLUEPRINT.md`, seções 2 e 3.

## Rodando o projeto

Dois jeitos de rodar, dependendo do que você tem instalado.

### Loop rápido de desenvolvimento (JDK 17 + Maven no host)

Só o Postgres roda em container; a aplicação roda direto no host — melhor
para iterar rápido (hot reload, debugger, etc.).

```bash
docker-compose up -d postgres
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Stack completo containerizado (só precisa de Docker)

Aplicação e banco rodam ambos em container — útil para validar o build de
produção ou rodar sem JDK/Maven instalados localmente.

```bash
docker-compose up --build
```

Em ambos os casos, a API sobe em `http://localhost:8080` com dados de seed
(`V9__seed_dev_data.sql`) já carregados — inclui uma balança de teste
(`scale-01`) com a chave `dev-scale-01-key` para simular o ESP32:

```bash
curl -X POST http://localhost:8080/api/readings \
  -H "Content-Type: application/json" \
  -H "X-Scale-Key: dev-scale-01-key" \
  -d '{"id":"scale-01","plate":"ABC1D23","weight":32010}'
```

Todo o fluxo funciona de verdade: cadastros, `/api/readings` (a balança
precisa continuar mandando leituras — em produção a cada ~100ms — até
`stabilityDurationMs` ser atingido, cerca de 3s por padrão, para a pesagem
ser finalizada) e `/api/reports/*`. Ver checklist de conformidade (seção 9)
no `BLUEPRINT.md`.

## Deploy (dev environment)

**No ar:** https://grainweighing.onrender.com — deploy real, feito via
`render` CLI (Postgres + web service provisionados a partir do
`render.yaml`), com o seed de dev carregado (`scale-01` /
`dev-scale-01-key`, mesmos dados do exemplo abaixo). Fluxo completo
(cadastro → readings até `STABLE` → pesagem → relatório) validado
manualmente contra essa instância, incluindo os 4 relatórios.

Alvo escolhido: [Render](https://render.com) — free tier sem cartão, sobe
direto do `Dockerfile` existente. `render.yaml` na raiz é um
[Blueprint](https://render.com/docs/blueprint-spec) que provisiona o app e
um Postgres juntos, com as variáveis de ambiente já linkadas entre os dois
(também dá pra provisionar via dashboard: **New** → **Blueprint** →
conectar o repositório).

**Limitações do free tier, para não surpreender durante a entrevista:**

- Web service dorme após ~15 min de inatividade — primeira request depois
  disso leva ~30-50s (cold start) para acordar o container.
- O Postgres free expira em 30 dias (9 de setembro de 2026) — suficiente
  para a janela da entrevista, não para manter o ambiente de pé
  indefinidamente.

## Sandbox de desenvolvedor (adicional, fora do core avaliado)

**No ar:** https://grainweighing-frontend.onrender.com — Render Static Site,
build direto do `frontend/` deste repositório (`render.yaml`). `/api/*` é
reescrito (proxy, status 200) pro backend real
(`https://grainweighing.onrender.com`) antes de servir o site — mesmo papel
do proxy do Vite dev server em desenvolvimento, evita CORS sem nenhuma
alteração no Spring Boot. Validado manualmente ponta a ponta contra a
instância real: GET, POST com corpo JSON, header `X-Scale-Key` passando
íntegro pelo proxy, e autenticação de verdade (chave errada → 401, não um
bypass). Detalhes de como a regra de rewrite foi criada:
[`frontend/README.md`](frontend/README.md#deploy).

[`frontend/`](frontend/) é uma página React + TypeScript + XState **fora do
escopo do desafio em si** — um sandbox visual pra estressar a API real:
filas de balanças, caminhões simulados fluindo automaticamente e perfis de
leitura que exercitam os edge cases documentados do algoritmo de
estabilização (mediana+MAD, guarda de slope, idempotência, isolamento por
balança). Detalhes, como rodar e limitações conhecidas:
[`frontend/README.md`](frontend/README.md).

## Saneamento manual de transações OPEN duplicadas (ambiente publicado)

Sessões do sandbox anteriores ao `LOG-020` (índice único parcial —
`transport_transactions`, uma OPEN por caminhão) deixaram caminhões `SB...`
com mais de uma `TransportTransaction` OPEN no Postgres publicado. Isso
**não é mais possível a partir do `LOG-020`** (o backend rejeita a segunda
tentativa com `409`), mas dados criados **antes** da migration não são
apagados nem corrigidos automaticamente.

**Checado (leitura, nenhum cancelamento) em 2026-08-10:** os 18 caminhões
`SB...` existentes no ambiente publicado tinham **188 `TransportTransaction`
OPEN cada um** — ~3.400 linhas no total, acumuladas ao longo de várias
sessões de teste, não um punhado de órfãs. Em escala assim, revisar
individualmente cada um dos ~3.400 IDs não é prático nem é o que traria mais
segurança — o risco real não é "qual das 188 é a válida", é confirmar que
**nenhuma** das 188 ainda representa uma passagem em andamento antes de
cancelar em lote.

Este procedimento **nunca deve rodar sem confirmação explícita de quem está
operando** — só usa o endpoint oficial de cancelamento (o mesmo que o
sandbox já usa para se autorrecuperar, `LOG-020`), nenhum acesso direto ao
banco.

### 1. Confirmar que nenhuma passagem está em andamento

Antes de cancelar qualquer coisa, feche todas as abas do sandbox
(`https://grainweighing-frontend.onrender.com`) ativas — cancelar a
transaction OPEN de um caminhão que uma aba ainda está usando ativamente
quebraria aquela passagem no meio.

### 2. Identificar caminhões contaminados e o tamanho real do problema

```bash
BASE="https://grainweighing.onrender.com"
for id in $(curl -s "$BASE/api/trucks" | python3 -c "
import json, sys
for t in json.load(sys.stdin):
    if t['plate'].startswith('SB'):
        print(t['id'])
"); do
  n=$(curl -s "$BASE/api/transport-transactions?truckId=$id&status=OPEN" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))")
  [ "$n" -gt 0 ] 2>/dev/null && echo "$id: $n OPEN"
done
```

Roda só leitura (`GET`) — seguro rodar quantas vezes quiser antes de decidir
qualquer coisa. Revise a lista completa de IDs e contagens **antes** de ir
para o próximo passo.

### 3. Cancelar em lote, pelo endpoint oficial — só depois de revisar o passo 2

```bash
BASE="https://grainweighing.onrender.com"
TRUCK_ID="<um id da lista revisada no passo 2>"
curl -s "$BASE/api/transport-transactions?truckId=$TRUCK_ID&status=OPEN" | python3 -c "
import json, sys
for t in json.load(sys.stdin):
    print(t['id'])
" | while read -r tx_id; do
  curl -s -X POST "$BASE/api/transport-transactions/$tx_id/cancel" -o /dev/null -w "%{http_code} $tx_id\n"
done
```

Repita truck por truck (não um loop sobre todos os `SB...` de uma vez) —
cada rodada deve ser algo que quem está operando viu e decidiu rodar,
não uma varredura automática silenciosa. Cancelar uma transaction que já não
está OPEN retorna 422 sem efeito colateral — seguro rodar de novo se o
estado mudou no meio do caminho.

### 4. Confirmação explícita antes de rodar

- Passo 1 e 2 (fechar abas, listar) sempre antes de qualquer `cancel`.
- Rodar o passo 3 exige confirmação explícita de quem está operando, truck
  por truck — nunca automatizado num script que varre todos os IDs sem
  revisão humana no meio.
- Se depois do passo 1 alguma dessas transactions ainda parecer
  legitimamente em andamento (checar `GET /api/reports/weighings?plate=...`
  — se já existe uma `Weighing` recente, a OPEN remanescente quase certamente
  está órfã), pare e investigue antes de cancelar aquele caminhão específico.

Depois de saneado, o `LOG-020` garante que o problema não volta a se
acumular — este procedimento é para dados que já existiam antes da correção,
não uma rotina recorrente. Eu (IA) identifiquei a contaminação real e escrevi
este procedimento, mas **não cancelei nada** — nenhum comando de escrita
contra `/cancel` foi executado nesta sessão.
