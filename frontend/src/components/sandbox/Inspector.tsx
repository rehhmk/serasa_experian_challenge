import { useSelector } from '@xstate/react'
import type { ActorRefFrom } from 'xstate'
import type { truckMachine } from '../../machines/truckMachine'
import { STABILIZATION_CONFIG } from '../../simulation/stabilizationConfig'
import { describeWeighingResult } from './truckDisplay'
import { YardActorContext } from './yardActorContext'

type GateStatus = 'pending' | 'pass' | 'fail'

function gate(pass: boolean, evaluated: boolean): GateStatus {
  if (!evaluated) return 'pending'
  return pass ? 'pass' : 'fail'
}

function formatKg(value: number): string {
  return `${value.toFixed(0)} kg`
}

// Responsabilidade única: escolher automaticamente o primeiro caminhão numa
// balança (não na fila) e mostrar, ao vivo, o pipeline real de
// POST /api/readings -> StabilizationEngine -> CompleteWeighingUseCase — a
// mesma sequência descrita no BLUEPRINT.md seção 2. Nunca inventa um passo
// que o backend não tem; cada linha aqui referencia um gate real do
// StabilizationEngine (mirrorado em stabilizationPredictor.ts).
export function Inspector() {
  const lanes = YardActorContext.useSelector((s) => s.context.lanes)
  const truckRefs = YardActorContext.useSelector((s) => s.context.truckRefs)
  const activeTruckId = Object.values(lanes).find((occupant) => occupant !== null) ?? null
  const activeRef = activeTruckId ? truckRefs[activeTruckId] : undefined

  return (
    <section className="inspector">
      <h2>Inspetor técnico</h2>
      {activeRef ? (
        <InspectorDetail actorRef={activeRef} />
      ) : (
        <p className="inspector-empty">
          Nenhum caminhão numa balança agora — despache um (ou ligue Auto) para acompanhar o pipeline em tempo real.
        </p>
      )}
    </section>
  )
}

