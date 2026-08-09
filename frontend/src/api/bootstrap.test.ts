import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  bootstrapSandbox,
  provisionScales,
  provisionTrucks,
  resolveSandboxBranch,
  resolveSandboxGrainType,
} from './bootstrap'
import { getScaleKey } from './scaleKeyStore'

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

/** Fetch fake orientado a rota: cada teste registra só as rotas que usa. */
function fakeFetch(handlers: Record<string, (init: RequestInit) => Response>) {
  return vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = String(input)
    const key = `${init.method ?? 'GET'} ${url.split('?')[0]}`
    const handler = handlers[key]
    if (!handler) {
      throw new Error(`No fake handler registered for ${key}`)
    }
    return handler(init)
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('resolveSandboxBranch', () => {
  it('reuses the first existing branch instead of creating a new one', async () => {
    const branch = { id: 'b1', name: 'Goiania', city: 'Goiania', state: 'GO' }
    vi.stubGlobal('fetch', fakeFetch({ 'GET /api/branches': () => jsonResponse(200, [branch]) }))

    await expect(resolveSandboxBranch()).resolves.toEqual(branch)
  })

  it('creates a sandbox branch when none exist', async () => {
    const created = { id: 'b1', name: 'Sandbox Branch', city: 'Sandbox City', state: 'SB' }
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'GET /api/branches': () => jsonResponse(200, []),
        'POST /api/branches': () => jsonResponse(201, created),
      }),
    )

    await expect(resolveSandboxBranch()).resolves.toEqual(created)
  })
})

describe('resolveSandboxGrainType', () => {
  it('reuses a grain type already named "Sandbox Grain" if present', async () => {
    const sandboxGrainType = { id: 'g1', name: 'Sandbox Grain', purchasePricePerTon: 1000, referenceStockKg: 100000 }
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'GET /api/grain-types': () =>
          jsonResponse(200, [
            { id: 'g0', name: 'Soja', purchasePricePerTon: 1800, referenceStockKg: 100000 },
            sandboxGrainType,
          ]),
      }),
    )

    await expect(resolveSandboxGrainType()).resolves.toEqual(sandboxGrainType)
  })

  it('creates "Sandbox Grain" (name is UNIQUE in the DB) only when nothing exists at all', async () => {
    const created = { id: 'g1', name: 'Sandbox Grain', purchasePricePerTon: 1000, referenceStockKg: 100000 }
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'GET /api/grain-types': () => jsonResponse(200, []),
        'POST /api/grain-types': () => jsonResponse(201, created),
      }),
    )

    await expect(resolveSandboxGrainType()).resolves.toEqual(created)
  })
})

describe('provisionTrucks', () => {
  it('reuses existing "SB…" trucks and only creates the ones missing to reach count', async () => {
    const existing = [
      { id: 't1', plate: 'SBAAAAA', tareWeightKg: 9000 },
      { id: 't0', plate: 'ABC1D23', tareWeightKg: 9000 }, // seed truck, not sandbox-owned — must be ignored
    ]
    const createBody: unknown[] = []
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'GET /api/trucks': () => jsonResponse(200, existing),
        'POST /api/trucks': (init) => {
          const body = JSON.parse(init.body as string)
          createBody.push(body)
          return jsonResponse(201, { id: `t-new-${createBody.length}`, ...body })
        },
      }),
    )

    const trucks = await provisionTrucks(3)

    expect(trucks).toHaveLength(3)
    expect(trucks[0]).toEqual(existing[0])
    expect(createBody).toHaveLength(2)
    for (const body of createBody as { plate: string }[]) {
      expect(body.plate.startsWith('SB')).toBe(true)
      expect(body.plate.length).toBeLessThanOrEqual(10)
    }
  })

  it('creates nothing when enough sandbox trucks already exist', async () => {
    const existing = [
      { id: 't1', plate: 'SBAAAAA', tareWeightKg: 9000 },
      { id: 't2', plate: 'SBBBBBB', tareWeightKg: 9000 },
    ]
    vi.stubGlobal('fetch', fakeFetch({ 'GET /api/trucks': () => jsonResponse(200, existing) }))

    await expect(provisionTrucks(2)).resolves.toEqual(existing)
  })
})

describe('provisionScales', () => {
  it('always mints fresh scales (never reuses scale-01 or prior ids) and captures each apiKey', async () => {
    let created = 0
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'POST /api/scales': (init) => {
          created += 1
          const body = JSON.parse(init.body as string)
          return jsonResponse(201, { id: body.id, branchId: body.branchId, apiKey: `key-${created}` })
        },
      }),
    )

    const scales = await provisionScales(2, 'branch-1')

    expect(created).toBe(2)
    expect(scales).toHaveLength(2)
    expect(scales.every((s) => s.id.startsWith('sandbox-'))).toBe(true)
    for (const scale of scales) {
      expect(getScaleKey(scale.id)).toBeDefined()
    }
  })
})

describe('bootstrapSandbox', () => {
  it('resolves branch/grainType and provisions scales/trucks in one call', async () => {
    const branch = { id: 'b1', name: 'Goiania', city: 'Goiania', state: 'GO' }
    const grainType = { id: 'g1', name: 'Soja', purchasePricePerTon: 1800, referenceStockKg: 100000 }
    vi.stubGlobal(
      'fetch',
      fakeFetch({
        'GET /api/branches': () => jsonResponse(200, [branch]),
        'GET /api/grain-types': () => jsonResponse(200, [grainType]),
        'GET /api/trucks': () => jsonResponse(200, []),
        'POST /api/trucks': (init) => jsonResponse(201, { id: 't1', ...JSON.parse(init.body as string) }),
        'POST /api/scales': (init) => {
          const body = JSON.parse(init.body as string)
          return jsonResponse(201, { ...body, apiKey: 'key-1' })
        },
      }),
    )

    const result = await bootstrapSandbox({ numLanes: 1, numTrucks: 1 })

    expect(result.branch).toEqual(branch)
    expect(result.grainType).toEqual(grainType)
    expect(result.scales).toHaveLength(1)
    expect(result.trucks).toHaveLength(1)
  })
})
