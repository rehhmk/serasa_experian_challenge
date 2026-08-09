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

Log cronológico de cada decisão técnica relevante (`LOG-001` a `LOG-016`),
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
implementação), com prompts reais utilizados (seção 3, `AI-001` a `AI-008`)
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

Ver checklist completo na seção 9 do `BLUEPRINT.md`. Em aberto no momento:

- 🔶 Registro de código gerado por IA (`USO_DE_IA.md`, seção 7) — hoje é um
  exemplo de template, ainda não os registros reais dos arquivos
  efetivamente implementados.

Fechado nesta rodada: relatórios administrativos (`LOG-015`) — 4 relatórios
MUST definidos e prontos para implementar (Livro de Pesagens, Volume/Custo
por Grão, Estoque/Oportunidade de Margem, Desempenho por Filial); SHOULD/
COULD documentados como roadmap, não bloqueiam a entrega.

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

**Importante:** cadastros (`/api/branches`, `/api/trucks`, `/api/grain-types`,
`/api/scales`, `/api/transport-transactions`) e o cálculo de margem já
funcionam de verdade. `/api/readings` e `/api/reports/*` retornam `500` —
são TODOs documentados (`StabilizationEngine`, `ScaleAuthFilter`,
`CompleteWeighingUseCase`, as 4 query services de relatório), não bugs do
deploy. Ver checklist de conformidade (seção 9) no `BLUEPRINT.md`.
