import { useState, type ChangeEvent } from 'react'
import type { TruckProfileName } from '../../simulation/readingProfiles/types'
import { YardActorContext } from './yardActorContext'

const AVAILABLE_PROFILES: { value: TruckProfileName; label: string }[] = [
  { value: 'normal', label: 'Normal' },
  { value: 'noisy', label: 'Caminhão ruidoso' },
  { value: 'slowEntry', label: 'Entrada lenta' },
  { value: 'duplicateRetry', label: 'Retry duplicado' },
]

function parsePositiveInt(value: string): number | null {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

// Responsabilidade única: configuração da simulação — nº de raias/caminhões,
// os controles de "+/− balança" e "enviar aleatórios à fila", e o perfil de
// cada caminhão. Não sabe nada de layout de raias ou fila.
export function ConfigPanel() {
  const isReady = YardActorContext.useSelector((s) => s.matches('ready'))
  const numLanes = YardActorContext.useSelector((s) => s.context.numLanes)
  const numTrucks = YardActorContext.useSelector((s) => s.context.numTrucks)
  const trucks = YardActorContext.useSelector((s) => s.context.trucks)
  const actorRef = YardActorContext.useActorRef()
  const [randomQty, setRandomQty] = useState(3)

  function handleNumLanesChange(event: ChangeEvent<HTMLInputElement>) {
    const value = parsePositiveInt(event.target.value)
    if (value !== null) {
      actorRef.send({ type: 'SET_CONFIG', numLanes: value })
    }
  }

  function handleNumTrucksChange(event: ChangeEvent<HTMLInputElement>) {
    const value = parsePositiveInt(event.target.value)
    if (value !== null) {
      actorRef.send({ type: 'SET_CONFIG', numTrucks: value })
    }
  }

  function handleRandomQtyChange(event: ChangeEvent<HTMLInputElement>) {
    const value = parsePositiveInt(event.target.value)
    if (value !== null) {
      setRandomQty(value)
    }
  }

  return (
    <div className="config-panel">
      <div className="config-panel-fields">
        <label>
          Balanças
          <input type="number" min={1} max={8} value={numLanes} disabled={!isReady} onChange={handleNumLanesChange} />
        </label>
        <label>
          Caminhões
          <input
            type="number"
            min={1}
            max={20}
            value={numTrucks}
            disabled={!isReady}
            onChange={handleNumTrucksChange}
          />
        </label>
        <button type="button" disabled={!isReady} onClick={() => actorRef.send({ type: 'RUN_CONCURRENCY_DEMO' })}>
          Testar concorrência (2 balanças)
        </button>
      </div>

      <div className="config-panel-highway-controls">
        <button
          type="button"
          disabled={!isReady}
          onClick={() => actorRef.send({ type: 'SET_CONFIG', numLanes: numLanes + 1 })}
        >
          + balança
        </button>
        <button
          type="button"
          disabled={!isReady || numLanes <= 1}
          onClick={() => actorRef.send({ type: 'SET_CONFIG', numLanes: Math.max(1, numLanes - 1) })}
        >
          − balança
        </button>
        <span className="config-panel-lane-count">
          {numLanes} balança{numLanes === 1 ? '' : 's'} na rodovia
        </span>
        <label className="config-panel-qty">
          Qtd.
          <input type="number" min={1} max={20} value={randomQty} disabled={!isReady} onChange={handleRandomQtyChange} />
        </label>
        <button
          type="button"
          disabled={!isReady}
          onClick={() => actorRef.send({ type: 'ENQUEUE_RANDOM_TRUCKS', count: randomQty })}
        >
          Enviar aleatórios à fila
        </button>
      </div>

      <ul className="truck-profile-list">
        {trucks.map((truck) => (
          <li key={truck.truckId}>
            <span className="truck-profile-list-plate">{truck.plate}</span>
            <select
              value={truck.profile}
              disabled={!isReady}
              onChange={(event) =>
                actorRef.send({
                  type: 'SET_TRUCK_PROFILE',
                  truckId: truck.truckId,
                  profile: event.target.value as TruckProfileName,
                })
              }
            >
              {AVAILABLE_PROFILES.map((profile) => (
                <option key={profile.value} value={profile.value}>
                  {profile.label}
                </option>
              ))}
            </select>
          </li>
        ))}
      </ul>
    </div>
  )
}
