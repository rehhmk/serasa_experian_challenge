import { apiRequest } from './http'
import type { OpenTransportTransactionRequest, TransportTransaction } from './types'

export function openTransportTransaction(
  request: OpenTransportTransactionRequest,
): Promise<TransportTransaction> {
  return apiRequest<TransportTransaction>('/transport-transactions', {
    method: 'POST',
    body: request,
  })
}
