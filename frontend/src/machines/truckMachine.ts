import { assign, fromCallback, fromPromise, sendParent, setup } from 'xstate'
import { postReading } from '../api/readings'
import { getWeighingBook } from '../api/reports'
import { openTransportTransaction } from '../api/transportTransactions'
import type { WeighingBookItem } from '../api/types'
import { createDuplicateRetryProfile } from '../simulation/readingProfiles/duplicateRetry'
import { createNoisyProfile } from '../simulation/readingProfiles/noisy'
import { createNormalProfile } from '../simulation/readingProfiles/normal'
import { createSlowEntryProfile } from '../simulation/readingProfiles/slowEntry'
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
// Orçamento de confirmação calibrado pro backend real (Render free tier),
// não localhost: medido ~300-400ms de round-trip por request contra a
// instância publicada, contra ~1ms em dev local. O predictor local decide
// STABLE de forma otimista assim que ENVIA a leitura que fecha a janela
// (postReading é fire-and-forget, não é aguardado) — a confirmação real só
// existe depois que essa mesma leitura chega no servidor, o backend também
// decide STABLE e CompleteWeighingUseCase comita. 5×400ms (~1.6s) bastava
// contra localhost; contra a internet, sobrava "não confirmado" mesmo em
// pesagens que iam completar poucos segundos depois.
const CONFIRM_RETRY_DELAY_MS = 600
const MAX_CONFIRM_ATTEMPTS = 10
const UNCONFIRMED_PAUSE_MS = 2000
const RECORDED_PAUSE_MS = 800
const LEAVE_MS = 600
// Margem contra deriva de relógio cliente/servidor no filtro `from` da
// confirmação (WeighingRepository: `recordedAt >= from`) — scaleId+plate já
// escopam a busca a esta passagem específica, então alargar a janela não
// arrisca pegar uma Weighing errada, só protege contra o relógio do
// navegador estar um pouco à frente do servidor.
const CONFIRM_FROM_SAFETY_MARGIN_MS = 10_000
// Tempo esvaziando antes de reabrir uma 2ª transação (duplicateRetry, LOG-008)
// — emptyDurationMs real do backend + margem pra jitter de rede.
const EMPTY_MARGIN_MS = STABILIZATION_CONFIG.emptyDurationMs + 300

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
  /** Qual passagem está em curso — só duplicateRetry chega a 2 (LOG-008). */
  passIndex: 1 | 2
  generator: ReadingGenerator | null
  samples: WeightSample[]
  predictor: PredictorState
  confirmedWeighing: WeighingBookItem | null
  confirmAttempts: number
  lastError: string | null
}

export type TruckEvent =
  | { type: 'DISPATCH'; scaleId: string; apiKey: string; profile?: TruckProfileName }
  | { type: 'RAW_READING'; sample: WeightSample }
  | { type: 'RETRY' }

function randomNetWeightKg(): number {
  return 15000 + Math.random() * 10000
}

function createGeneratorFor(profile: TruckProfileName, tareWeightKg: number): ReadingGenerator {
  const targetWeightKg = tareWeightKg + randomNetWeightKg()
  switch (profile) {
    case 'normal':
      return createNormalProfile({ targetWeightKg })
    case 'noisy':
      return createNoisyProfile({ targetWeightKg })
    case 'slowEntry':
      return createSlowEntryProfile({ targetWeightKg })
    case 'duplicateRetry':
      return createDuplicateRetryProfile({ targetWeightKg })
  }
}

function nextWindow(context: TruckContext, sample: WeightSample): WeightSample[] {
  return [...context.samples, sample].slice(-STABILIZATION_CONFIG.maxWindowSamples)
}

function nextPredictor(context: TruckContext, sample: WeightSample): PredictorState {
  return advancePredictor(context.predictor, nextWindow(context, sample), sample.timestampMs)
}

function freshPassContext(passIndex: 1 | 2 = 1): Pick<
  TruckContext,
  | 'transactionId'
  | 'passStartedAtMs'
  | 'passIndex'
  | 'generator'
  | 'samples'
  | 'predictor'
  | 'confirmedWeighing'
  | 'confirmAttempts'
  | 'lastError'
