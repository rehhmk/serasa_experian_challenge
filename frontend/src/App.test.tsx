import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the sandbox scaffold heading', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: /grain weighing — sandbox/i })).toBeInTheDocument()
  })
})
