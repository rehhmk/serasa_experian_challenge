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
  grainTypeId: string | null
  scales: ScaleSummary[]
  trucks: YardTruck[]
  truckRefs: Record<string, ActorRefFrom<typeof truckMachine>>
  /** scaleId -> truckId ocupando a raia, ou null se livre. */
  lanes: Record<string, string | null>
  /** truckIds aguardando raia livre, em ordem de chegada. */
  queue: string[]
  bootstrapError: string | null
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

interface BootstrapOutput {
  branchId: string
  grainTypeId: string
  scales: ScaleSummary[]
  trucks: Truck[]
}

interface ReprovisionOutput {
  scales: ScaleSummary[]
  trucks: Truck[]
}

function toYardTrucks(trucks: Truck[]): YardTruck[] {
  return trucks.map((t) => ({ truckId: t.id, plate: t.plate, tareWeightKg: t.tareWeightKg, profile: 'normal' }))
}

function lanesFor(scales: ScaleSummary[]): Record<string, string | null> {
  return Object.fromEntries(scales.map((s) => [s.id, null]))
}

function hasQueuedTruckAndFreeLane(context: YardContext): boolean {
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
  },
}).createMachine({
  id: 'yard',
  context: ({ input }) => ({
    numLanes: input.numLanes,
    numTrucks: input.numTrucks,
    auto: false,
    branchId: null,
    grainTypeId: null,
    scales: [],
    trucks: [],
    truckRefs: {},
    lanes: {},
    queue: [],
    bootstrapError: null,
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
      },
      always: { guard: 'autoCanDispatch', actions: 'dispatchNext' },
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
            const newTrucks = toYardTrucks(event.output.trucks.filter((t) => !existingTruckIds.has(t.id)))

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
