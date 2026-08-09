import { Lane } from './Lane'
import { YardActorContext } from './yardActorContext'

// Responsabilidade única: layout puro — uma Lane por balança provisionada.
export function LaneGrid() {
  const scales = YardActorContext.useSelector((s) => s.context.scales)

  return (
    <div className="lane-grid">
      {scales.map((scale) => (
        <Lane key={scale.id} scaleId={scale.id} />
      ))}
    </div>
  )
}
