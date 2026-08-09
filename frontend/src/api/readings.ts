import { apiRequest } from './http'

export interface PostReadingParams {
  scaleId: string
  apiKey: string
  plate: string
  weightKg: number
}

export interface PostReadingOptions {
  /** Testes/casos avançados que precisam ver a falha real, em vez de engolida. */
  throwOnError?: boolean
}

// Espelha o contrato real do ESP32 (POST /api/readings, fire-and-forget,
// sempre 202 sem corpo — ScaleReadingController.java): por padrão nunca lança,
// só loga. A máquina de estados do caminhão nunca deve travar esperando isso.
export async function postReading(
  { scaleId, apiKey, plate, weightKg }: PostReadingParams,
  options: PostReadingOptions = {},
): Promise<void> {
  try {
    await apiRequest<void>('/readings', {
      method: 'POST',
      headers: { 'X-Scale-Key': apiKey },
      body: { id: scaleId, plate, weight: weightKg },
    })
  } catch (error) {
    if (options.throwOnError) {
      throw error
    }
    console.warn('postReading failed (ignorado, fire-and-forget por design)', error)
  }
}
