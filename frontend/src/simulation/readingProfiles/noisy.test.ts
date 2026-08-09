import { describe, expect, it } from 'vitest'
import { predictWeighing } from '../stabilizationPredictor'
import type { WeightSample } from '../stabilizationPredictor'
import { createNoisyProfile } from './noisy'

describe('createNoisyProfile', () => {
  it('produz picos isolados que o algoritmo real (mediana+MAD) filtra, permanecendo estável', () => {
    const target = 30000
    const generator = createNoisyProfile({ targetWeightKg: target, rampDurationMs: 0, spikeEveryNthTick: 8 })

    const window: WeightSample[] = []
    for (let i = 0; i < 24; i++) {
      const timestampMs = i * 100
      window.push({ timestampMs, weightKg: generator.next(timestampMs) })
    }

    const spikeCount = window.filter((s) => s.weightKg > target + 700).length
    expect(spikeCount).toBeGreaterThan(0) // o cenário só faz sentido se um pico de fato ocorreu
    expect(spikeCount / window.length).toBeLessThan(0.2) // dentro do minValidRatio real (0.8)

    const result = predictWeighing(window)
    expect(result.stable).toBe(true)
    expect(Math.abs(result.weightKg - target)).toBeLessThan(50)
  })
})
