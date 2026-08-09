import { useSelector } from '@xstate/react'
import type { ActorRefFrom } from 'xstate'
import type { truckMachine } from '../../machines/truckMachine'
import { StabilityBadge } from './StabilityBadge'
import { TelemetryReadout } from './TelemetryReadout'
import { describeTruckDisplay } from './truckDisplay'

interface TruckTokenProps {
  actorRef: ActorRefFrom<typeof truckMachine>
}

// Responsabilidade única: dado o ator de UM caminhão, renderiza a "estrada"
// animada (posição derivada do estado real da máquina, via
// describeTruckDisplay) + placa/peso/telemetria. Não sabe nada de raia/pátio
// — Lane é quem decide qual ator passar aqui.
export function TruckToken({ actorRef }: TruckTokenProps) {
  const snapshot = useSelector(actorRef, (s) => s)
  const { plate, profile, predictor, confirmedWeighing } = snapshot.context
  const { label, tone, roadPercent } = describeTruckDisplay(snapshot)
  const weightTons = (confirmedWeighing?.netWeightKg ?? predictor.weightKg) / 1000

  return (
    <div className="truck-token">
      <div className="truck-token-header">
        <span className="truck-profile">{profile}</span>
        <StabilityBadge label={label} tone={tone} />
      </div>

      <div className="lane-road" aria-hidden="true">
        <div className="lane-road-track" />
        <span className="lane-road-truck" style={{ left: `${roadPercent}%` }}>
          🚚
        </span>
      </div>

      <div className="truck-token-footer">
        <span className="truck-plate">{plate}</span>
        <span className="truck-weight">{weightTons.toFixed(2)} t</span>
      </div>

      <TelemetryReadout
        samplesUsed={predictor.samplesUsed}
        standardDeviationG={predictor.standardDeviation * 1000}
        slopeKgPerSec={predictor.slope}
      />
    </div>
  )
}
