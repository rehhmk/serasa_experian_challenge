import type { ReadingGenerator } from './types'

export interface SlowEntryProfileOptions {
  targetWeightKg: number
  rampDurationMs?: number
  noiseKg?: number
}

// Rampa deliberadamente longa (bem mais que os ~1.5s do "normal"). O motor
// de estabilização (LOG-007) só ignora uma tendência via outlier removal
// quando ela é uma minoria isolada na janela — uma rampa mais longa que o
// tempo pra encher a janela inteira (maxWindowSamples=40 amostras x ~100ms
// = ~4s) garante um slope real e sustentado acima de maxSlopeKgPerSec pela
// maior parte da subida, então o caminhão legitimamente não estabiliza
// enquanto sobe, só depois de achatar — mesmo mecanismo do backend, não um
// atraso artificial.
export function createSlowEntryProfile({
  targetWeightKg,
  rampDurationMs = 6000,
  noiseKg = 5,
}: SlowEntryProfileOptions): ReadingGenerator {
  return {
    next(elapsedMs: number): number {
      const rampProgress = Math.min(1, elapsedMs / rampDurationMs)
      const base = targetWeightKg * rampProgress
      const noise = (Math.random() * 2 - 1) * noiseKg
      return Math.max(1, base + noise)
    },
  }
}
