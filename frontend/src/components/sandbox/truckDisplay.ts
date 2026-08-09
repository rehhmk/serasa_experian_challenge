import type { SnapshotFrom } from 'xstate'
import type { truckMachine } from '../../machines/truckMachine'
import type { StabilityBadgeTone } from './StabilityBadge'

export interface TruckDisplayState {
  label: string
  tone: StabilityBadgeTone
  /**
   * Posição ao longo da "estrada" da raia, 0 (entrando) a 100 (saindo).
   * Puramente decorativo — não corresponde a nenhuma distância real, só
   * marca visualmente em que fase da passagem o caminhão está.
   */
  roadPercent: number
}

// Módulo puro (sem JSX) pra ficar testável sem montar componente — Lane/
// TruckToken só consomem o resultado, não reimplementam a lógica de mapeamento.
export function describeTruckDisplay(snapshot: SnapshotFrom<typeof truckMachine>): TruckDisplayState {
  if (snapshot.matches('travelling')) {
    return { label: 'A caminho', tone: 'neutral', roadPercent: 15 }
  }
  if (snapshot.matches('openingTransaction')) {
    return { label: 'A caminho', tone: 'neutral', roadPercent: 35 }
  }
  if (snapshot.matches({ onScale: 'collecting' })) {
    return { label: 'Coletando', tone: 'neutral', roadPercent: 50 }
  }
  if (snapshot.matches({ onScale: 'stabilizing' })) {
    return { label: 'Estabilizando', tone: 'progress', roadPercent: 50 }
  }
  if (snapshot.matches('confirming') || snapshot.matches('confirmRetryWait')) {
    return { label: 'Confirmando', tone: 'progress', roadPercent: 50 }
  }
  if (snapshot.matches('recorded')) {
    return { label: 'Estável', tone: 'success', roadPercent: 50 }
  }
  if (snapshot.matches('unconfirmed')) {
    return { label: 'Não confirmado', tone: 'warning', roadPercent: 50 }
  }
  if (snapshot.matches('transactionError')) {
    return { label: 'Erro', tone: 'danger', roadPercent: 35 }
  }
  if (snapshot.matches('emptying')) {
    return { label: 'Esvaziando (2ª passagem)', tone: 'neutral', roadPercent: 65 }
  }
  if (snapshot.matches('openingSecondTransaction')) {
    return { label: 'Reabrindo transação', tone: 'neutral', roadPercent: 50 }
  }
  if (snapshot.matches('leaving')) {
    return { label: 'Saindo', tone: 'neutral', roadPercent: 90 }
  }
  return { label: 'Na fila', tone: 'neutral', roadPercent: 0 }
}
