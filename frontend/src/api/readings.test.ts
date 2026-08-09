import { afterEach, describe, expect, it, vi } from 'vitest'
import { postReading } from './readings'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('postReading', () => {
  it('sends the X-Scale-Key header and the {id, plate, weight} body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 202 }))
    vi.stubGlobal('fetch', fetchMock)

    await postReading({ scaleId: 'scale-01', apiKey: 'dev-scale-01-key', plate: 'ABC1D23', weightKg: 32010 })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/readings')
    expect(init.headers['X-Scale-Key']).toBe('dev-scale-01-key')
    expect(JSON.parse(init.body)).toEqual({ id: 'scale-01', plate: 'ABC1D23', weight: 32010 })
  })

  it('swallows failures by default — fire-and-forget must never throw into the caller', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })))
    vi.spyOn(console, 'warn').mockImplementation(() => {})

    await expect(
      postReading({ scaleId: 'scale-01', apiKey: 'k', plate: 'ABC1D23', weightKg: 32010 }),
    ).resolves.toBeUndefined()
  })

  it('rethrows when throwOnError is set, for tests/advanced callers that need the real failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 401 })))

    await expect(
      postReading(
        { scaleId: 'scale-01', apiKey: 'wrong-key', plate: 'ABC1D23', weightKg: 32010 },
        { throwOnError: true },
      ),
    ).rejects.toMatchObject({ status: 401 })
  })
})
