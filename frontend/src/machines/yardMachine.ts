import { assign, enqueueActions, fromPromise, setup, type ActorRefFrom } from 'xstate'
import { bootstrapSandbox, provisionScales, provisionTrucks } from '../api/bootstrap'
import { getScaleKey } from '../api/scaleKeyStore'
import type { ScaleSummary, Truck } from '../api/types'
import type { TruckProfileName } from '../simulation/readingProfiles/types'
import { truckMachine } from './truckMachine'

// Pausa visível entre um caminhão sair da balança e voltar pro fim da fila —
// sem isso o modo "Auto" pareceria instantâneo/teleportado.
const REQUEUE_DELAY_MS = 800

export interface YardTruck {
  truckId: string
  plate: string
  tareWeightKg: number
  profile: TruckProfileName
}

export interface YardContext {
  numLanes: number
  numTrucks: number
  auto: boolean
  branchId: string | null
  /** Só pra exibir nos cards de raia (ex: "SORRISO-MT") — não usado em nenhuma chamada de API. */
  branchName: string | null
  grainTypeId: string | null
  scales: ScaleSummary[]
  trucks: YardTruck[]
  truckRefs: Record<string, ActorRefFrom<typeof truckMachine>>
  /** scaleId -> truckId ocupando a raia, ou null se livre. */
  lanes: Record<string, string | null>
  /** truckIds aguardando raia livre, em ordem de chegada. */
  queue: string[]
  bootstrapError: string | null
  /** RUN_CONCURRENCY_DEMO pediu numLanes>=2 antes de poder despachar o par — ver `ready.always`. */
  pendingConcurrencyDemo: boolean
  /** ENQUEUE_RANDOM_TRUCKS: quantos dos caminhões recém-provisionados devem ganhar perfil aleatório. */
  pendingRandomEnqueueCount: number
}

export interface YardInput {
  numLanes: number
  numTrucks: number
}

export type YardEvent =
  | { type: 'TOGGLE_AUTO' }
  | { type: 'SET_CONFIG'; numLanes?: number; numTrucks?: number }
  | { type: 'SET_TRUCK_PROFILE'; truckId: string; profile: TruckProfileName }
  | { type: 'DISPATCH_NEXT' }
  | { type: 'TRUCK_DONE'; descriptorId: string; scaleId: string | null }
  | { type: 'REQUEUE'; descriptorId: string }
  | { type: 'RETRY' }
  | { type: 'RESET' }
  // Isolamento por balança (ScaleSessionManager, ConcurrentHashMap<scaleId,...>)
  // não depende do formato das leituras — não existe um "perfil de
  // concorrência". O preset só despacha 2 caminhões `normal` pra 2 balanças
  // diferentes ao mesmo tempo, garantindo capacidade primeiro se preciso.
  | { type: 'RUN_CONCURRENCY_DEMO' }
  // Batch: cresce a frota em `count` e devolve os novos caminhões já na fila,
  // cada um com um perfil sorteado — "acelera" o sandbox sem precisar
  // configurar um por um. Sempre cresce (nunca reaproveita caminhão
  // existente), então sempre passa por reprovisioning de verdade.
  | { type: 'ENQUEUE_RANDOM_TRUCKS'; count: number }

interface BootstrapOutput {
  branchId: string
  branchName: string
  grainTypeId: string
  scales: ScaleSummary[]
  trucks: Truck[]
}

interface ReprovisionOutput {
  scales: ScaleSummary[]
  trucks: Truck[]
}

const ALL_TRUCK_PROFILES: TruckProfileName[] = ['normal', 'noisy', 'slowEntry', 'duplicateRetry']

function randomTruckProfile(): TruckProfileName {
  return ALL_TRUCK_PROFILES[Math.floor(Math.random() * ALL_TRUCK_PROFILES.length)]
}

function toYardTrucks(trucks: Truck[]): YardTruck[] {
  return trucks.map((t) => ({ truckId: t.id, plate: t.plate, tareWeightKg: t.tareWeightKg, profile: 'normal' }))
}

