import { createBranch, listBranches } from './branches'
import { createGrainType, listGrainTypes } from './grainTypes'
import { createTruck, listTrucks } from './trucks'
import { createScale } from './scales'
import { setScaleKey } from './scaleKeyStore'
import type { Branch, GrainType, ScaleSummary, Truck } from './types'

const SANDBOX_GRAIN_TYPE_NAME = 'Sandbox Grain'
const SANDBOX_TRUCK_PLATE_PREFIX = 'SB'

// Filial/tipo de grão: reaproveita o que já existir no banco (seed dev ou de
// uma sessão anterior do sandbox) — não precisa ser exclusivo do sandbox.
export async function resolveSandboxBranch(): Promise<Branch> {
  const existing = await listBranches()
  if (existing.length > 0) {
    return existing[0]
  }
  return createBranch({ name: 'Sandbox Branch', city: 'Sandbox City', state: 'SB' })
}

export async function resolveSandboxGrainType(): Promise<GrainType> {
  const existing = await listGrainTypes()
  const sandboxGrainType = existing.find((grainType) => grainType.name === SANDBOX_GRAIN_TYPE_NAME)
  if (sandboxGrainType) {
    return sandboxGrainType
  }
  if (existing.length > 0) {
    return existing[0]
  }
  return createGrainType({
    name: SANDBOX_GRAIN_TYPE_NAME,
    purchasePricePerTon: 1000,
    referenceStockKg: 100000,
  })
}

// plate é UNIQUE e VARCHAR(10) no banco — caminhões "SB…" de sessões
// anteriores são reaproveitados; só cria os que faltarem para atingir `count`.
export async function provisionTrucks(count: number): Promise<Truck[]> {
  const existing = await listTrucks()
  const sandboxTrucks = existing.filter((truck) => truck.plate.startsWith(SANDBOX_TRUCK_PLATE_PREFIX))
  const reused = sandboxTrucks.slice(0, count)
  const missing = count - reused.length
  if (missing <= 0) {
    return reused
  }
  const created = await Promise.all(
    Array.from({ length: missing }, () =>
      createTruck({ plate: randomSandboxPlate(), tareWeightKg: 9000 }),
    ),
  )
  return [...reused, ...created]
}

// Balanças: sempre mint ids novos e aleatórios por sessão — nunca reaproveita
// scale-01 (seed dev) nem ids de sessões anteriores. A apiKey em texto plano só
// existe na resposta de criação (ScaleCreatedResponse), então cada scale
// provisionada aqui precisa vir de um POST /api/scales real, sempre.
export async function provisionScales(count: number, branchId: string): Promise<ScaleSummary[]> {
  const created = await Promise.all(
    Array.from({ length: count }, async () => {
      const scale = await createScale({ id: randomSandboxScaleId(), branchId })
      setScaleKey(scale.id, scale.apiKey)
      return { id: scale.id, branchId: scale.branchId }
    }),
  )
  return created
}

export interface BootstrapSandboxParams {
  numLanes: number
  numTrucks: number
}

export interface BootstrapSandboxResult {
  branch: Branch
  grainType: GrainType
  scales: ScaleSummary[]
  trucks: Truck[]
}

export async function bootstrapSandbox(
  { numLanes, numTrucks }: BootstrapSandboxParams,
): Promise<BootstrapSandboxResult> {
  const branch = await resolveSandboxBranch()
  const grainType = await resolveSandboxGrainType()
  const [scales, trucks] = await Promise.all([
    provisionScales(numLanes, branch.id),
    provisionTrucks(numTrucks),
  ])
  return { branch, grainType, scales, trucks }
}

function randomSandboxPlate(): string {
  const suffix = Math.random().toString(36).slice(2, 7).toUpperCase()
  return `${SANDBOX_TRUCK_PLATE_PREFIX}${suffix}`
}

function randomSandboxScaleId(): string {
  return `sandbox-${Math.random().toString(36).slice(2, 10)}`
}
