import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

// Smoke test: só garante que a árvore monta e o bootstrap do pátio começa —
// a cobertura de comportamento real (dispatch, Auto, etc.) já está nos
// testes de yardMachine/truckMachine. Nunca deixa a promise resolver, então
// nenhuma chamada de fetch real é tentada neste teste.
vi.mock('./api/bootstrap', () => ({
  bootstrapSandbox: vi.fn(() => new Promise(() => {})),
}))

describe('App', () => {
  it('renderiza a página de sandbox e começa o bootstrap do pátio', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: /sandbox/i })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent(/provisionando/i)
  })
})
