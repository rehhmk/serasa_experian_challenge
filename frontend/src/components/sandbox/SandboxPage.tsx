import './sandbox.css'
import { ConfigPanel } from './ConfigPanel'
import { ControlBar } from './ControlBar'
import { Inspector } from './Inspector'
import { LaneGrid } from './LaneGrid'
import { QueuePanel } from './QueuePanel'
import { ScaleProvisioningBanner } from './ScaleProvisioningBanner'
import { YardActorContext } from './yardActorContext'

// Responsabilidade única: prover o ator do pátio e compor as telas — nenhuma
// lógica própria, só composição.
export function SandboxPage() {
  return (
    <YardActorContext.Provider>
      <div className="sandbox-page">
        <header className="sandbox-header">
          <h1>Sandbox — walkthrough técnico da API de pesagem</h1>
          <p>
            Esta página consome a API real (<code>POST /api/readings</code>, <code>GET /api/reports/weighings</code>)
            para estressar balanças/filas simuladas — <strong>não faz parte do core avaliado do desafio</strong>, é
            uma ferramenta de apoio para explicar o pipeline de estabilização ao vivo.
          </p>
        </header>

        <ScaleProvisioningBanner />

        <section className="sandbox-section" aria-label="Controles e cenários">
          <h2>Controles e cenários</h2>
          <p className="sandbox-section-hint">
            Perfis por caminhão cobrem normal, outlier ruidoso, entrada lenta e 2ª passagem (idempotência); o botão
            de concorrência despacha 2 balanças ao mesmo tempo.
          </p>
          <ControlBar />
          <ConfigPanel />
        </section>

        <section className="sandbox-section" aria-label="Rodovia de balanças">
          <h2>Balanças</h2>
          <LaneGrid />
        </section>

        <Inspector />

        <QueuePanel />
      </div>
    </YardActorContext.Provider>
  )
}
