import { createNormalProfile, type NormalProfileOptions } from './normal'
import type { ReadingGenerator } from './types'

// O comportamento interessante do duplicateRetry (LOG-008) não mora no
// formato das leituras — mora na truckMachine, que roda duas passagens
// completas (transação nova a cada uma) pro mesmo caminhão. O gerador em si
// só precisa estabilizar de forma comum, então delega pro "normal".
export function createDuplicateRetryProfile(options: NormalProfileOptions): ReadingGenerator {
  return createNormalProfile(options)
}
