import { apiRequest } from './http'
import type { Truck, TruckRequest } from './types'

export function listTrucks(): Promise<Truck[]> {
  return apiRequest<Truck[]>('/trucks')
}

export function createTruck(request: TruckRequest): Promise<Truck> {
  return apiRequest<Truck>('/trucks', { method: 'POST', body: request })
}
