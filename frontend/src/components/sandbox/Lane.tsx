import { TruckToken } from './TruckToken'
import { YardActorContext } from './yardActorContext'

interface LaneProps {
  scaleId: string
}

// Genérico e de responsabilidade única: renderiza UMA raia/balança, dado só
// o scaleId — não existe um componente por balança específica. LaneGrid
// decide quantas instâncias renderizar.
export function Lane({ scaleId }: LaneProps) {
  const occupantId = YardActorContext.useSelector((s) => s.context.lanes[scaleId] ?? null)
  const truckRefs = YardActorContext.useSelector((s) => s.context.truckRefs)
  const branchName = YardActorContext.useSelector((s) => s.context.branchName)
  const truckRef = occupantId ? truckRefs[occupantId] : undefined

  return (
    <div className="lane" data-scale-id={scaleId}>
      <header className="lane-header">
        <span className="lane-header-scale">{scaleId}</span>
        {branchName && <span className="lane-header-branch">{branchName}</span>}
      </header>
      {truckRef ? <TruckToken actorRef={truckRef} /> : <p className="lane-empty">Balança livre</p>}
    </div>
  )
}
