import { createActor, setup } from 'xstate'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { truckMachine, type TruckInput } from '../../machines/truckMachine'
import { openTransportTransaction } from '../../api/transportTransactions'
import { getWeighingBook } from '../../api/reports'
import type { TransportTransaction, WeighingBookItem } from '../../api/types'
import type { WeightSample } from '../../simulation/stabilizationPredictor'
import { describeTruckDisplay } from './truckDisplay'

vi.mock('../../api/readings', () => ({ postReading: vi.fn().mockResolvedValue(undefined) }))
vi.mock('../../api/transportTransactions', () => ({ openTransportTransaction: vi.fn() }))
vi.mock('../../api/reports', () => ({ getWeighingBook: vi.fn() }))

const TEST_INPUT: TruckInput = {
  descriptorId: 'truck-1',
  plate: 'SBAAAAA',
  truckId: 'truck-uuid-1',
  tareWeightKg: 9000,
  branchId: 'branch-1',
  grainTypeId: 'grain-1',
  profile: 'normal',
}

const OPEN_TRANSACTION: TransportTransaction = {
  id: 'tx-1',
  truckId: TEST_INPUT.truckId,
  grainTypeId: TEST_INPUT.grainTypeId,
  branchId: TEST_INPUT.branchId,
  status: 'OPEN',
  purchasePriceSnapshot: 1000,
  startedAt: new Date().toISOString(),
  finishedAt: null,
}

const CONFIRMED_WEIGHING: WeighingBookItem = {
  id: 'w-1',
  recordedAt: new Date().toISOString(),
  branchId: TEST_INPUT.branchId,
  scaleId: 'sandbox-scale-1',
  plate: TEST_INPUT.plate,
  grainTypeId: TEST_INPUT.grainTypeId,
  grossWeightKg: 32010,
  tareWeightKg: 9000,
  netWeightKg: 23010,
  cost: 23010,
}

function stableReadingSequence(count: number, weightKg = 5000, stepMs = 500): WeightSample[] {
  return Array.from({ length: count }, (_, i) => ({ timestampMs: i * stepMs, weightKg }))
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.mocked(openTransportTransaction).mockResolvedValue(OPEN_TRANSACTION)
  vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [] })
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('describeTruckDisplay', () => {
  it('queued (nunca despachado) -> "Na fila", roadPercent 0', () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    expect(describeTruckDisplay(actor.getSnapshot())).toEqual({ label: 'Na fila', tone: 'neutral', roadPercent: 0 })
    actor.stop()
  })

  it('travelling -> "A caminho", roadPercent baixo (entrando)', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })

    const display = describeTruckDisplay(actor.getSnapshot())
    expect(display.label).toBe('A caminho')
    expect(display.roadPercent).toBeGreaterThan(0)
    expect(display.roadPercent).toBeLessThan(50)
    actor.stop()
  })

  it('onScale.collecting -> "Coletando", roadPercent 50 (na balança)', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)

    expect(describeTruckDisplay(actor.getSnapshot())).toEqual({ label: 'Coletando', tone: 'neutral', roadPercent: 50 })
    actor.stop()
  })

  it('onScale.stabilizing -> "Estabilizando", ainda roadPercent 50', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)
    for (const sample of stableReadingSequence(20)) {
      actor.send({ type: 'RAW_READING', sample })
    }

    expect(describeTruckDisplay(actor.getSnapshot())).toEqual({
      label: 'Estabilizando',
      tone: 'progress',
      roadPercent: 50,
    })
    actor.stop()
  })

  it('recorded -> "Estável"', async () => {
    vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [CONFIRMED_WEIGHING] })
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)
    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)

    expect(describeTruckDisplay(actor.getSnapshot())).toEqual({ label: 'Estável', tone: 'success', roadPercent: 50 })
    actor.stop()
  })

  it('leaving -> "Saindo", roadPercent alto (saindo pela direita)', async () => {
    // recorded -> leaving dispara sendParent (TRUCK_DONE) — só existe destino
    // real quando o caminhão é filho de outro ator (uso real via
    // yardMachine), daí o harness, mesmo padrão de truckMachine.test.ts.
    const harness = setup({ actors: { truck: truckMachine } }).createMachine({
      context: ({ spawn }) => ({ child: spawn('truck', { input: TEST_INPUT }) }),
      on: { TRUCK_DONE: {} },
    })
    vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [CONFIRMED_WEIGHING] })
    const parent = createActor(harness).start()
    const child = parent.getSnapshot().context.child

    child.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)
    for (const sample of stableReadingSequence(26)) {
      child.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(800) // RECORDED_PAUSE_MS

    const display = describeTruckDisplay(child.getSnapshot())
    expect(display.label).toBe('Saindo')
    expect(display.roadPercent).toBeGreaterThan(50)
    parent.stop()
  })
})
