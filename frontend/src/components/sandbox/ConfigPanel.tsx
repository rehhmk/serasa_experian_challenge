import type { ChangeEvent } from 'react'
import type { TruckProfileName } from '../../simulation/readingProfiles/types'
import { YardActorContext } from './yardActorContext'

// duplicateRetry chega numa PR futura (a yardMachine e o <select> abaixo já
// suportam qualquer valor de TruckProfileName sem mudança estrutural).
const AVAILABLE_PROFILES: { value: TruckProfileName; label: string }[] = [
  { value: 'normal', label: 'Normal' },
  { value: 'noisy', label: 'Caminhão ruidoso' },
  { value: 'slowEntry', label: 'Entrada lenta' },
]

function parsePositiveInt(value: string): number | null {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

// Responsabilidade única: configuração da simulação — nº de raias/caminhões
// e o perfil de cada caminhão. Não sabe nada de layout de raias ou fila.
export function ConfigPanel() {
  const isReady = YardActorContext.useSelector((s) => s.matches('ready'))
  const numLanes = YardActorContext.useSelector((s) => s.context.numLanes)
  const numTrucks = YardActorContext.useSelector((s) => s.context.numTrucks)
  const trucks = YardActorContext.useSelector((s) => s.context.trucks)
  const actorRef = YardActorContext.useActorRef()

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
