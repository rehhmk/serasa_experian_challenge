import { STABILIZATION_CONFIG, type StabilizationConfig } from './stabilizationConfig'

export interface WeightSample {
  timestampMs: number
  weightKg: number
}

export interface PredictionResult {
  stable: boolean
  weightKg: number
  standardDeviation: number
  samplesUsed: number
  /** kg/s sobre a janela limpa — 0 quando não há amostras suficientes pra calcular. */
  slope: number
  /** max - min sobre a base usada (amostras limpas quando há; a janela crua caso contrário). */
  range: number
  /** clean.length / n — 1 quando a remoção de outlier ainda não rodou (poucas amostras). */
  validRatio: number
  /** n - clean.length — quantas amostras a mediana+MAD descartou desta janela. */
  outliersRemoved: number
}

/**
 * Porta linha-a-linha StabilizationEngine.process() (Java) — mediana+MAD
 * (outlier removal) -> range/stdDev/slope sobre amostras limpas -> peso final
 * = média arredondada pela resolução da balança. `stable` aqui significa
 * "esta janela específica satisfaz os critérios instantâneos", não
 * "confirmado por tempo suficiente" (isso é advancePredictor, abaixo).
 *
 * Mesma precondição do original: `window` em ordem cronológica de chegada
 * (necessário para o slope), não validada em runtime.
 */
export function predictWeighing(
  window: WeightSample[],
  config: StabilizationConfig = STABILIZATION_CONFIG,
): PredictionResult {
  if (window.length === 0) {
    throw new Error('window must not be empty')
  }
  const n = window.length

  if (n < config.minSamples) {
    const rawMean = mean(window)
    return {
      stable: false,
      weightKg: roundToResolution(rawMean, config.scaleResolutionKg),
      standardDeviation: populationStdDev(window, rawMean),
      samplesUsed: n,
      slope: computeSlope(window),
      range: rangeOf(window),
      validRatio: 1,
      outliersRemoved: 0,
    }
  }

  const median = medianOf(window.map((s) => s.weightKg).sort((a, b) => a - b))
  const mad = medianOf(window.map((s) => Math.abs(s.weightKg - median)).sort((a, b) => a - b))
  const robustSigma = 1.4826 * mad
  const threshold = Math.max(3 * robustSigma, config.outlierToleranceKg)

  const clean = window.filter((s) => Math.abs(s.weightKg - median) <= threshold)
  const validRatio = clean.length / n
  const outliersRemoved = n - clean.length

  if (clean.length === 0 || validRatio < config.minValidRatio) {
    const basis = clean.length === 0 ? window : clean
    const basisMean = mean(basis)
    return {
      stable: false,
      weightKg: roundToResolution(basisMean, config.scaleResolutionKg),
      standardDeviation: populationStdDev(basis, basisMean),
      samplesUsed: clean.length,
      slope: computeSlope(basis),
      range: rangeOf(basis),
      validRatio,
      outliersRemoved,
    }
  }

  const cleanMean = mean(clean)
  const range = rangeOf(clean)
  const stdDev = populationStdDev(clean, cleanMean)
  const slope = computeSlope(clean)

  const stable =
    stdDev <= config.maxStdDevKg && range <= config.maxRangeKg && Math.abs(slope) <= config.maxSlopeKgPerSec

  return {
    stable,
    weightKg: roundToResolution(cleanMean, config.scaleResolutionKg),
    standardDeviation: stdDev,
    samplesUsed: clean.length,
    slope,
    range,
    validRatio,
    outliersRemoved,
  }
}

export type SessionStatus = 'COLLECTING' | 'STABILIZING' | 'STABLE'

export interface PredictorState {
  status: SessionStatus
  stabilizingSinceMs: number | null
  weightKg: number
  standardDeviation: number
  samplesUsed: number
  slope: number
  range: number
  validRatio: number
  outliersRemoved: number
  /** nowMs - stabilizingSinceMs enquanto STABILIZING/STABLE; 0 em COLLECTING. */
  stabilizingForMs: number
}

