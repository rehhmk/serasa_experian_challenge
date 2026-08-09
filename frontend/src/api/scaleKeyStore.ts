// Chaves de API de balanças provisionadas pelo sandbox — só em memória, nunca
// localStorage/sessionStorage. Um refresh da página reprovisiona balanças
// novas do zero (ver bootstrap.ts); é o comportamento aceito, não um bug.
const scaleKeys = new Map<string, string>()

export function setScaleKey(scaleId: string, apiKey: string): void {
  scaleKeys.set(scaleId, apiKey)
}

export function getScaleKey(scaleId: string): string | undefined {
  return scaleKeys.get(scaleId)
}

export function hasScaleKey(scaleId: string): boolean {
  return scaleKeys.has(scaleId)
}

export function clearScaleKeys(): void {
  scaleKeys.clear()
}