function InspectorDetail({ actorRef }: { actorRef: ActorRefFrom<typeof truckMachine> }) {
  const snapshot = useSelector(actorRef, (s) => s)
  const { plate, scaleId, predictor } = snapshot.context
  const result = describeWeighingResult(snapshot)

  const onRoad = !snapshot.matches('queued')
  const hasScale = scaleId !== null
  const receivedReadings = predictor.samplesUsed > 0 || onRoad
  const evaluatedGates = predictor.samplesUsed >= STABILIZATION_CONFIG.minSamples
  const confirming = snapshot.matches('confirming') || snapshot.matches('confirmRetryWait')
  const confirmed = result.kind === 'confirmed'

  const gates = [
    {
      label: 'Amostras',
      detail: `${predictor.samplesUsed} / ${STABILIZATION_CONFIG.minSamples}`,
      status: gate(predictor.samplesUsed >= STABILIZATION_CONFIG.minSamples, predictor.samplesUsed > 0),
    },
    {
      label: 'Desvio padrão',
      detail: `${predictor.standardDeviation.toFixed(1)} / ${STABILIZATION_CONFIG.maxStdDevKg} kg`,
      status: gate(predictor.standardDeviation <= STABILIZATION_CONFIG.maxStdDevKg, evaluatedGates),
    },
    {
      label: 'Range',
      detail: `${predictor.range.toFixed(1)} / ${STABILIZATION_CONFIG.maxRangeKg} kg`,
      status: gate(predictor.range <= STABILIZATION_CONFIG.maxRangeKg, evaluatedGates),
    },
    {
      label: 'Slope',
      detail: `${predictor.slope.toFixed(2)} / ±${STABILIZATION_CONFIG.maxSlopeKgPerSec} kg/s`,
      status: gate(Math.abs(predictor.slope) <= STABILIZATION_CONFIG.maxSlopeKgPerSec, evaluatedGates),
    },
    {
      label: 'Duração',
      detail: `${(predictor.stabilizingForMs / 1000).toFixed(1)} / ${(STABILIZATION_CONFIG.stabilityDurationMs / 1000).toFixed(1)} s`,
      status: gate(predictor.stabilizingForMs >= STABILIZATION_CONFIG.stabilityDurationMs, predictor.stabilizingForMs > 0),
    },
  ]

  return (
    <>
      <p className="inspector-subject">
        Acompanhando <strong>{plate}</strong> {scaleId ? <>@ <strong>{scaleId}</strong></> : null}
      </p>

      <ol className="inspector-pipeline">
        <li className={`inspector-step ${onRoad ? 'inspector-step--active' : 'inspector-step--pending'}`}>
          <span className="inspector-step-index">1</span>
          <div>
            <p className="inspector-step-title">POST /api/readings a cada 100ms</p>
            <p className="inspector-step-detail">{onRoad ? 'Balança enviando leituras' : 'Aguardando despacho'}</p>
          </div>
        </li>
        <li className={`inspector-step ${receivedReadings ? 'inspector-step--done' : 'inspector-step--pending'}`}>
          <span className="inspector-step-index">2</span>
          <div>
            <p className="inspector-step-title">Validação de X-Scale-Key</p>
            <p className="inspector-step-detail">
              {receivedReadings ? 'Chave aceita (leituras sendo processadas)' : 'Sem leituras aceitas ainda'}
            </p>
          </div>
        </li>
        <li className={`inspector-step ${hasScale ? 'inspector-step--done' : 'inspector-step--pending'}`}>
          <span className="inspector-step-index">3</span>
          <div>
            <p className="inspector-step-title">Sessão isolada por balança</p>
            <p className="inspector-step-detail">{hasScale ? `ScaleSession(${scaleId})` : '—'}</p>
          </div>
        </li>
        <li
          className={`inspector-step ${predictor.samplesUsed > 0 ? (confirmed || snapshot.matches({ onScale: 'stable' }) ? 'inspector-step--done' : 'inspector-step--active') : 'inspector-step--pending'}`}
        >
          <span className="inspector-step-index">4</span>
          <div>
            <p className="inspector-step-title">Mediana + MAD — remoção de outliers</p>
            <p className="inspector-step-detail">
              {predictor.samplesUsed > 0
                ? `${predictor.outliersRemoved} descartada${predictor.outliersRemoved === 1 ? '' : 's'}, razão válida ${(predictor.validRatio * 100).toFixed(0)}%`
                : '—'}
            </p>
          </div>
        </li>
        <li className={`inspector-step ${confirmed || confirming ? 'inspector-step--done' : evaluatedGates ? 'inspector-step--active' : 'inspector-step--pending'}`}>
          <span className="inspector-step-index">5</span>
          <div>
            <p className="inspector-step-title">Gates: amostras, desvio, range, slope, duração</p>
            <ul className="inspector-gates">
              {gates.map((g) => (
                <li key={g.label} className={`inspector-gate inspector-gate--${g.status}`}>
                  <span>{g.label}</span>
                  <span className="inspector-gate-detail">{g.detail}</span>
                </li>
              ))}
            </ul>
          </div>
        </li>
        <li className={`inspector-step ${confirmed ? 'inspector-step--done' : confirming ? 'inspector-step--active' : 'inspector-step--pending'}`}>
          <span className="inspector-step-index">6</span>
          <div>
            <p className="inspector-step-title">Finalização transacional + confirmação no relatório</p>
            <p className="inspector-step-detail">
              {confirmed
                ? 'CompleteWeighingUseCase persistiu — confirmado via GET /api/reports/weighings'
                : confirming
                  ? 'Consultando o Livro de Pesagens...'
                  : '—'}
            </p>
          </div>
        </li>
      </ol>

      <div className={`inspector-result inspector-result--${result.kind}`}>
        <span className="inspector-result-kind">
          {result.kind === 'predicted' ? 'Predição local (UI)' : 'Confirmado pela API'}
        </span>
        <dl className="inspector-result-values">
          <div>
            <dt>Bruto</dt>
            <dd>{formatKg(result.grossWeightKg)}</dd>
          </div>
          <div>
            <dt>Tara</dt>
            <dd>{formatKg(result.tareWeightKg)}</dd>
          </div>
          <div>
            <dt>Líquido</dt>
            <dd>{formatKg(result.netWeightKg)}</dd>
          </div>
          <div>
            <dt>Custo</dt>
            <dd>{result.cost !== null ? `R$ ${result.cost.toFixed(2)}` : '—'}</dd>
          </div>
        </dl>
        {result.kind === 'predicted' && (
          <p className="inspector-result-caveat">
            Calculado no navegador com os mesmos thresholds do StabilizationEngine — nunca é a confirmação de que o
            backend persistiu a Weighing.
          </p>
        )}
      </div>
    </>
  )
}
