import { useState } from 'react'

/**
 * Pagination locale réutilisable pour les tableaux.
 */
export function usePagination(initialPage = 0, initialSize = 10) {
  const [page, setPage] = useState(initialPage)
  const [size, setSize] = useState(initialSize)

  return { page, setPage, size, setSize }
}