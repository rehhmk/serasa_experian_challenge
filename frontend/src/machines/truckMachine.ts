import { assign, fromCallback, fromPromise, sendParent, setup } from 'xstate'
import { postReading } from '../api/readings'
import { getWeighingBook } from '../api/reports'
import { openTransportTransaction } from '../api/transportTransactions'
import type { WeighingBookItem } from '../api/types'
import { createNormalProfile } from '../simulation/readingProfiles/normal'
import type { ReadingGenerator, TruckProfileName } from '../simulation/readingProfiles/types'
import { STABILIZATION_CONFIG } from '../simulation/stabilizationConfig'
import {
  advancePredictor,
  INITIAL_PREDICTOR_STATE,
  type PredictorState,
  type WeightSample,
} from '../simulation/stabilizationPredictor'

// Cadência real do protocolo (CLAUDE.md §4: ESP32 manda a cada ~100ms).
const READING_INTERVAL_MS = 100
const TRAVEL_MS = 1200
const CONFIRM_RETRY_DELAY_MS = 400
const MAX_CONFIRM_ATTEMPTS = 5
const UNCONFIRMED_PAUSE_MS = 2000
const RECORDED_PAUSE_MS = 800
const LEAVE_MS = 600

export interface TruckInput {
  descriptorId: string
  plate: string
  truckId: string
  tareWeightKg: number
  branchId: string
  grainTypeId: string
  profile: TruckProfileName
}

export interface TruckContext extends TruckInput {
  scaleId: string | null
  apiKey: string | null
  transactionId: string | null
  passStartedAtMs: number | null
  generator: ReadingGenerator | null
  samples: WeightSample[]
  predictor: PredictorState
  confirmedWeighing: WeighingBookItem | null
  confirmAttempts: number
  lastError: string | null
}

export type TruckEvent =
  | { type: 'DISPATCH'; scaleId: string; apiKey: string }
  | { type: 'RAW_READING'; sample: WeightSample }
  | { type: 'RETRY' }

function randomNetWeightKg(): number {
  return 15000 + Math.random() * 10000
}

function createGeneratorFor(profile: TruckProfileName, tareWeightKg: number): ReadingGenerator {
  switch (profile) {
    case 'normal':
      return createNormalProfile({ targetWeightKg: tareWeightKg + randomNetWeightKg() })
  }
}

function nextWindow(context: TruckContext, sample: WeightSample): WeightSample[] {
  return [...context.samples, sample].slice(-STABILIZATION_CONFIG.maxWindowSamples)
}

function nextPredictor(context: TruckContext, sample: WeightSample): PredictorState {
  return advancePredictor(context.predictor, nextWindow(context, sample), sample.timestampMs)
}

function freshPassContext(): Pick<
  TruckContext,
  'transactionId' | 'passStartedAtMs' | 'generator' | 'samples' | 'predictor' | 'confirmedWeighing' | 'confirmAttempts' | 'lastError'
> {
  return {
    transactionId: null,
    passStartedAtMs: null,
    generator: null,
    samples: [],
    predictor: INITIAL_PREDICTOR_STATE,
    confirmedWeighing: null,
    confirmAttempts: 0,
    lastError: null,
  }
}

// Transição compartilhada entre collecting/stabilizing: o predictor (não o
// XState) já decide qual é o próximo estado a cada leitura — mirror direto de
// ScaleSession.addReading, que recalcula do zero a cada chamada em vez de
// ramificar no estado atual. As 3 substates existem só para o badge da UI
// (Coletando/Estabilizando/Estável), a lógica é idêntica nas 3.
const rawReadingTransitions = [
  { guard: 'nextIsStable', actions: 'applyReading', target: 'stable' },
  { guard: 'nextIsStabilizing', actions: 'applyReading', target: 'stabilizing' },
  { actions: 'applyReading', target: 'collecting' },
] as const

