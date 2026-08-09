import type { ReadingGenerator } from './types'

export interface NoisyProfileOptions {
  targetWeightKg: number
  rampDurationMs?: number
  noiseKg?: number
  spikeKg?: number
  spikeEveryNthTick?: number
}

// Mesma rampa/platô do "normal", mas com um pico isolado grande de tempos em
// tempos (mirror direto do teste real do backend: 20 amostras estáveis + 1
// pico de +800kg -> outlier descartado por mediana+MAD, ainda estável).
// spikeEveryNthTick=8 mantém a fração de outliers bem abaixo dos 20%
// tolerados (minValidRatio=0.8) — o caminhão estabiliza mesmo assim.
export function createNoisyProfile({
  targetWeightKg,
  rampDurationMs = 1500,
  noiseKg = 5,
  spikeKg = 800,
  spikeEveryNthTick = 8,
}: NoisyProfileOptions): ReadingGenerator {
  let tick = 0
  return {
    next(elapsedMs: number): number {
      tick += 1
      const rampProgress = Math.min(1, elapsedMs / rampDurationMs)
      const base = targetWeightKg * rampProgress
      const noise = (Math.random() * 2 - 1) * noiseKg
      const isSpikeTick = rampProgress >= 1 && tick % spikeEveryNthTick === 0
      return Math.max(1, base + noise + (isSpikeTick ? spikeKg : 0))
    },
  }
}
