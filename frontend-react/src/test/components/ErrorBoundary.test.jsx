import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup } from '@testing-library/react'
import ErrorBoundary from '../../components/ErrorBoundary'

afterEach(cleanup)

function Boom() {
  throw new ReferenceError('useLocation is not defined')
}

describe('ErrorBoundary', () => {
  it('affiche les enfants quand tout va bien', () => {
    render(
      <ErrorBoundary>
        <div>Contenu sain</div>
      </ErrorBoundary>,
    )
    expect(screen.getByText('Contenu sain')).toBeInTheDocument()
  })

  it('remplace un écran blanc par un message lisible et des actions', () => {
    // React journalise l'erreur : on tait la sortie pour garder le test lisible.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary>
        <Boom />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Une erreur est survenue')).toBeInTheDocument()
    // Le message d'origine doit rester visible : c'est ce qui permet de diagnostiquer.
    expect(screen.getByText('useLocation is not defined')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Recharger/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Tableau de bord/ })).toBeInTheDocument()
    spy.mockRestore()
  })
})
