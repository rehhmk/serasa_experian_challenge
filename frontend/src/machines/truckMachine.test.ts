import { assign, createActor, setup, type ActorRefFrom } from 'xstate'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { truckMachine, type TruckInput } from './truckMachine'
import { openTransportTransaction } from '../api/transportTransactions'
import { postReading } from '../api/readings'
import { getWeighingBook } from '../api/reports'
import type { TransportTransaction, WeighingBookItem } from '../api/types'
import type { WeightSample } from '../simulation/stabilizationPredictor'

vi.mock('../api/readings', () => ({ postReading: vi.fn().mockResolvedValue(undefined) }))
vi.mock('../api/transportTransactions', () => ({ openTransportTransaction: vi.fn() }))
vi.mock('../api/reports', () => ({ getWeighingBook: vi.fn() }))

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

/** 26 amostras @5000kg, 500ms de espaçamento — mesmo cenário de stableForFullStabilityDurationSaves (backend). */
function stableReadingSequence(count: number, weightKg = 5000, stepMs = 500): WeightSample[] {
  return Array.from({ length: count }, (_, i) => ({ timestampMs: i * stepMs, weightKg }))
}

async function dispatchAndReachOnScale(
  actor: ReturnType<typeof createActor<typeof truckMachine>>,
): Promise<void> {
  actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
  await vi.advanceTimersByTimeAsync(1200) // TRAVEL_MS
  await vi.advanceTimersByTimeAsync(0) // flush a resolução do openTransaction mockado
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

describe('truckMachine', () => {
  it('DISPATCH leva queued -> travelling -> openingTransaction -> onScale.collecting', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()

    await dispatchAndReachOnScale(actor)

    const snapshot = actor.getSnapshot()
    expect(snapshot.matches({ onScale: 'collecting' })).toBe(true)
    expect(snapshot.context.transactionId).toBe('tx-1')
    expect(openTransportTransaction).toHaveBeenCalledWith({
      truckId: TEST_INPUT.truckId,
      grainTypeId: TEST_INPUT.grainTypeId,
      branchId: TEST_INPUT.branchId,
    })
    actor.stop()
  })

  it('sequência estável por tempo suficiente chega em STABLE e confirma via reports', async () => {
    vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [CONFIRMED_WEIGHING] })
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    await dispatchAndReachOnScale(actor)

    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    // "stable" é transiente (always) — já devia ter saído de onScale pra confirming.
    expect(actor.getSnapshot().value).toBe('confirming')
    await vi.advanceTimersByTimeAsync(0) // flush a confirmação mockada

    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('recorded')
    expect(snapshot.context.confirmedWeighing).toEqual(CONFIRMED_WEIGHING)
    actor.stop()
  })

  it('sequência estável por menos que stabilityDurationMs fica em stabilizing (não confirma cedo demais)', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    await dispatchAndReachOnScale(actor)

    for (const sample of stableReadingSequence(20)) {
      actor.send({ type: 'RAW_READING', sample })
    }

    expect(actor.getSnapshot().matches({ onScale: 'stabilizing' })).toBe(true)
    expect(getWeighingBook).not.toHaveBeenCalled()
    actor.stop()
  })

  it('sem confirmação após MAX_CONFIRM_ATTEMPTS retries, cai em unconfirmed', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    await dispatchAndReachOnScale(actor)

    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)

    // confirmRetryWait -> confirming, 5 vezes (getWeighingBook sempre vazio, do beforeEach)
    for (let i = 0; i < 5; i++) {
      await vi.advanceTimersByTimeAsync(400) // CONFIRM_RETRY_DELAY_MS
      await vi.advanceTimersByTimeAsync(0)
    }

    expect(actor.getSnapshot().value).toBe('unconfirmed')
    // confirmAttempts vira 5 (MAX_CONFIRM_ATTEMPTS) só depois da 5ª chamada — não há uma 6ª.
    expect(getWeighingBook).toHaveBeenCalledTimes(5)
    actor.stop()
  })

  it('falha ao abrir a transação vai para transactionError; RETRY tenta de novo', async () => {
    vi.mocked(openTransportTransaction).mockRejectedValueOnce(new Error('No OPEN TransportTransaction'))
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()

    actor.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('transactionError')

    vi.mocked(openTransportTransaction).mockResolvedValue(OPEN_TRANSACTION)
    actor.send({ type: 'RETRY' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().matches({ onScale: 'collecting' })).toBe(true)
    actor.stop()
  })

  it('a cada tick chama postReading com a X-Scale-Key e a placa corretas (fire-and-forget)', async () => {
    const actor = createActor(truckMachine, { input: TEST_INPUT }).start()
    await dispatchAndReachOnScale(actor)

    await vi.advanceTimersByTimeAsync(100) // um tick do readingLoop real (100ms)

    expect(postReading).toHaveBeenCalledWith(
      expect.objectContaining({ scaleId: 'sandbox-scale-1', apiKey: 'key-1', plate: TEST_INPUT.plate }),
    )
    actor.stop()
  })
})

// leaving/queued dispara sendParent — só existe destino real quando o
// caminhão é spawnado como filho (uso real em produção, via yardMachine na
// próxima PR). O harness abaixo espelha esse contrato pra testar o loop
// completo sem depender do yardMachine ainda não existir.
const harnessMachine = setup({
  types: {
    context: {} as { child: ActorRefFrom<typeof truckMachine>; truckDoneEvents: unknown[] },
  },
  actors: { truck: truckMachine },
}).createMachine({
  context: ({ spawn }) => ({
    child: spawn('truck', { input: TEST_INPUT }),
    truckDoneEvents: [],
  }),
  on: {
    TRUCK_DONE: {
      actions: assign({ truckDoneEvents: ({ context, event }) => [...context.truckDoneEvents, event] }),
    },
  },
})

describe('truckMachine (spawnado como filho, contrato real de produção)', () => {
  it('completa uma passagem, avisa o pai via TRUCK_DONE e volta para queued', async () => {
    vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [CONFIRMED_WEIGHING] })
    const parent = createActor(harnessMachine).start()
    const child = parent.getSnapshot().context.child

    child.send({ type: 'DISPATCH', scaleId: 'sandbox-scale-1', apiKey: 'key-1' })
    await vi.advanceTimersByTimeAsync(1200)
    await vi.advanceTimersByTimeAsync(0)

    for (const sample of stableReadingSequence(26)) {
      child.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)
    expect(child.getSnapshot().value).toBe('recorded')

    await vi.advanceTimersByTimeAsync(800) // RECORDED_PAUSE_MS
    await vi.advanceTimersByTimeAsync(600) // LEAVE_MS

    expect(child.getSnapshot().value).toBe('queued')
    expect(parent.getSnapshot().context.truckDoneEvents).toEqual([
      { type: 'TRUCK_DONE', descriptorId: TEST_INPUT.descriptorId, scaleId: 'sandbox-scale-1' },
    ])
    parent.stop()
  })
})
