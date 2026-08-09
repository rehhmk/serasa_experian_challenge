# Grain Weighing Platform — Desafio Técnico Backend

Solução para o desafio técnico de backend: ingestão, estabilização e
armazenamento de leituras de peso de balanças rodoviárias (ESP32,
fire-and-forget) para uma empresa de transporte de grãos.

Este repositório está, neste momento, na fase de **design documentado antes
da implementação** — os três arquivos abaixo registram a arquitetura, as
decisões técnicas e o uso de IA que sustentam o código a ser escrito.

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
