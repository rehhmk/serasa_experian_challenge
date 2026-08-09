import { YardActorContext } from './yardActorContext'

// Responsabilidade única: mostrar o estado de bootstrap do pátio (provisionando
// balanças/caminhões reais via API) e o botão de retry se falhar. Não renderiza
// nada quando o pátio já está "ready" — as outras telas assumem esse caso.
export function ScaleProvisioningBanner() {
  const isBootstrapping = YardActorContext.useSelector((s) => s.matches('bootstrapping'))
  const isReprovisioning = YardActorContext.useSelector((s) => s.matches('reprovisioning'))
  const isBootstrapError = YardActorContext.useSelector((s) => s.matches('bootstrapError'))
  const bootstrapError = YardActorContext.useSelector((s) => s.context.bootstrapError)
  const actorRef = YardActorContext.useActorRef()

  if (isBootstrapping) {
    return (
      <div className="sandbox-banner sandbox-banner--info" role="status">
        Provisionando filial, tipo de grão, balanças e caminhões via API...
      </div>
    )
  }

  if (isReprovisioning) {
    return (
      <div className="sandbox-banner sandbox-banner--info" role="status">
        Provisionando capacidade extra...
      </div>
    )
  }

  if (isBootstrapError) {
    return (
      <div className="sandbox-banner sandbox-banner--error" role="alert">
        <span>Falha ao provisionar o sandbox: {bootstrapError}</span>
        <button type="button" onClick={() => actorRef.send({ type: 'RETRY' })}>
          Tentar de novo
        </button>
      </div>
    )
  }

  return null
}
