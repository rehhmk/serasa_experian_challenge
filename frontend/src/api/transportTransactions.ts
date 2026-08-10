import { apiRequest } from './http'
import type { OpenTransportTransactionRequest, TransportTransaction, TransportTransactionStatus } from './types'

export function openTransportTransaction(
  request: OpenTransportTransactionRequest,
): Promise<TransportTransaction> {
  return apiRequest<TransportTransaction>('/transport-transactions', {
    method: 'POST',
    body: request,
  })
}

// truckId+status juntos (TransportTransactionController.list, LOG-020) — não
// existe combinação parcial suportada no backend, então esta função sempre
// manda os dois. Uso real: sandbox resolvendo um conflito 409 de abertura,
// achando a transaction OPEN travada de uma sessão anterior pra cancelar.
export function findOpenTransportTransactionForTruck(
  truckId: string,
): Promise<TransportTransaction[]> {
  const status: TransportTransactionStatus = 'OPEN'
  return apiRequest<TransportTransaction[]>('/transport-transactions', {
    query: { truckId, status },
  })
}

export function cancelTransportTransaction(id: string): Promise<TransportTransaction> {
  return apiRequest<TransportTransaction>(`/transport-transactions/${id}/cancel`, {
    method: 'POST',
  })
}
