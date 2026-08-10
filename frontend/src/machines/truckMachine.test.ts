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

    // confirmRetryWait -> confirming, 10 vezes (getWeighingBook sempre vazio, do beforeEach)
    for (let i = 0; i < 10; i++) {
      await vi.advanceTimersByTimeAsync(600) // CONFIRM_RETRY_DELAY_MS
      await vi.advanceTimersByTimeAsync(0)
    }

    expect(actor.getSnapshot().value).toBe('unconfirmed')
    // confirmAttempts vira 10 (MAX_CONFIRM_ATTEMPTS) só depois da 10ª chamada — não há uma 11ª.
    expect(getWeighingBook).toHaveBeenCalledTimes(10)
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

describe('truckMachine — duplicateRetry (LOG-008)', () => {
  it('só abre a 2ª transação depois de confirmar a 1ª e "esvaziar" de verdade (nunca em paralelo)', async () => {
    const firstWeighing = { ...CONFIRMED_WEIGHING, id: 'w-1' }
    const secondWeighing = { ...CONFIRMED_WEIGHING, id: 'w-2', netWeightKg: 23500 }
    vi.mocked(getWeighingBook)
      .mockResolvedValueOnce({ period: null, filters: {}, data: [firstWeighing] })
      .mockResolvedValueOnce({ period: null, filters: {}, data: [secondWeighing] })
    vi.mocked(openTransportTransaction)
      .mockResolvedValueOnce({ ...OPEN_TRANSACTION, id: 'tx-1' })
      .mockResolvedValueOnce({ ...OPEN_TRANSACTION, id: 'tx-2' })

    const actor = createActor(truckMachine, { input: { ...TEST_INPUT, profile: 'duplicateRetry' } }).start()
    await dispatchAndReachOnScale(actor)

    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)
    expect(actor.getSnapshot().value).toBe('recorded')
    expect(actor.getSnapshot().context.transactionId).toBe('tx-1')
    expect(openTransportTransaction).toHaveBeenCalledTimes(1) // a 2ª ainda não pode existir

    await vi.advanceTimersByTimeAsync(800) // RECORDED_PAUSE_MS
    expect(actor.getSnapshot().value).toBe('emptying')
    expect(openTransportTransaction).toHaveBeenCalledTimes(1) // esvaziando != aberto de novo

    await vi.advanceTimersByTimeAsync(100) // um tick do emptyLoop real
    expect(postReading).toHaveBeenCalledWith(
      expect.objectContaining({ plate: TEST_INPUT.plate, weightKg: 50 }), // <= emptyThresholdKg real (200)
    )

    await vi.advanceTimersByTimeAsync(1300) // EMPTY_MARGIN_MS (emptyDurationMs real + margem)
    await vi.advanceTimersByTimeAsync(0)

    expect(openTransportTransaction).toHaveBeenCalledTimes(2) // só agora, sessão "resetada"
    expect(actor.getSnapshot().matches({ onScale: 'collecting' })).toBe(true)
    expect(actor.getSnapshot().context.passIndex).toBe(2)
    expect(actor.getSnapshot().context.transactionId).toBe('tx-2')
    expect(actor.getSnapshot().context.confirmedWeighing).toBeNull() // contexto da 2ª passagem é limpo, não herda o da 1ª

    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('recorded')
    expect(actor.getSnapshot().context.confirmedWeighing).toEqual(secondWeighing) // confirma a pesagem certa (a nova)
    actor.stop()
  })

  it('falha ao abrir a 2ª transação tenta de novo em openingSecondTransaction, nunca em openingTransaction', async () => {
    vi.mocked(getWeighingBook).mockResolvedValue({ period: null, filters: {}, data: [CONFIRMED_WEIGHING] })
    vi.mocked(openTransportTransaction)
      .mockResolvedValueOnce(OPEN_TRANSACTION) // 1ª passagem ok
      .mockRejectedValueOnce(new Error('Multiple OPEN TransportTransactions')) // 2ª falha
      .mockResolvedValueOnce({ ...OPEN_TRANSACTION, id: 'tx-2' }) // retry ok

    const actor = createActor(truckMachine, { input: { ...TEST_INPUT, profile: 'duplicateRetry' } }).start()
    await dispatchAndReachOnScale(actor)
    for (const sample of stableReadingSequence(26)) {
      actor.send({ type: 'RAW_READING', sample })
    }
    await vi.advanceTimersByTimeAsync(0)
    await vi.advanceTimersByTimeAsync(800) // RECORDED_PAUSE_MS -> emptying
    await vi.advanceTimersByTimeAsync(1300) // EMPTY_MARGIN_MS -> openingSecondTransaction (mock rejeita)
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('transactionError')
    expect(actor.getSnapshot().context.passIndex).toBe(2) // já sabe que a tentativa em curso é a 2ª

    actor.send({ type: 'RETRY' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().matches({ onScale: 'collecting' })).toBe(true)
    expect(actor.getSnapshot().context.transactionId).toBe('tx-2')
    expect(openTransportTransaction).toHaveBeenCalledTimes(3) // nunca reabriu a 1ª de novo
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
