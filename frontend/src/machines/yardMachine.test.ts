import { createActor } from 'xstate'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { yardMachine, type YardInput } from './yardMachine'
import { bootstrapSandbox, provisionScales, provisionTrucks } from '../api/bootstrap'
import { cancelTransportTransaction, openTransportTransaction } from '../api/transportTransactions'
import { clearScaleKeys, getScaleKey, setScaleKey } from '../api/scaleKeyStore'
import type { Branch, GrainType, ScaleSummary, Truck } from '../api/types'

vi.mock('../api/bootstrap', () => ({
  bootstrapSandbox: vi.fn(),
  provisionScales: vi.fn(),
  provisionTrucks: vi.fn(),
}))
// truckMachine chama estes módulos direto — mockados pra nenhum truck
// spawnado tentar rede de verdade, mesmo que os testes daqui não avancem
// nenhum caminhão até o fim do ciclo (isso já é coberto por truckMachine.test.ts).
// openTransportTransaction nunca resolve por padrão (trucks ficam presos em
// "abrindo transação", nunca ganham transactionId) — o teste de RESET que
// precisa de um transactionId de verdade sobrescreve isso pontualmente.
vi.mock('../api/readings', () => ({ postReading: vi.fn().mockResolvedValue(undefined) }))
vi.mock('../api/transportTransactions', () => ({
  openTransportTransaction: vi.fn(() => new Promise(() => {})),
  findOpenTransportTransactionForTruck: vi.fn().mockResolvedValue([]),
  cancelTransportTransaction: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('../api/reports', () => ({ getWeighingBook: vi.fn(() => new Promise(() => {})) }))

const BRANCH: Branch = { id: 'branch-1', name: 'Sandbox Branch', city: null, state: null }
const GRAIN_TYPE: GrainType = { id: 'grain-1', name: 'Sandbox Grain', purchasePricePerTon: 1000, referenceStockKg: 100000 }

function scale(id: string): ScaleSummary {
  return { id, branchId: BRANCH.id }
}

function truck(id: string, plate: string): Truck {
  return { id, plate, tareWeightKg: 9000 }
}

function mockBootstrap(scales: ScaleSummary[], trucks: Truck[]): void {
  vi.mocked(bootstrapSandbox).mockResolvedValue({ branch: BRANCH, grainType: GRAIN_TYPE, scales, trucks })
  for (const s of scales) {
    setScaleKey(s.id, `key-${s.id}`)
  }
}

const DEFAULT_INPUT: YardInput = { numLanes: 2, numTrucks: 3 }

async function bootAndReachReady(input: YardInput = DEFAULT_INPUT) {
  const actor = createActor(yardMachine, { input }).start()
  await vi.advanceTimersByTimeAsync(0)
  return actor
}

beforeEach(() => {
  vi.useFakeTimers()
  clearScaleKeys()
})

afterEach(() => {
  vi.useRealTimers()
  vi.clearAllMocks()
})

describe('yardMachine bootstrapping', () => {
  it('provisiona balanças/caminhões, spawna um ator por caminhão e enfileira todos', async () => {
    mockBootstrap([scale('sandbox-a'), scale('sandbox-b')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB'), truck('t3', 'SBCCCCC')])

    const actor = await bootAndReachReady()
    const snapshot = actor.getSnapshot()

    expect(snapshot.value).toBe('ready')
    expect(Object.keys(snapshot.context.lanes)).toEqual(['sandbox-a', 'sandbox-b'])
    expect(snapshot.context.lanes).toEqual({ 'sandbox-a': null, 'sandbox-b': null })
    expect(snapshot.context.queue).toEqual(['t1', 't2', 't3'])
    expect(Object.keys(snapshot.context.truckRefs)).toEqual(['t1', 't2', 't3'])
    expect(snapshot.context.truckRefs.t1.getSnapshot().value).toBe('queued')
    actor.stop()
  })

  it('erro no bootstrap vai para bootstrapError; RETRY tenta de novo', async () => {
    vi.mocked(bootstrapSandbox).mockRejectedValueOnce(new Error('network down'))
    const actor = createActor(yardMachine, { input: DEFAULT_INPUT }).start()
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('bootstrapError')

    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    actor.send({ type: 'RETRY' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('ready')
    actor.stop()
  })
})

describe('yardMachine dispatch manual', () => {
  it('DISPATCH_NEXT ocupa a primeira raia livre com o primeiro caminhão da fila', async () => {
    mockBootstrap([scale('sandbox-a'), scale('sandbox-b')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 2, numTrucks: 2 })

    actor.send({ type: 'DISPATCH_NEXT' })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.context.lanes['sandbox-a']).toBe('t1')
    expect(snapshot.context.queue).toEqual(['t2'])
    expect(snapshot.context.truckRefs.t1.getSnapshot().value).toBe('travelling')
    actor.stop()
  })

  it('DISPATCH_NEXT sem fila ou sem raia livre não faz nada', async () => {
    mockBootstrap([scale('sandbox-a')], [])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 0 })

    actor.send({ type: 'DISPATCH_NEXT' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().context.lanes).toEqual({ 'sandbox-a': null })
    actor.stop()
  })
})

describe('yardMachine modo Auto', () => {
  it('com Auto ligado, drena a fila sozinho até as raias ficarem cheias', async () => {
    mockBootstrap(
      [scale('sandbox-a'), scale('sandbox-b')],
      [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB'), truck('t3', 'SBCCCCC')],
    )
    const actor = await bootAndReachReady({ numLanes: 2, numTrucks: 3 })

    actor.send({ type: 'TOGGLE_AUTO' })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(Object.values(snapshot.context.lanes).sort()).toEqual(['t1', 't2'])
    expect(snapshot.context.queue).toEqual(['t3']) // 3º caminhão espera raia livre
    actor.stop()
  })

  it('com Auto desligado, a fila não se move sozinha', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    await vi.advanceTimersByTimeAsync(5000)

    expect(actor.getSnapshot().context.lanes['sandbox-a']).toBeNull()
    expect(actor.getSnapshot().context.queue).toEqual(['t1'])
    actor.stop()
  })
})

describe('yardMachine TRUCK_DONE', () => {
  it('libera a raia e devolve o caminhão pro fim da fila após a pausa de requeue', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 2 })
    actor.send({ type: 'DISPATCH_NEXT' }) // t1 ocupa sandbox-a, queue=[t2]
    await vi.advanceTimersByTimeAsync(0)
    expect(actor.getSnapshot().context.queue).toEqual(['t2'])

    actor.send({ type: 'TRUCK_DONE', descriptorId: 't1', scaleId: 'sandbox-a' })

    expect(actor.getSnapshot().context.lanes['sandbox-a']).toBeNull() // raia livre na hora
    expect(actor.getSnapshot().context.queue).toEqual(['t2']) // t1 ainda não voltou

    await vi.advanceTimersByTimeAsync(800) // REQUEUE_DELAY_MS

    expect(actor.getSnapshot().context.queue).toEqual(['t2', 't1'])
    actor.stop()
  })
})

describe('yardMachine SET_CONFIG', () => {
  it('reduzir numLanes/numTrucks não reprovisiona nada', async () => {
    mockBootstrap([scale('sandbox-a'), scale('sandbox-b')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 2, numTrucks: 2 })

    actor.send({ type: 'SET_CONFIG', numLanes: 1 })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('ready')
    expect(actor.getSnapshot().context.numLanes).toBe(1)
    expect(Object.keys(actor.getSnapshot().context.lanes)).toHaveLength(2) // raias existentes não são removidas
    actor.stop()
  })

  it('aumentar numLanes reprovisiona só o delta de balanças novas', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    vi.mocked(provisionScales).mockResolvedValue([scale('sandbox-b'), scale('sandbox-c')])
    vi.mocked(provisionTrucks).mockResolvedValue([truck('t1', 'SBAAAAA')]) // sem caminhão novo

    actor.send({ type: 'SET_CONFIG', numLanes: 3 })
    await vi.advanceTimersByTimeAsync(0)

    expect(provisionScales).toHaveBeenCalledWith(2, BRANCH.id) // delta = 3 - 1
    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('ready')
    expect(Object.keys(snapshot.context.lanes).sort()).toEqual(['sandbox-a', 'sandbox-b', 'sandbox-c'])
    expect(snapshot.context.trucks).toHaveLength(1) // não duplicou o caminhão já existente
    actor.stop()
  })

  it('aumentar numTrucks spawna atores só pros caminhões genuinamente novos', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    vi.mocked(provisionScales).mockResolvedValue([])
    vi.mocked(provisionTrucks).mockResolvedValue([truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])

    actor.send({ type: 'SET_CONFIG', numTrucks: 2 })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.context.trucks.map((t) => t.truckId).sort()).toEqual(['t1', 't2'])
    expect(snapshot.context.queue).toEqual(['t1', 't2'])
    expect(snapshot.context.truckRefs.t2.getSnapshot().value).toBe('queued')
    actor.stop()
  })
})

