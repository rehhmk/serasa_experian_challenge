interface QueueItemProps {
  plate: string
  profile: string
}

// Deliberadamente não reusa TruckToken: um caminhão na fila não tem
// telemetria ao vivo pra mostrar (ainda não está numa balança), então um
// chip estático e mais simples é a responsabilidade certa aqui.
export function QueueItem({ plate, profile }: QueueItemProps) {
  return (
    <li className="queue-item">
      <span className="queue-item-plate">{plate}</span>
      <span className="queue-item-profile">{profile}</span>
    </li>
  )
}
