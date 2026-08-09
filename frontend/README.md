# Grain Weighing — Sandbox

Sandbox de desenvolvedor em React + TypeScript para estressar visualmente a
API de pesagem do backend (`../pom.xml`): filas de balanças, caminhões
simulados fluindo automaticamente (ou sob controle manual) e perfis de
leitura que exercitam edge cases documentados do algoritmo de
estabilização — direto contra a API real, sem nenhum mock de servidor.

Peça **adicional** ao desafio técnico — o core avaliado é o backend
(`../BLUEPRINT.md`, `../LOG_DECISOES_TECNICAS.md`). Escopo desta leva de
trabalho: só a página Sandbox. Dashboard, livro de pesagens, relatórios e
cadastros do mockup original ficam fora, como possível evolução futura.

## Rodando

```bash
npm install
npm run dev      # http://localhost:5173, proxy /api -> backend real (ver .env.example)
npm run test     # Vitest
npm run build    # tsc --noEmit + vite build
npm run lint     # oxlint
```

Precisa do backend rodando (`docker-compose up -d postgres && mvn
spring-boot:run -Dspring-boot.run.profiles=dev` na raiz do repo) — o dev
server do Vite só faz proxy de `/api/*` (`vite.config.ts`), não substitui o
backend. Se o backend estiver numa porta diferente de `8080`, copie
`.env.example` para `.env.local` e ajuste `VITE_API_PROXY_TARGET`.

Zero alteração no código Java em toda esta implementação — a UI é só mais
um consumidor da API já existente, autenticando cada balança com a
`X-Scale-Key` real obtida na hora da criação (`POST /api/scales`).

## Deploy

**No ar:** https://grainweighing-frontend.onrender.com — Render Static
Site, build direto deste diretório (`buildCommand: cd frontend && npm ci &&
npm run build`, `staticPublishPath: frontend/dist`, ver `../render.yaml`).

Em produção não existe dev server do Vite pra fazer proxy — quem cumpre
esse papel é `public/_redirects` (formato Render/Netlify, copiado pro
`dist/` no build): reescreve `/api/*` pro backend real
(`https://grainweighing.onrender.com`) antes do site estático ser servido,
com status `200` (rewrite transparente, não redirect visível no browser).
Site estático servido via CDN — sem cold start, ao contrário do backend
(free tier, dorme após ~15min de inatividade).

## Arquitetura

```text
src/
  api/          client HTTP tipado (1 arquivo por família de endpoint) +
                bootstrap.ts (orquestra a criação inicial de filial/tipo de
                grão/balanças/caminhões via API real, get-then-create)
  simulation/   lógica pura, sem React/XState: mirror do algoritmo de
                estabilização (stabilizationPredictor.ts) + os perfis de
                leitura (readingProfiles/)
  machines/     truckMachine (ciclo de vida de 1 caminhão numa balança) e
                yardMachine (pátio: fila + raias + despacho), XState v5
  components/sandbox/  UI — componentes genéricos de responsabilidade única
```

`truckMachine` e `yardMachine` nunca chamam `fetch` direto — sempre através
de `api/*`, os mesmos módulos testados isoladamente. `simulation/` não
importa nada de `machines/`/`components/` (zero dependência de framework),
então os perfis e o predictor são testáveis sem precisar de ator nenhum.

## Perfis de caminhão

Cada perfil (exceto o preset de concorrência) existe pra provar, contra o
algoritmo **real** do backend, um comportamento documentado — não pra
"parecer" certo:

| Perfil | O que testa | Como |
|---|---|---|
| **Normal** | Fluxo feliz | Rampa de entrada + platô com ruído pequeno |
| **Caminhão ruidoso** (`noisy`) | Mediana+MAD (LOG-007) | Platô com pico isolado de +800kg (mesmo valor do teste `singleLargeOutlierIsRemovedByMedianMad` do backend) — outlier filtrado, ainda estabiliza |
| **Entrada lenta** (`slowEntry`) | Guarda de slope (LOG-007) | Rampa mais longa que o tempo de encher a janela (`maxWindowSamples=40` × ~100ms ≈ 4s) — slope real sustentado > `maxSlopeKgPerSec`, não estabiliza enquanto sobe |
| **Retry duplicado** (`duplicateRetry`) | Idempotência (LOG-008) | Completa 1 passagem, espera a sessão da balança "esvaziar" de verdade (`emptyThresholdKg`/`emptyDurationMs` reais), só então abre uma 2ª transação — nunca em paralelo com a 1ª |
| **Testar concorrência (2 balanças)** | Isolamento por balança (LOG-005) | Não é um perfil — despacha 2 caminhões `normal` pra 2 balanças diferentes ao mesmo tempo. Isolamento não depende do formato das leituras, então inventar uma variação estatística fake seria menos honesto que isso |

## Limitações conhecidas

1. **Thresholds de estabilização no frontend são cópia manual** de
   `application.yml` (`simulation/stabilizationConfig.ts`). Não existe
   endpoint de config no backend, e criar um só pra isso violaria a decisão
   de zero alteração na API — se os valores do `application.yml` mudarem,
   este arquivo precisa ser atualizado à mão.
2. **`GET /api/reports/weighings` não filtra por `transportTransactionId`**
   — só por `scaleId`/`plate`/período. A confirmação de cada passagem
   (inclusive no `duplicateRetry`) é por janela de tempo, não por id de
   transação.
3. **Chaves de API de balanças provisionadas pelo sandbox só existem em
   memória** (`scaleKeyStore.ts`), nunca `localStorage`. Um refresh da
   página reprovisiona balanças novas do zero — comportamento aceito, não
   um bug.
4. **`POST /api/readings` é fire-and-forget** (sempre `202`, sem corpo) —
   a UI nunca sabe pela resposta se a estabilização avançou; ela prevê
   localmente (`stabilizationPredictor.ts`) e confirma depois via
   `GET /api/reports/weighings`.
5. **Não testado visualmente num browser real** durante o desenvolvimento
   (sem ferramenta de browser/screenshot disponível no ambiente) — validado
   via suíte automatizada (testes de máquina de estados com fake timers e
   API mockada, mais um smoke test que renderiza a árvore inteira via
   jsdom) e verificação manual ponta a ponta contra o backend real rodando
   localmente (balança/caminhão/transação criados via API, leituras
   simulando cada perfil, estabilização real confirmada via
   `GET /api/reports/weighings`).
