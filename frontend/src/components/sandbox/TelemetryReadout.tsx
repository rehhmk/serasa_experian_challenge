interface TelemetryReadoutProps {
  weightKg: number
  standardDeviation: number
  samplesUsed: number
}

// Puramente apresentacional — recebe números já calculados (pelo predictor,
// via TruckToken) e só formata. Reutilizável fora do contexto de raia/caminhão
// se algum dia precisar (ex: página de relatórios).
export function TelemetryReadout({ weightKg, standardDeviation, samplesUsed }: TelemetryReadoutProps) {
  return (
    <dl className="telemetry-readout">
      <div>
        <dt>Peso</dt>
        <dd>{weightKg.toFixed(1)} kg</dd>
      </div>
      <div>
        <dt>Amostras</dt>
        <dd>{samplesUsed}</dd>
      </div>
      <div>
        <dt>σ</dt>
        <dd>{standardDeviation.toFixed(2)} kg</dd>
      </div>
    </dl>
  )
}
