import { apiRequest } from './http'
import type { ReportEnvelope, WeighingBookFilters, WeighingBookItem } from './types'

export interface GetWeighingBookParams extends WeighingBookFilters {
  from: string
  to: string
  page?: number
  size?: number
}

// Única forma de confirmar que uma Weighing real foi persistida — o POST de
// leitura nunca informa isso (ver readings.ts). Usado pela truckMachine no
// estado "confirming" (PR4).
export function getWeighingBook(
  params: GetWeighingBookParams,
): Promise<ReportEnvelope<WeighingBookItem>> {
  const { from, to, branchId, grainTypeId, scaleId, plate, page, size } = params
  return apiRequest<ReportEnvelope<WeighingBookItem>>('/reports/weighings', {
    query: { from, to, branchId, grainTypeId, scaleId, plate, page, size },
  })
}
