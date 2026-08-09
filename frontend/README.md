# Grain Weighing — Sandbox

Sandbox de desenvolvedor em React + TypeScript para estressar visualmente a
API de pesagem do backend (`../pom.xml`): filas de balanças, caminhões
simulados fluindo automaticamente e perfis de leitura que exercitam edge
cases documentados do algoritmo de estabilização.

Peça **adicional** ao desafio técnico — o core avaliado é o backend. Escopo,
decisões e limitações conhecidas completas: ver o plano de implementação (nas
próximas PRs este README ganha uma seção própria "Como rodar" +
"Limitações conhecidas").

## Rodando

```bash
npm install
npm run dev      # http://localhost:5173, proxy /api → backend real (ver .env.example)
npm run test     # Vitest
npm run build    # tsc --noEmit + vite build
npm run lint     # oxlint
```

Precisa do backend rodando (`docker-compose up -d postgres && mvn spring-boot:run -Dspring-boot.run.profiles=dev` na raiz do repo) — o dev server do Vite só faz proxy de `/api/*`, não substitui o backend.