export const truckMachine = setup({
  types: {
    context: {} as TruckContext,
    events: {} as TruckEvent,
    input: {} as TruckInput,
  },
  actors: {
    openTransaction: fromPromise<
      Awaited<ReturnType<typeof openTransportTransaction>>,
      { truckId: string; grainTypeId: string; branchId: string }
    >(({ input }) => openTransportTransaction(input)),

    // Um invoke por passagem pela balança, compartilhado entre collecting/
    // stabilizing/stable (vive em onScale, não nos filhos) — só é derrubado
    // quando a máquina sai de onScale de vez.
    readingLoop: fromCallback<
      TruckEvent,
      { scaleId: string; apiKey: string; plate: string; generator: ReadingGenerator; intervalMs: number }
    >(({ input, sendBack }) => {
      const startedAtMs = Date.now()
      const timer = setInterval(() => {
        const elapsedMs = Date.now() - startedAtMs
        const weightKg = input.generator.next(elapsedMs)
        void postReading({ scaleId: input.scaleId, apiKey: input.apiKey, plate: input.plate, weightKg })
        sendBack({ type: 'RAW_READING', sample: { timestampMs: Date.now(), weightKg } })
      }, input.intervalMs)
      return () => clearInterval(timer)
    }),

    // Única forma de saber se a Weighing real foi persistida — o POST de
    // leitura nunca informa isso (ver readings.ts / ScaleReadingController).
    fetchConfirmation: fromPromise<WeighingBookItem | null, { scaleId: string; plate: string; fromMs: number }>(
      async ({ input }) => {
        const envelope = await getWeighingBook({
          scaleId: input.scaleId,
          plate: input.plate,
          from: new Date(input.fromMs).toISOString(),
          to: new Date().toISOString(),
        })
        return envelope.data[0] ?? null
      },
    ),
  },
  guards: {
    nextIsStable: ({ context, event }) =>
      event.type === 'RAW_READING' && nextPredictor(context, event.sample).status === 'STABLE',
    nextIsStabilizing: ({ context, event }) =>
      event.type === 'RAW_READING' && nextPredictor(context, event.sample).status === 'STABILIZING',
    canRetryConfirmation: ({ context }) => context.confirmAttempts < MAX_CONFIRM_ATTEMPTS,
  },
  actions: {
    applyReading: assign(({ context, event }) => {
      if (event.type !== 'RAW_READING') {
        return {}
      }
      const samples = nextWindow(context, event.sample)
      const predictor = advancePredictor(context.predictor, samples, event.sample.timestampMs)
      return { samples, predictor }
    }),
  },
}).createMachine({
  id: 'truck',
  context: ({ input }) => ({
    ...input,
    scaleId: null,
    apiKey: null,
    ...freshPassContext(),
  }),
  initial: 'queued',
  states: {
    queued: {
      on: {
        DISPATCH: {
          target: 'travelling',
          actions: assign(({ event }) => ({
            scaleId: event.scaleId,
            apiKey: event.apiKey,
            ...freshPassContext(),
          })),
        },
      },
    },
    travelling: {
      after: { [TRAVEL_MS]: 'openingTransaction' },
    },
    openingTransaction: {
      invoke: {
        src: 'openTransaction',
        input: ({ context }) => ({
          truckId: context.truckId,
          grainTypeId: context.grainTypeId,
          branchId: context.branchId,
        }),
        onDone: {
          target: 'onScale',
          actions: assign(({ context, event }) => ({
            transactionId: event.output.id,
            passStartedAtMs: Date.now(),
            generator: createGeneratorFor(context.profile, context.tareWeightKg),
          })),
        },
        onError: {
          target: 'transactionError',
          actions: assign({ lastError: ({ event }) => String(event.error) }),
        },
      },
    },
    onScale: {
      invoke: {
        id: 'readingLoop',
        src: 'readingLoop',
        input: ({ context }) => ({
          scaleId: context.scaleId!,
          apiKey: context.apiKey!,
          plate: context.plate,
          generator: context.generator!,
          intervalMs: READING_INTERVAL_MS,
        }),
      },
      initial: 'collecting',
      states: {
        collecting: { on: { RAW_READING: rawReadingTransitions } },
        stabilizing: { on: { RAW_READING: rawReadingTransitions } },
        stable: { always: '#truck.confirming' },
      },
    },
    confirming: {
      invoke: {
        src: 'fetchConfirmation',
        input: ({ context }) => ({
          scaleId: context.scaleId!,
          plate: context.plate,
          fromMs: context.passStartedAtMs!,
        }),
        onDone: [
          {
            guard: ({ event }) => event.output != null,
            target: 'recorded',
            actions: assign({ confirmedWeighing: ({ event }) => event.output }),
          },
          {
            target: 'confirmRetryWait',
            actions: assign({ confirmAttempts: ({ context }) => context.confirmAttempts + 1 }),
          },
        ],
        onError: {
          target: 'confirmRetryWait',
          actions: assign({
            confirmAttempts: ({ context }) => context.confirmAttempts + 1,
            lastError: ({ event }) => String(event.error),
          }),
        },
      },
    },
    confirmRetryWait: {
      after: {
        [CONFIRM_RETRY_DELAY_MS]: [
          { guard: 'canRetryConfirmation', target: 'confirming' },
          { target: 'unconfirmed' },
        ],
      },
    },
    // A confirmação não bateu depois de MAX_CONFIRM_ATTEMPTS — fica visível
    // na UI como aviso (PR6) em vez de travar o caminhão indefinidamente.
    unconfirmed: {
      after: { [UNCONFIRMED_PAUSE_MS]: 'leaving' },
    },
    recorded: {
      after: { [RECORDED_PAUSE_MS]: 'leaving' },
    },
    transactionError: {
      on: { RETRY: 'openingTransaction' },
    },
    leaving: {
      entry: sendParent(({ context }) => ({
        type: 'TRUCK_DONE',
        descriptorId: context.descriptorId,
        scaleId: context.scaleId,
      })),
      exit: assign({ scaleId: null, apiKey: null }),
      after: { [LEAVE_MS]: 'queued' },
    },
  },
})