function lanesFor(scales: ScaleSummary[]): Record<string, string | null> {
  return Object.fromEntries(scales.map((s) => [s.id, null]))
}

// Exportado pra UI reusar (ex: desabilitar o botão de despacho manual)
// sem duplicar a mesma checagem que o guard já faz.
export function hasQueuedTruckAndFreeLane(context: YardContext): boolean {
  return context.queue.length > 0 && Object.values(context.lanes).some((occupant) => occupant === null)
}

function firstFreeLane(lanes: Record<string, string | null>): string | undefined {
  return Object.entries(lanes).find(([, occupant]) => occupant === null)?.[0]
}

export const yardMachine = setup({
  types: {
    context: {} as YardContext,
    events: {} as YardEvent,
    input: {} as YardInput,
  },
  actors: {
    truck: truckMachine,

    bootstrap: fromPromise<BootstrapOutput, { numLanes: number; numTrucks: number }>(async ({ input }) => {
      const result = await bootstrapSandbox(input)
      return {
        branchId: result.branch.id,
        branchName: result.branch.name,
        grainTypeId: result.grainType.id,
        scales: result.scales,
        trucks: result.trucks,
      }
    }),

    // Balanças: sempre delta (provisionScales nunca reaproveita, ver bootstrap.ts).
    // Caminhões: sempre o total desejado (provisionTrucks reaproveita "SB…"
    // existentes) — dispatchNext/reprovisioning filtram o que já tem ator vivo.
    reprovision: fromPromise<ReprovisionOutput, { branchId: string; scaleDelta: number; numTrucks: number }>(
      async ({ input }) => {
        const [scales, trucks] = await Promise.all([
          input.scaleDelta > 0 ? provisionScales(input.scaleDelta, input.branchId) : Promise.resolve([]),
          provisionTrucks(input.numTrucks),
        ])
        return { scales, trucks }
      },
    ),
  },
  guards: {
    hasQueuedTruckAndFreeLane: ({ context }) => hasQueuedTruckAndFreeLane(context),
    autoCanDispatch: ({ context }) => context.auto && hasQueuedTruckAndFreeLane(context),
    needsMoreCapacity: ({ context, event }) => {
      if (event.type !== 'SET_CONFIG') {
        return false
      }
      const moreLanes = event.numLanes !== undefined && event.numLanes > context.numLanes
      const moreTrucks = event.numTrucks !== undefined && event.numTrucks > context.numTrucks
      return moreLanes || moreTrucks
    },
  },
  actions: {
    // Único lugar que fala com um caminhão específico (sendTo) E atualiza
    // raia/fila (assign) — enqueueActions evita side effect dentro de assign.
    dispatchNext: enqueueActions(({ context, enqueue }) => {
      const truckId = context.queue[0]
      const scaleId = firstFreeLane(context.lanes)
      if (!truckId || !scaleId) {
        return
      }
      const truck = context.trucks.find((t) => t.truckId === truckId)
      const ref = context.truckRefs[truckId]
      const apiKey = getScaleKey(scaleId)
      if (!truck || !ref || !apiKey) {
        return
      }
      enqueue.sendTo(ref, { type: 'DISPATCH', scaleId, apiKey, profile: truck.profile })
      enqueue.assign({
        lanes: { ...context.lanes, [scaleId]: truckId },
        queue: context.queue.slice(1),
      })
    }),

    // Despacha até 2 caminhões de perfil "normal" pra 2 balanças livres
    // distintas, na mesma leva de ações — mais fiel a "ao mesmo tempo" do
    // que 2 DISPATCH_NEXT manuais em sequência (que dependem de quem chama).
    // Melhor esforço: despacha o que der (0, 1 ou 2), nunca falha.
    dispatchConcurrencyPair: enqueueActions(({ context, enqueue }) => {
      enqueue.assign({ pendingConcurrencyDemo: false })

      const freeLaneIds = Object.entries(context.lanes)
        .filter(([, occupant]) => occupant === null)
        .map(([scaleId]) => scaleId)
      const normalQueuedTruckIds = context.queue.filter(
        (truckId) => context.trucks.find((t) => t.truckId === truckId)?.profile === 'normal',
      )
      const pairSize = Math.min(2, freeLaneIds.length, normalQueuedTruckIds.length)

      const dispatchedTruckIds: string[] = []
      const nextLanes = { ...context.lanes }
      for (let i = 0; i < pairSize; i++) {
        const truckId = normalQueuedTruckIds[i]
        const scaleId = freeLaneIds[i]
        const truck = context.trucks.find((t) => t.truckId === truckId)
        const ref = context.truckRefs[truckId]
        const apiKey = getScaleKey(scaleId)
        if (!truck || !ref || !apiKey) {
          continue
        }
        enqueue.sendTo(ref, { type: 'DISPATCH', scaleId, apiKey, profile: truck.profile })
        nextLanes[scaleId] = truckId
        dispatchedTruckIds.push(truckId)
      }

      if (dispatchedTruckIds.length > 0) {
        enqueue.assign({
          lanes: nextLanes,
          queue: context.queue.filter((id) => !dispatchedTruckIds.includes(id)),
        })
      }
    }),
  },
}).createMachine({
  id: 'yard',
  context: ({ input }) => ({
    numLanes: input.numLanes,
    numTrucks: input.numTrucks,
    auto: false,
    branchId: null,
    branchName: null,
    grainTypeId: null,
    scales: [],
    trucks: [],
    truckRefs: {},
    lanes: {},
    queue: [],
    bootstrapError: null,
    pendingConcurrencyDemo: false,
    pendingRandomEnqueueCount: 0,
  }),
  initial: 'bootstrapping',
  states: {
    bootstrapping: {
      invoke: {
        src: 'bootstrap',
        input: ({ context }) => ({ numLanes: context.numLanes, numTrucks: context.numTrucks }),
        onDone: {
          target: 'ready',
          actions: assign(({ event, spawn }) => {
            const trucks = toYardTrucks(event.output.trucks)
            const truckRefs: Record<string, ActorRefFrom<typeof truckMachine>> = {}
            for (const truck of trucks) {
              truckRefs[truck.truckId] = spawn('truck', {
                id: truck.truckId,
                input: {
                  descriptorId: truck.truckId,
                  plate: truck.plate,
                  truckId: truck.truckId,
                  tareWeightKg: truck.tareWeightKg,
                  branchId: event.output.branchId,
                  grainTypeId: event.output.grainTypeId,
                  profile: truck.profile,
                },
              })
            }
            return {
              branchId: event.output.branchId,
              branchName: event.output.branchName,
              grainTypeId: event.output.grainTypeId,
              scales: event.output.scales,
              trucks,
              truckRefs,
              lanes: lanesFor(event.output.scales),
              queue: trucks.map((t) => t.truckId),
              bootstrapError: null,
            }
          }),
        },
        onError: {
          target: 'bootstrapError',
          actions: assign({ bootstrapError: ({ event }) => String(event.error) }),
        },
      },
    },
    bootstrapError: {
      on: { RETRY: 'bootstrapping' },
    },
    ready: {
      on: {
        TOGGLE_AUTO: {
          actions: assign({ auto: ({ context }) => !context.auto }),
        },
        SET_TRUCK_PROFILE: {
          actions: assign({
            trucks: ({ context, event }) =>
              context.trucks.map((t) => (t.truckId === event.truckId ? { ...t, profile: event.profile } : t)),
          }),
        },
        SET_CONFIG: [
          {
            guard: 'needsMoreCapacity',
            target: 'reprovisioning',
            actions: assign(({ context, event }) => ({
              numLanes: event.numLanes ?? context.numLanes,
              numTrucks: event.numTrucks ?? context.numTrucks,
            })),
          },
          {
            actions: assign(({ context, event }) => ({
              numLanes: event.numLanes ?? context.numLanes,
              numTrucks: event.numTrucks ?? context.numTrucks,
            })),
          },
        ],
        DISPATCH_NEXT: {
          guard: 'hasQueuedTruckAndFreeLane',
          actions: 'dispatchNext',
        },
        // Libera a raia e sempre devolve o caminhão pro fim da fila depois de
        // uma pausa visível — a frota é fixa (spawnada uma vez no bootstrap),
        // então um caminhão que terminou volta a ficar disponível pra
        // despacho, manual ou automático. `auto` só decide se o pátio se
        // despacha sozinho (guard `autoCanDispatch` abaixo) — não se o
        // caminhão pode voltar pra fila.
        TRUCK_DONE: {
          actions: enqueueActions(({ context, event, enqueue }) => {
            if (event.scaleId) {
              enqueue.assign({ lanes: { ...context.lanes, [event.scaleId]: null } })
            }
            enqueue.sendTo(
              ({ self }) => self,
              { type: 'REQUEUE', descriptorId: event.descriptorId },
              { delay: REQUEUE_DELAY_MS },
            )
          }),
        },
        REQUEUE: {
          actions: assign({ queue: ({ context, event }) => [...context.queue, event.descriptorId] }),
        },
        RESET: {
          target: 'bootstrapping',
          actions: enqueueActions(({ context, enqueue }) => {
            for (const ref of Object.values(context.truckRefs)) {
              enqueue.stopChild(ref)
            }
          }),
        },
        RUN_CONCURRENCY_DEMO: [
          {
            guard: ({ context }) => context.numLanes < 2,
            target: 'reprovisioning',
            actions: assign({ numLanes: 2, pendingConcurrencyDemo: true }),
          },
          { actions: 'dispatchConcurrencyPair' },
        ],
        ENQUEUE_RANDOM_TRUCKS: {
          target: 'reprovisioning',
          actions: assign(({ context, event }) => ({
            numTrucks: context.numTrucks + event.count,
            pendingRandomEnqueueCount: event.count,
          })),
        },
      },
      always: [
        { guard: ({ context }) => context.pendingConcurrencyDemo, actions: 'dispatchConcurrencyPair' },
        { guard: 'autoCanDispatch', actions: 'dispatchNext' },
      ],
    },
    reprovisioning: {
      invoke: {
        src: 'reprovision',
        input: ({ context }) => ({
          branchId: context.branchId!,
          scaleDelta: context.numLanes - context.scales.length,
          numTrucks: context.numTrucks,
        }),
        onDone: {
          target: 'ready',
          actions: assign(({ context, event, spawn }) => {
            const newScales = event.output.scales
            const existingTruckIds = new Set(context.trucks.map((t) => t.truckId))
            const newTrucksRaw = toYardTrucks(event.output.trucks.filter((t) => !existingTruckIds.has(t.id)))
            // ENQUEUE_RANDOM_TRUCKS pediu perfis sorteados pros caminhões que
            // essa reprovisão específica está criando — RUN_CONCURRENCY_DEMO
            // e um SET_CONFIG puro não mexem nisso (fica 0), então o default
            // 'normal' de toYardTrucks vale como antes.
            const newTrucks =
              context.pendingRandomEnqueueCount > 0
                ? newTrucksRaw.map((t) => ({ ...t, profile: randomTruckProfile() }))
                : newTrucksRaw

            const newTruckRefs: Record<string, ActorRefFrom<typeof truckMachine>> = {}
            for (const truck of newTrucks) {
              newTruckRefs[truck.truckId] = spawn('truck', {
                id: truck.truckId,
                input: {
                  descriptorId: truck.truckId,
                  plate: truck.plate,
                  truckId: truck.truckId,
                  tareWeightKg: truck.tareWeightKg,
                  branchId: context.branchId!,
                  grainTypeId: context.grainTypeId!,
                  profile: truck.profile,
                },
              })
            }

            return {
              scales: [...context.scales, ...newScales],
              trucks: [...context.trucks, ...newTrucks],
              truckRefs: { ...context.truckRefs, ...newTruckRefs },
              lanes: { ...context.lanes, ...lanesFor(newScales) },
              queue: [...context.queue, ...newTrucks.map((t) => t.truckId)],
              bootstrapError: null,
              pendingRandomEnqueueCount: 0,
            }
          }),
        },
        onError: {
          target: 'ready',
          actions: assign({ bootstrapError: ({ event }) => String(event.error) }),
        },
      },
    },
  },
})
