import { apiRequest } from './http'
import type { ScaleCreatedResponse, ScaleRequest, ScaleSummary } from './types'

export function listScales(): Promise<ScaleSummary[]> {
  return apiRequest<ScaleSummary[]>('/scales')
}

// Único momento em que a apiKey em texto plano existe — capture-a imediatamente
// (ver scaleKeyStore.ts). Um GET /api/scales depois nunca mais a devolve.
export function createScale(request: ScaleRequest): Promise<ScaleCreatedResponse> {
  return apiRequest<ScaleCreatedResponse>('/scales', { method: 'POST', body: request })
}
