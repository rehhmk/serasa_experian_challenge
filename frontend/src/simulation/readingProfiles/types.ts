// Um perfil de caminhão = um gerador de leituras puro, sem saber nada de
// React/XState/HTTP. A truckMachine chama next() a cada tick e faz o POST.
export interface ReadingGenerator {
  next(elapsedMs: number): number
}

export type TruckProfileName = 'normal'
