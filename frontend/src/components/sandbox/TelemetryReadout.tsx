interface TelemetryReadoutProps {
  samplesUsed: number
  standardDeviationG: number
  slopeKgPerSec: number
}

// Puramente apresentacional — diagnóstico do algoritmo de estabilização
// (amostras/desvio/slope), não peso do caminhão (isso é o TruckToken quem
// mostra, ao lado da placa). Recebe números já calculados, só formata.
export function TelemetryReadout({ samplesUsed, standardDeviationG, slopeKgPerSec }: TelemetryReadoutProps) {
  return (
    <dl className="telemetry-readout">
      <div>
        <dt>amostras</dt>
        <dd>{samplesUsed}</dd>
      </div>
      <div>
        <dt>σ</dt>
        <dd>{standardDeviationG.toFixed(0)} g</dd>
      </div>
      <div>
        <dt>slope</dt>
        <dd>{slopeKgPerSec.toFixed(2)}</dd>
      </div>
    </dl>
  )
}
