import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ThemeProvider, createTheme } from '@mui/material/styles'
import StatusChip from '../../components/StatusChip'

const renderWithTheme = (ui) =>
  render(<ThemeProvider theme={createTheme()}>{ui}</ThemeProvider>)

describe('StatusChip', () => {
  it('ne rend rien si le statut est vide', () => {
    const { container } = renderWithTheme(<StatusChip status={null} />)
    expect(container).toBeEmptyDOMElement()
  })

  it('affiche le libellé humanisé du statut', () => {
    renderWithTheme(<StatusChip status="ACTIVE" />)
    expect(screen.getByText('Active')).toBeInTheDocument()
  })

  it('humanise les statuts avec underscores', () => {
    renderWithTheme(<StatusChip status="ON_LEAVE" />)
    expect(screen.getByText('On Leave')).toBeInTheDocument()
  })

  it('associe PAID à la couleur success', () => {
    renderWithTheme(<StatusChip status="PAID" />)
    expect(screen.getByText('Paid')).toBeInTheDocument()
  })

  it('associe ABSENT à la couleur error', () => {
    renderWithTheme(<StatusChip status="ABSENT" />)
    expect(screen.getByText('Absent')).toBeInTheDocument()
  })
})
