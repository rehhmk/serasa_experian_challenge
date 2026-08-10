import { useSelector } from '@xstate/react'
import type { ActorRefFrom } from 'xstate'
import type { truckMachine } from '../../machines/truckMachine'
import { StabilityBadge } from './StabilityBadge'
import { TelemetryReadout } from './TelemetryReadout'
import { describeTruckDisplay, describeWeighingResult } from './truckDisplay'

interface TruckTokenProps {
  actorRef: ActorRefFrom<typeof truckMachine>
}

// Responsabilidade única: dado o ator de UM caminhão, renderiza a "estrada"
// animada (posição derivada do estado real da máquina, via
// describeTruckDisplay) + placa/peso/telemetria. Não sabe nada de raia/pátio
// — Lane é quem decide qual ator passar aqui.
export function TruckToken({ actorRef }: TruckTokenProps) {
  const snapshot = useSelector(actorRef, (s) => s)
  const { plate, profile, predictor } = snapshot.context
  const { label, tone, roadPercent } = describeTruckDisplay(snapshot)
  const result = describeWeighingResult(snapshot)
  const netWeightTons = result.netWeightKg / 1000

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
        <span className={`truck-weight truck-weight--${result.kind}`}>
          {netWeightTons.toFixed(2)} t
          <span className="truck-weight-kind">{result.kind === 'predicted' ? 'predito' : 'confirmado'}</span>
        </span>
      </div>

      <TelemetryReadout
        samplesUsed={predictor.samplesUsed}
        standardDeviationG={predictor.standardDeviation * 1000}
        slopeKgPerSec={predictor.slope}
        rangeKg={predictor.range}
      />
    </div>
  )
}
