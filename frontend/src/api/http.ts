import type { ApiErrorBody } from './types'

export class ApiRequestError extends Error {
  readonly status: number
  readonly body: ApiErrorBody | null

  constructor(status: number, body: ApiErrorBody | null, fallbackMessage: string) {
    super(body?.message ?? fallbackMessage)
    this.name = 'ApiRequestError'
    this.status = status
    this.body = body
  }
}

export type QueryValue = string | number | boolean | undefined

interface RequestOptions {
  method?: 'GET' | 'POST'
  body?: unknown
  headers?: Record<string, string>
  query?: Record<string, QueryValue>
}

function buildPath(path: string, query?: Record<string, QueryValue>): string {
  const params = new URLSearchParams()
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) params.set(key, String(value))
    }
  }
  const qs = params.toString()
  return `/api${path}${qs ? `?${qs}` : ''}`
}

async function safeParseJson<T>(response: Response): Promise<T | null> {
  const text = await response.text()
  if (!text) return null
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}

// Client HTTP único do sandbox — todo fetch() do app passa por aqui. Base
// relativa (/api/...) por design: funciona com o proxy do Vite em dev
// (vite.config.ts) e continuaria funcionando se o build algum dia fosse
// servido same-origin pelo próprio Spring Boot, sem precisar mudar nada aqui.
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, headers, query } = options

  const response = await fetch(buildPath(path, query), {
    method,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...headers,
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!response.ok) {
    const errorBody = await safeParseJson<ApiErrorBody>(response)
    throw new ApiRequestError(
      response.status,
      errorBody,
      `Request failed: ${method} ${path} -> ${response.status}`,
    )
  }

  // POST /api/readings sempre responde 202 sem corpo (fire-and-forget).
  if (response.status === 202 || response.status === 204) {
    return undefined as T
  }

  return (await safeParseJson<T>(response)) as T
}
