import { hasQueuedTruckAndFreeLane } from '../../machines/yardMachine'
import { YardActorContext } from './yardActorContext'

// Responsabilidade única: controles globais da simulação (Auto, despacho
// manual, reset) — nada sobre raias/caminhões específicos.
export function ControlBar() {
  const isReady = YardActorContext.useSelector((s) => s.matches('ready'))
  const auto = YardActorContext.useSelector((s) => s.context.auto)
  const canDispatch = YardActorContext.useSelector((s) => hasQueuedTruckAndFreeLane(s.context))
  const actorRef = YardActorContext.useActorRef()

  return (
    <div className="control-bar">
      <label className="control-bar-toggle">
        <input
          type="checkbox"
          checked={auto}
          disabled={!isReady}
          onChange={() => actorRef.send({ type: 'TOGGLE_AUTO' })}
        />
        Auto
      </label>
      <button
        type="button"
        disabled={!isReady || auto || !canDispatch}
        onClick={() => actorRef.send({ type: 'DISPATCH_NEXT' })}
      >
        Despachar próximo
      </button>
      <button type="button" disabled={!isReady} onClick={() => actorRef.send({ type: 'RESET' })}>
        Reiniciar sandbox
      </button>
    </div>
  )
}