describe('yardMachine SET_TRUCK_PROFILE', () => {
  it('atualiza o perfil de um caminhão específico sem afetar os outros', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 2 })

    actor.send({ type: 'SET_TRUCK_PROFILE', truckId: 't2', profile: 'normal' })
    await vi.advanceTimersByTimeAsync(0)

    const trucks = actor.getSnapshot().context.trucks
    expect(trucks.find((t) => t.truckId === 't1')?.profile).toBe('normal')
    expect(trucks.find((t) => t.truckId === 't2')?.profile).toBe('normal')
    actor.stop()
  })
})

describe('yardMachine RESET', () => {
  it('para todos os atores de caminhão e reboota do zero', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })
    const oldRef = actor.getSnapshot().context.truckRefs.t1

    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    actor.send({ type: 'RESET' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().value).toBe('ready')
    expect(oldRef.getSnapshot().status).toBe('stopped')
    actor.stop()
  })

  // LOG-020: um truck resetado no meio de uma passagem ainda segura uma
  // TransportTransaction OPEN no backend de verdade — sem cancelar aqui,
  // ela fica órfã e o próximo dispatch desse mesmo truck "SB..." esbarraria
  // num 409 (truckMachine se recupera sozinho disso, mas só reativamente).
  it('cancela a transaction OPEN de um caminhão parado no meio de uma passagem antes de resetar', async () => {
    vi.mocked(openTransportTransaction).mockResolvedValue({
      id: 'tx-mid-pass',
      truckId: 't1',
      grainTypeId: GRAIN_TYPE.id,
      branchId: BRANCH.id,
      status: 'OPEN',
      purchasePriceSnapshot: 1000,
      startedAt: new Date().toISOString(),
      finishedAt: null,
    })
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    actor.send({ type: 'DISPATCH_NEXT' })
    await vi.advanceTimersByTimeAsync(1200) // TRAVEL_MS -> openingTransaction
    await vi.advanceTimersByTimeAsync(0) // flush openTransportTransaction resolvido

    expect(actor.getSnapshot().context.truckRefs.t1.getSnapshot().context.transactionId).toBe('tx-mid-pass')

    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    actor.send({ type: 'RESET' })
    await vi.advanceTimersByTimeAsync(0)

    expect(cancelTransportTransaction).toHaveBeenCalledWith('tx-mid-pass')
    actor.stop()
  })

  it('não tenta cancelar nada para um caminhão ainda na fila (sem transactionId)', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })
    // t1 segue 'queued' — nunca foi despachado, nunca teve transactionId.

    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    actor.send({ type: 'RESET' })
    await vi.advanceTimersByTimeAsync(0)

    expect(cancelTransportTransaction).not.toHaveBeenCalled()
    actor.stop()
  })
})

