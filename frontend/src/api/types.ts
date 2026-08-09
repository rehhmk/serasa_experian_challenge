// Espelha 1:1 os records/DTOs Java lidos diretamente do backend (registry/* e
// reports/*). BigDecimal/Instant/UUID não têm serializer custom configurado no
// projeto (sem ObjectMapper próprio) — Jackson serializa como number/string
// ISO-8601/string por padrão, daí os tipos abaixo.

export interface Branch {
  id: string
  name: string
  city: string | null
  state: string | null
}

export interface BranchRequest {
  name: string
  city?: string
  state?: string
}

export interface GrainType {
  id: string
  name: string
  purchasePricePerTon: number
  referenceStockKg: number
}

export interface GrainTypeRequest {
  name: string
  purchasePricePerTon: number
  referenceStockKg: number
}

export interface Truck {
  id: string
  plate: string
  tareWeightKg: number
}

export interface TruckRequest {
  plate: string
  tareWeightKg: number
}

// GET /api/scales nunca devolve apiKey (ScaleController.ScaleSummary, LOG-015).
export interface ScaleSummary {
  id: string
  branchId: string
}

// apiKey em texto plano só existe aqui, na resposta de criação — nunca mais
// recuperável depois (ScaleCreatedResponse.java).
export interface ScaleCreatedResponse extends ScaleSummary {
  apiKey: string
}

export interface ScaleRequest {
  id: string
  branchId: string
}

export type TransportTransactionStatus = 'OPEN' | 'COMPLETED' | 'CANCELLED'

export interface TransportTransaction {
  id: string
  truckId: string
  grainTypeId: string
  branchId: string
  status: TransportTransactionStatus
  purchasePriceSnapshot: number
  startedAt: string
  finishedAt: string | null
}

export interface OpenTransportTransactionRequest {
  truckId: string
  grainTypeId: string
  branchId: string
}

// Relatório "Livro de Pesagens" — único que expõe plate (WeighingBookItem.java).
export interface WeighingBookItem {
  id: string
  recordedAt: string
  branchId: string
  scaleId: string
  plate: string
  grainTypeId: string
  grossWeightKg: number
  tareWeightKg: number
  netWeightKg: number
  cost: number
}

export interface WeighingBookFilters {
  branchId?: string
  grainTypeId?: string
  scaleId?: string
  plate?: string
}

export interface ReportPeriod {
  from: string
  to: string
}

// ReportEnvelope<T> (Java) não carrega metadados de paginação (sem
// totalElements/totalPages) — só o payload da página pedida.
export interface ReportEnvelope<T> {
  period: ReportPeriod | null
  filters: unknown
  data: T[]
}

// Corpo padrão de erro do GlobalExceptionHandler (ApiError.java).
export interface ApiErrorBody {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
}
