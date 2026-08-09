import { useSelector } from '@xstate/react'
import type { ActorRefFrom, SnapshotFrom } from 'xstate'
import type { truckMachine } from '../../machines/truckMachine'
import { StabilityBadge, type StabilityBadgeTone } from './StabilityBadge'
import { TelemetryReadout } from './TelemetryReadout'

interface TruckTokenProps {
  actorRef: ActorRefFrom<typeof truckMachine>
}

// Responsabilidade única: dado o ator de UM caminhão, mostrar placa/perfil/
// badge de estado + telemetria. Não sabe nada de raia/pátio — Lane é quem
// decide qual ator passar aqui.
export function TruckToken({ actorRef }: TruckTokenProps) {
  const snapshot = useSelector(actorRef, (s) => s)
  const { plate, profile, predictor, confirmedWeighing } = snapshot.context
  const { label, tone } = describeTruckState(snapshot)

  return (
    <div className="truck-token">
      <div className="truck-token-header">
        <span className="truck-plate">{plate}</span>
        <span className="truck-profile">{profile}</span>
        <StabilityBadge label={label} tone={tone} />
      </div>
      <TelemetryReadout
        weightKg={confirmedWeighing?.netWeightKg ?? predictor.weightKg}
        standardDeviation={predictor.standardDeviation}
        samplesUsed={predictor.samplesUsed}
      />
    </div>
  )
}

function describeTruckState(snapshot: SnapshotFrom<typeof truckMachine>): { label: string; tone: StabilityBadgeTone } {
  if (snapshot.matches({ onScale: 'collecting' })) return { label: 'Coletando', tone: 'neutral' }
  if (snapshot.matches({ onScale: 'stabilizing' })) return { label: 'Estabilizando', tone: 'progress' }
  if (snapshot.matches('confirming') || snapshot.matches('confirmRetryWait')) {
    return { label: 'Confirmando', tone: 'progress' }
  }
  if (snapshot.matches('recorded')) return { label: 'Estável', tone: 'success' }
  if (snapshot.matches('unconfirmed')) return { label: 'Não confirmado', tone: 'warning' }
  if (snapshot.matches('transactionError')) return { label: 'Erro', tone: 'danger' }
  if (snapshot.matches('travelling') || snapshot.matches('openingTransaction')) {
    return { label: 'A caminho', tone: 'neutral' }
  }
  if (snapshot.matches('leaving')) return { label: 'Saindo', tone: 'neutral' }
  return { label: 'Na fila', tone: 'neutral' }
}