// getScaleKey é exercitado indiretamente pelo teste de DISPATCH_NEXT acima
// (a chave precisa existir pra dispatchNext montar o DISPATCH) — teste
// direto só pra deixar explícito que é isso que trava o despacho sem chave.
describe('yardMachine dispatchNext sem apiKey conhecida', () => {
  it('não despacha se a balança não tem chave capturada (não deveria acontecer via bootstrap real)', async () => {
    vi.mocked(bootstrapSandbox).mockResolvedValue({
      branch: BRANCH,
      grainType: GRAIN_TYPE,
      scales: [scale('sandbox-no-key')],
      trucks: [truck('t1', 'SBAAAAA')],
    })
    const actor = createActor(yardMachine, { input: { numLanes: 1, numTrucks: 1 } }).start()
    await vi.advanceTimersByTimeAsync(0)
    expect(getScaleKey('sandbox-no-key')).toBeUndefined()

    actor.send({ type: 'DISPATCH_NEXT' })
    await vi.advanceTimersByTimeAsync(0)

    expect(actor.getSnapshot().context.lanes['sandbox-no-key']).toBeNull()
    expect(actor.getSnapshot().context.queue).toEqual(['t1'])
    actor.stop()
  })
})

describe('yardMachine RUN_CONCURRENCY_DEMO', () => {
  it('com raias e caminhões normal suficientes, despacha 2 de uma vez pra 2 balanças distintas', async () => {
    mockBootstrap(
      [scale('sandbox-a'), scale('sandbox-b')],
      [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB'), truck('t3', 'SBCCCCC')],
    )
    const actor = await bootAndReachReady({ numLanes: 2, numTrucks: 3 })

    actor.send({ type: 'RUN_CONCURRENCY_DEMO' })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('ready')
    expect(Object.values(snapshot.context.lanes).sort()).toEqual(['t1', 't2'])
    expect(snapshot.context.queue).toEqual(['t3'])
    actor.stop()
  })

  it('com menos de 2 raias, reprovisiona pra 2 e despacha o par sozinho assim que fica pronto', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 2 })

    vi.mocked(provisionScales).mockResolvedValue([scale('sandbox-b')])
    vi.mocked(provisionTrucks).mockResolvedValue([truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    setScaleKey('sandbox-b', 'key-sandbox-b') // provisionScales real seta a key (PR2); aqui está mockado, então simulamos o efeito

    actor.send({ type: 'RUN_CONCURRENCY_DEMO' })
    await vi.advanceTimersByTimeAsync(0) // reprovisiona (delta=1 balança)
    await vi.advanceTimersByTimeAsync(0) // volta pra "ready" e despacha o par sozinho (always)

    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('ready')
    expect(snapshot.context.numLanes).toBe(2)
    expect(Object.keys(snapshot.context.lanes).sort()).toEqual(['sandbox-a', 'sandbox-b'])
    expect(Object.values(snapshot.context.lanes).sort()).toEqual(['t1', 't2'])
    expect(snapshot.context.queue).toEqual([])
    actor.stop()
  })

  it('com só 1 caminhão normal disponível, despacha só esse (melhor esforço, nunca falha)', async () => {
    mockBootstrap([scale('sandbox-a'), scale('sandbox-b')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 2, numTrucks: 2 })
    actor.send({ type: 'SET_TRUCK_PROFILE', truckId: 't2', profile: 'noisy' }) // só t1 continua "normal"
    await vi.advanceTimersByTimeAsync(0)

    actor.send({ type: 'RUN_CONCURRENCY_DEMO' })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.context.lanes['sandbox-a']).toBe('t1')
    expect(snapshot.context.lanes['sandbox-b']).toBeNull() // não inventa um 2º caminhão pra preencher
    expect(snapshot.context.queue).toEqual(['t2'])
    actor.stop()
  })

  it('sem raia livre, não despacha nada (idempotente, não trava)', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 2 })
    actor.send({ type: 'DISPATCH_NEXT' }) // ocupa a única raia com t1
    await vi.advanceTimersByTimeAsync(0)
    expect(actor.getSnapshot().context.lanes['sandbox-a']).toBe('t1')

    actor.send({ type: 'RUN_CONCURRENCY_DEMO' })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('ready')
    expect(snapshot.context.lanes['sandbox-a']).toBe('t1') // inalterado
    expect(snapshot.context.queue).toEqual(['t2'])
    actor.stop()
  })
})

