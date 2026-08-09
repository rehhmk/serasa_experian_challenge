import type { ReadingGenerator } from './types'

export interface NormalProfileOptions {
  targetWeightKg: number
  rampDurationMs?: number
  noiseKg?: number
}

// Caminhão "de livro": rampa de entrada (0 -> targetWeightKg) simulando o
// caminhão subindo na balança, depois platô com ruído pequeno — sempre dentro
// dos thresholds de stdDev/range/slope, então estabiliza no primeiro window
// elegível. weight precisa ficar > 0 (ScaleReadingRequest.weight é @Positive).
export function createNormalProfile({
  targetWeightKg,
  rampDurationMs = 1500,
  noiseKg = 5,
}: NormalProfileOptions): ReadingGenerator {
  return {
    next(elapsedMs: number): number {
      const rampProgress = Math.min(1, elapsedMs / rampDurationMs)
      const base = targetWeightKg * rampProgress
      const noise = (Math.random() * 2 - 1) * noiseKg
      return Math.max(1, base + noise)
    },
  }
}
