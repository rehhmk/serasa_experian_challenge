import { apiRequest } from './http'
import type { GrainType, GrainTypeRequest } from './types'

export function listGrainTypes(): Promise<GrainType[]> {
  return apiRequest<GrainType[]>('/grain-types')
}

export function createGrainType(request: GrainTypeRequest): Promise<GrainType> {
  return apiRequest<GrainType>('/grain-types', { method: 'POST', body: request })
}
