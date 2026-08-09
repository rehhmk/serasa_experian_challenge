import { describe, expect, it } from 'vitest'
import { predictWeighing } from '../stabilizationPredictor'
import type { WeightSample } from '../stabilizationPredictor'
import { createSlowEntryProfile } from './slowEntry'

describe('createSlowEntryProfile', () => {
  it('não é estável enquanto a rampa ainda domina a janela (guarda de slope real, não atraso artificial)', () => {
    const target = 30000
    const generator = createSlowEntryProfile({ targetWeightKg: target, rampDurationMs: 6000 })

    const window: WeightSample[] = []
    for (let i = 0; i < 20; i++) {
      const timestampMs = i * 100
      window.push({ timestampMs, weightKg: generator.next(timestampMs) })
    }

    expect(predictWeighing(window).stable).toBe(false)
  })

  it('estabiliza depois de achatar, uma vez que a janela fica só com o platô', () => {
    const target = 30000
    const generator = createSlowEntryProfile({ targetWeightKg: target, rampDurationMs: 6000 })

    const flatWindow: WeightSample[] = []
    for (let i = 0; i < 90; i++) {
      const timestampMs = i * 100
      const weightKg = generator.next(timestampMs)
      if (timestampMs >= 7000) {
        flatWindow.push({ timestampMs, weightKg })
      }
    }

    const result = predictWeighing(flatWindow.slice(-20))
    expect(result.stable).toBe(true)
    expect(Math.abs(result.weightKg - target)).toBeLessThan(50)
  })
})
