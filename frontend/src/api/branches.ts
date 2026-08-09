import { apiRequest } from './http'
import type { Branch, BranchRequest } from './types'

export function listBranches(): Promise<Branch[]> {
  return apiRequest<Branch[]>('/branches')
}

export function createBranch(request: BranchRequest): Promise<Branch> {
  return apiRequest<Branch>('/branches', { method: 'POST', body: request })
}
