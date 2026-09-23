import { describe, it, expect, vi, afterEach } from 'vitest'
import { renderHook, act, waitFor, cleanup } from '@testing-library/react'
import { useFetch } from '../../hooks/useFetch'

afterEach(cleanup)

function deferred() {
  let resolve, reject
  const promise = new Promise((res, rej) => { resolve = res; reject = rej })
  return { promise, resolve, reject }
}

describe('useFetch', () => {
  it('déplie une réponse API et termine le chargement', async () => {
    const fetcher = vi.fn().mockResolvedValue({ data: { data: { total: 3 } } })
    const { result } = renderHook(() => useFetch(fetcher))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.data).toEqual({ total: 3 })
    expect(result.current.error).toBeNull()
  })

  it('ignore une réponse ancienne arrivée après la nouvelle', async () => {
    const first = deferred()
    const second = deferred()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result, rerender } = renderHook(({ id }) => useFetch(fetcher, [id]), { initialProps: { id: 1 } })
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    rerender({ id: 2 })
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
    await act(async () => { second.resolve({ data: { data: 'nouvelle' } }) })
    await act(async () => { first.resolve({ data: { data: 'ancienne' } }) })
    expect(result.current.data).toBe('nouvelle')
    expect(result.current.loading).toBe(false)
  })

  it('ignore une erreur obsolète pendant le chargement suivant', async () => {
    const first = deferred()
    const second = deferred()
    const fetcher = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const { result, rerender } = renderHook(({ id }) => useFetch(fetcher, [id]), { initialProps: { id: 1 } })
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(1))
    rerender({ id: 2 })
    await waitFor(() => expect(fetcher).toHaveBeenCalledTimes(2))
    await act(async () => { first.reject(new Error('ancienne erreur')) })
    expect(result.current.error).toBeNull()
    expect(result.current.loading).toBe(true)
    await act(async () => { second.resolve({ data: { data: 'ok' } }) })
    expect(result.current.data).toBe('ok')
  })

  it('expose les erreurs synchrones et termine le chargement', async () => {
    const error = new Error('échec')
    const { result } = renderHook(() => useFetch(() => { throw error }))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe(error)
    expect(result.current.data).toBeNull()
  })
})