describe('yardMachine branchName', () => {
  it('guarda o nome da filial no bootstrap, só pra exibição', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    expect(actor.getSnapshot().context.branchName).toBe(BRANCH.name)
    actor.stop()
  })
})

describe('yardMachine ENQUEUE_RANDOM_TRUCKS', () => {
  it('cresce a frota em `count`, sorteia um perfil pra cada caminhão novo e enfileira todos', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 1 })

    vi.mocked(provisionScales).mockResolvedValue([])
    vi.mocked(provisionTrucks).mockResolvedValue([
      truck('t1', 'SBAAAAA'),
      truck('t2', 'SBBBBBB'),
      truck('t3', 'SBCCCCC'),
      truck('t4', 'SBDDDDD'),
    ])

    actor.send({ type: 'ENQUEUE_RANDOM_TRUCKS', count: 3 })
    await vi.advanceTimersByTimeAsync(0)

    const snapshot = actor.getSnapshot()
    expect(snapshot.value).toBe('ready')
    expect(snapshot.context.numTrucks).toBe(4) // 1 original + 3 novos
    expect(snapshot.context.pendingRandomEnqueueCount).toBe(0) // limpo depois de aplicar
    expect(snapshot.context.trucks).toHaveLength(4)
    expect(snapshot.context.queue.sort()).toEqual(['t1', 't2', 't3', 't4'])
    // t1 já existia (era 'normal') — só os 3 novos podem ter ganhado perfil sorteado.
    expect(snapshot.context.trucks.find((t) => t.truckId === 't1')?.profile).toBe('normal')
    expect(snapshot.context.truckRefs.t2.getSnapshot().value).toBe('queued')
    expect(snapshot.context.truckRefs.t3.getSnapshot().value).toBe('queued')
    expect(snapshot.context.truckRefs.t4.getSnapshot().value).toBe('queued')
    actor.stop()
  })

  it('não mexe no perfil de caminhões já existentes, só nos novos desta reprovisão', async () => {
    mockBootstrap([scale('sandbox-a')], [truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB')])
    const actor = await bootAndReachReady({ numLanes: 1, numTrucks: 2 })
    actor.send({ type: 'SET_TRUCK_PROFILE', truckId: 't1', profile: 'noisy' })
    await vi.advanceTimersByTimeAsync(0)

    vi.mocked(provisionScales).mockResolvedValue([])
    vi.mocked(provisionTrucks).mockResolvedValue([truck('t1', 'SBAAAAA'), truck('t2', 'SBBBBBB'), truck('t3', 'SBCCCCC')])

    actor.send({ type: 'ENQUEUE_RANDOM_TRUCKS', count: 1 })
    await vi.advanceTimersByTimeAsync(0)

    const trucks = actor.getSnapshot().context.trucks
    expect(trucks.find((t) => t.truckId === 't1')?.profile).toBe('noisy') // preservado
    actor.stop()
  })
})