export const INITIAL_PREDICTOR_STATE: PredictorState = {
  status: 'COLLECTING',
  stabilizingSinceMs: null,
  weightKg: 0,
  standardDeviation: 0,
  samplesUsed: 0,
  slope: 0,
  range: 0,
  validRatio: 0,
  outliersRemoved: 0,
  stabilizingForMs: 0,
}

/**
 * Porta ScaleSession.addReading() — só a parte de confirmação de estabilidade
 * no tempo (COLLECTING -> STABILIZING -> STABLE). Gestão de janela/troca de
 * placa/reset por peso vazio é responsabilidade de quem chama (truckMachine),
 * não deste helper puro.
 */
export function advancePredictor(
  prev: PredictorState,
  window: WeightSample[],
  nowMs: number,
  config: StabilizationConfig = STABILIZATION_CONFIG,
): PredictorState {
  const candidate = predictWeighing(window, config)

  if (!candidate.stable) {
    return {
      status: 'COLLECTING',
      stabilizingSinceMs: null,
      weightKg: candidate.weightKg,
      standardDeviation: candidate.standardDeviation,
      samplesUsed: candidate.samplesUsed,
      slope: candidate.slope,
      range: candidate.range,
      validRatio: candidate.validRatio,
      outliersRemoved: candidate.outliersRemoved,
      stabilizingForMs: 0,
    }
  }

  if (prev.stabilizingSinceMs == null) {
    return {
      status: 'STABILIZING',
      stabilizingSinceMs: nowMs,
      weightKg: candidate.weightKg,
      standardDeviation: candidate.standardDeviation,
      samplesUsed: candidate.samplesUsed,
      slope: candidate.slope,
      range: candidate.range,
      validRatio: candidate.validRatio,
      outliersRemoved: candidate.outliersRemoved,
      stabilizingForMs: 0,
    }
  }

  const stableForMs = nowMs - prev.stabilizingSinceMs
  const status: SessionStatus = stableForMs < config.stabilityDurationMs ? 'STABILIZING' : 'STABLE'
  return {
    status,
    stabilizingSinceMs: prev.stabilizingSinceMs,
    weightKg: candidate.weightKg,
    standardDeviation: candidate.standardDeviation,
    samplesUsed: candidate.samplesUsed,
    slope: candidate.slope,
    range: candidate.range,
    validRatio: candidate.validRatio,
    outliersRemoved: candidate.outliersRemoved,
    stabilizingForMs: stableForMs,
  }
}

function mean(samples: WeightSample[]): number {
  return samples.reduce((sum, s) => sum + s.weightKg, 0) / samples.length
}

function populationStdDev(samples: WeightSample[], meanValue: number): number {
  const sumSquares = samples.reduce((sum, s) => sum + (s.weightKg - meanValue) ** 2, 0)
  return Math.sqrt(sumSquares / samples.length)
}

function rangeOf(samples: WeightSample[]): number {
  const weights = samples.map((s) => s.weightKg)
  return Math.max(...weights) - Math.min(...weights)
}

function medianOf(sortedValues: number[]): number {
  const m = sortedValues.length
  const mid = Math.floor(m / 2)
  return m % 2 === 1 ? sortedValues[mid] : (sortedValues[mid - 1] + sortedValues[mid]) / 2
}

function computeSlope(chronologicallyOrdered: WeightSample[]): number {
  if (chronologicallyOrdered.length < 2) {
    return 0
  }
  const first = chronologicallyOrdered[0]
  const last = chronologicallyOrdered[chronologicallyOrdered.length - 1]
  const dtSeconds = (last.timestampMs - first.timestampMs) / 1000
  if (dtSeconds <= 0) {
    return 0
  }
  return (last.weightKg - first.weightKg) / dtSeconds
}

function roundToResolution(value: number, resolutionKg: number): number {
  return Math.round(value / resolutionKg) * resolutionKg
}
