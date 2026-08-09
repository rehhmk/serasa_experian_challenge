import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, ApiRequestError } from './http'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function emptyResponse(status: number): Response {
  return new Response(null, { status })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('GETs a relative /api path and returns the parsed JSON body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { id: '1', name: 'Sorriso-MT' }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await apiRequest<{ id: string; name: string }>('/branches/1')

    expect(result).toEqual({ id: '1', name: 'Sorriso-MT' })
    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/branches/1')
    expect(init.method).toBe('GET')
  })

  it('serializes the body and sets Content-Type on POST', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(201, { id: '1' }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/branches', { method: 'POST', body: { name: 'Sorriso-MT' } })

    const [, init] = fetchMock.mock.calls[0]
    expect(init.method).toBe('POST')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(init.body).toBe(JSON.stringify({ name: 'Sorriso-MT' }))
  })

  it('encodes query params, skipping undefined values', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { data: [] }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/reports/weighings', {
      query: { from: '2026-01-01T00:00:00Z', to: '2026-01-02T00:00:00Z', scaleId: undefined, page: 0 },
    })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/reports/weighings?from=2026-01-01T00%3A00%3A00Z&to=2026-01-02T00%3A00%3A00Z&page=0')
  })

  it('returns undefined for a 202 Accepted with no body (readings contract)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(emptyResponse(202)))

    const result = await apiRequest('/readings', { method: 'POST', body: { id: 'scale-01' } })

    expect(result).toBeUndefined()
  })

  it('throws ApiRequestError with the backend message on a mapped error (GlobalExceptionHandler shape)', async () => {
    const errorBody = {
      timestamp: '2026-01-01T00:00:00Z',
      status: 422,
      error: 'Unprocessable Entity',
      message: 'No OPEN TransportTransaction for truck ABC1D23',
      path: '/api/readings',
    }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(422, errorBody)))

    await expect(apiRequest('/readings', { method: 'POST', body: {} })).rejects.toMatchObject({
      name: 'ApiRequestError',
      status: 422,
      message: errorBody.message,
      body: errorBody,
    })
  })

  it('falls back to a generic message when the error response has no JSON body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(emptyResponse(500)))

    const error = await apiRequest('/branches').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiRequestError)
    expect((error as ApiRequestError).status).toBe(500)
    expect((error as ApiRequestError).body).toBeNull()
  })
})