> {
  return {
    transactionId: null,
    passStartedAtMs: null,
    passIndex,
    generator: null,
    samples: [],
    predictor: INITIAL_PREDICTOR_STATE,
    confirmedWeighing: null,
    confirmAttempts: 0,
    lastError: null,
  }
}

// Usado nos dois onDone de abertura de transação (1ª e 2ª passagem): preserva
// o passIndex atual do contexto (freshPassContext() já cuida disso) e só
// preenche o que acabou de ficar disponível.
function nextPassContext(
  context: TruckContext,
  transactionId: string,
): Pick<
  TruckContext,
  'transactionId' | 'passStartedAtMs' | 'passIndex' | 'generator' | 'samples' | 'predictor' | 'confirmedWeighing' | 'confirmAttempts' | 'lastError'
> {
  return {
    ...freshPassContext(context.passIndex),
    transactionId,
    passStartedAtMs: Date.now(),
    generator: createGeneratorFor(context.profile, context.tareWeightKg),
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

    // Só duplicateRetry usa isto (LOG-008): faz o peso "cair" de verdade no
    // backend real (weight <= emptyThresholdKg) até a sessão da balança
    // resetar (emptyDurationMs) — só então a 2ª transação pode abrir sem
    // colidir com a 1ª (IncorrectResultSizeDataAccessException confirmado em
    // CompleteWeighingUseCase.resolveOpenTransaction).
    emptyLoop: fromCallback<
      TruckEvent,
      { scaleId: string; apiKey: string; plate: string; intervalMs: number }
    >(({ input }) => {
      const timer = setInterval(() => {
        void postReading({ scaleId: input.scaleId, apiKey: input.apiKey, plate: input.plate, weightKg: 50 })
      }, input.intervalMs)
      return () => clearInterval(timer)
    }),
  },
  guards: {
    nextIsStable: ({ context, event }) =>
      event.type === 'RAW_READING' && nextPredictor(context, event.sample).status === 'STABLE',
    nextIsStabilizing: ({ context, event }) =>
      event.type === 'RAW_READING' && nextPredictor(context, event.sample).status === 'STABILIZING',
    canRetryConfirmation: ({ context }) => context.confirmAttempts < MAX_CONFIRM_ATTEMPTS,
    isFirstPassOfDuplicateRetry: ({ context }) => context.profile === 'duplicateRetry' && context.passIndex === 1,
    isSecondPass: ({ context }) => context.passIndex === 2,
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
          actions: assign(({ context, event }) => ({
            scaleId: event.scaleId,
            apiKey: event.apiKey,
            profile: event.profile ?? context.profile,
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
          actions: assign(({ context, event }) => nextPassContext(context, event.output.id)),
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
          fromMs: context.passStartedAtMs! - CONFIRM_FROM_SAFETY_MARGIN_MS,
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
      after: {
        [RECORDED_PAUSE_MS]: [
          { guard: 'isFirstPassOfDuplicateRetry', target: 'emptying', actions: assign({ passIndex: 2 }) },
          { target: 'leaving' },
        ],
      },
    },
    // Só a 1ª passagem do duplicateRetry passa por aqui — espelha o
    // gate real de peso vazio do backend (emptyThresholdKg/emptyDurationMs)
    // até a sessão da balança resetar de verdade, não um atraso artificial.
    emptying: {
      invoke: {
        id: 'emptyLoop',
        src: 'emptyLoop',
        input: ({ context }) => ({
          scaleId: context.scaleId!,
          apiKey: context.apiKey!,
          plate: context.plate,
          intervalMs: READING_INTERVAL_MS,
        }),
      },
      after: { [EMPTY_MARGIN_MS]: 'openingSecondTransaction' },
    },
    openingSecondTransaction: {
      invoke: {
        src: 'openTransaction',
        input: ({ context }) => ({
          truckId: context.truckId,
          grainTypeId: context.grainTypeId,
          branchId: context.branchId,
        }),
        onDone: {
          target: 'onScale',
          actions: assign(({ context, event }) => nextPassContext(context, event.output.id)),
        },
        onError: {
          target: 'transactionError',
          actions: assign({ lastError: ({ event }) => String(event.error) }),
        },
      },
    },
    transactionError: {
      on: {
        RETRY: [
          { guard: 'isSecondPass', target: 'openingSecondTransaction' },
          { target: 'openingTransaction' },
        ],
      },
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
