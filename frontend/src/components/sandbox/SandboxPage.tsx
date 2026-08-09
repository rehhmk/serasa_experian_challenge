import './sandbox.css'
import { ConfigPanel } from './ConfigPanel'
import { ControlBar } from './ControlBar'
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
        <h1>Sandbox — Stress-test da API de pesagem</h1>
        <p>
          Simula caminhões passando por filas de balanças reais, disparando <code>POST /api/readings</code> de
          verdade — nenhum endpoint novo, só a UI como consumidora.
        </p>
        <ScaleProvisioningBanner />
        <ControlBar />
        <ConfigPanel />
        <LaneGrid />
        <QueuePanel />
      </div>
    </YardActorContext.Provider>
  )
}
