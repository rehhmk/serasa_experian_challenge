import { QueueItem } from './QueueItem'
import { YardActorContext } from './yardActorContext'

// Responsabilidade única: lista de caminhões aguardando raia livre.
export function QueuePanel() {
  const queue = YardActorContext.useSelector((s) => s.context.queue)
  const trucks = YardActorContext.useSelector((s) => s.context.trucks)

  return (
    <div className="queue-panel">
      <h2>Fila de espera ({queue.length})</h2>
      {queue.length === 0 ? (
        <p className="queue-panel-empty">Vazia</p>
      ) : (
        <ul className="queue-panel-list">
          {queue.map((truckId) => {
            const truck = trucks.find((t) => t.truckId === truckId)
            return truck ? <QueueItem key={truckId} plate={truck.plate} profile={truck.profile} /> : null
          })}
        </ul>
      )}
    </div>
  )
}
