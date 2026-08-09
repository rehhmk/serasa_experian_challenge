import { createActorContext } from '@xstate/react'
import { yardMachine, type YardInput } from '../../machines/yardMachine'

export const DEFAULT_YARD_INPUT: YardInput = { numLanes: 2, numTrucks: 3 }

// Único ator do sandbox — todos os componentes leem/despacham através dele,
// sem prop-drilling (createActorContext, helper oficial do @xstate/react).
export const YardActorContext = createActorContext(yardMachine, { input: DEFAULT_YARD_INPUT })
