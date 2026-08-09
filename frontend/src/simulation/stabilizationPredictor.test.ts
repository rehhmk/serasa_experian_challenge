import { describe, expect, it } from 'vitest'
import {
  advancePredictor,
  INITIAL_PREDICTOR_STATE,
  predictWeighing,
  type PredictorState,
  type WeightSample,
} from './stabilizationPredictor'

// Cenários portados 1:1 de StabilizationEngineTest.java (LOG-007/LOG-012) —
// mesmos pesos, mesmo espaçamento (100ms), mesmas asserções. Prova que o
// mirror do frontend concorda com o algoritmo real, não só "parece certo".
function windowOf(weights: number[], stepMs = 100): WeightSample[] {
  return weights.map((weightKg, i) => ({ timestampMs: i * stepMs, weightKg }))
}

describe('predictWeighing (mirrors StabilizationEngine.process)', () => {
  it('a fully stable window is stable', () => {
    const result = predictWeighing(windowOf(Array(20).fill(5000)))
    expect(result.stable).toBe(true)
    expect(result.weightKg).toBeCloseTo(5000, 3)
    expect(result.standardDeviation).toBeCloseTo(0, 3)
    expect(result.samplesUsed).toBe(20)
  })

  it('small oscillating noise is still stable', () => {
    const weights = Array.from({ length: 20 }, (_, i) => 5000 + (i % 2 === 0 ? 3 : -3))
    const result = predictWeighing(windowOf(weights))
    expect(result.stable).toBe(true)
    expect(result.weightKg).toBeCloseTo(5000, 3)
    expect(result.standardDeviation).toBeCloseTo(3, 3)
    expect(result.samplesUsed).toBe(20)
  })

  it('a single large outlier is removed by median+MAD (mirrors the "noisy" truck profile)', () => {
    const weights = Array(20).fill(5000)
    weights[10] = 5800
    const result = predictWeighing(windowOf(weights))
    expect(result.stable).toBe(true)
    expect(result.weightKg).toBeCloseTo(5000, 3)
    expect(result.standardDeviation).toBeCloseTo(0, 3)
    expect(result.samplesUsed).toBe(19)
  })

  it('a clear upward trend is never stable while ramping (mirrors the "slowEntry" truck profile)', () => {
    const weights = Array.from({ length: 20 }, (_, i) => 5000 + 1.5 * i)
    const result = predictWeighing(windowOf(weights))
    expect(result.stable).toBe(false)
    expect(result.samplesUsed).toBe(20)
    expect(result.weightKg).toBeCloseTo(5020, 3)
    expect(result.slope).toBeCloseTo(15, 3) // kg/s, > maxSlopeKgPerSec=10
  })

  it('a clear downward trend is never stable while ramping', () => {
    const weights = Array.from({ length: 20 }, (_, i) => 6000 - 1.5 * i)
    const result = predictWeighing(windowOf(weights))
    expect(result.stable).toBe(false)
    expect(result.samplesUsed).toBe(20)
    expect(result.weightKg).toBeCloseTo(5980, 3)
    expect(result.slope).toBeCloseTo(-15, 3)
  })

  it('oscillation beyond the range/stdDev threshold is not stable', () => {
    const weights = Array<number>(20).fill(0)
    weights[0] = 5000
    for (let i = 1; i < 19; i++) {
      weights[i] = i % 2 === 1 ? 5150 : 4850
    }
    weights[19] = 5000
    const result = predictWeighing(windowOf(weights))
    expect(result.stable).toBe(false)
    expect(result.samplesUsed).toBe(20)
    expect(result.standardDeviation).toBeCloseTo(142.3025, 2)
  })

  it('fewer samples than minSamples is never stable', () => {
    const result = predictWeighing(windowOf(Array(19).fill(5000)))
    expect(result.stable).toBe(false)
    expect(result.samplesUsed).toBe(19)
    expect(result.standardDeviation).toBeCloseTo(0, 3)
  })

  it('the final weight is rounded to the scale resolution', () => {
    const result = predictWeighing(windowOf(Array(20).fill(5011)))
    expect(result.stable).toBe(true)
    expect(result.weightKg).toBeCloseTo(5020, 3)
    expect(result.samplesUsed).toBe(20)
  })
})

describe('advancePredictor (mirrors ScaleSession.addReading timing)', () => {
  it('stable for less than stabilityDurationMs stays STABILIZING', () => {
    let state: PredictorState = INITIAL_PREDICTOR_STATE
    const window: WeightSample[] = []
    for (let i = 0; i < 20; i++) {
      const sample = { timestampMs: i * 500, weightKg: 5000 }
      window.push(sample)
      state = advancePredictor(state, window, sample.timestampMs)
    }
    expect(state.status).toBe('STABILIZING')

    for (let i = 20; i < 25; i++) {
      const sample = { timestampMs: i * 500, weightKg: 5000 }
      window.push(sample)
      state = advancePredictor(state, window, sample.timestampMs)
    }
    expect(state.status).toBe('STABILIZING')
  })

  it('stable for the full stabilityDurationMs reaches STABLE', () => {
    let state: PredictorState = INITIAL_PREDICTOR_STATE
    const window: WeightSample[] = []
    for (let i = 0; i < 26; i++) {
      const sample = { timestampMs: i * 500, weightKg: 5000 }
      window.push(sample)
      state = advancePredictor(state, window, sample.timestampMs)
    }
    expect(state.status).toBe('STABLE')
    expect(state.weightKg).toBeCloseTo(5000, 3)
    expect(state.standardDeviation).toBeCloseTo(0, 3)
  })
})
