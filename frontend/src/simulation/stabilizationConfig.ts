export interface StabilizationConfig {
  minSamples: number
  minValidRatio: number
  maxStdDevKg: number
  maxRangeKg: number
  maxSlopeKgPerSec: number
  stabilityDurationMs: number
  scaleResolutionKg: number
  emptyThresholdKg: number
  emptyDurationMs: number
  outlierToleranceKg: number
  /** Piso de ScaleSession.MAX_WINDOW_SAMPLES — nunca abaixo de minSamples lá. */
  maxWindowSamples: number
}

// Cópia manual de application.yml (grainweighing.stabilization). Não existe
// endpoint de config no backend, e criar um só para isto violaria a decisão
// de zero alteração na API — se os valores do application.yml mudarem, este
// arquivo precisa ser atualizado à mão (risco aceito, documentado no README).
export const STABILIZATION_CONFIG: StabilizationConfig = {
  minSamples: 20,
  minValidRatio: 0.8,
  maxStdDevKg: 30,
  maxRangeKg: 100,
  maxSlopeKgPerSec: 10,
  stabilityDurationMs: 3000,
  scaleResolutionKg: 20,
  emptyThresholdKg: 200,
  emptyDurationMs: 1000,
  outlierToleranceKg: 20,
  maxWindowSamples: 40,
}
