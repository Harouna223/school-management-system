import { useEffect, useRef, useState } from 'react'

/**
 * Fetch générique avec annulation automatique (prévient les fuites mémoire).
 */
export function useFetch(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const cancelled = useRef(false)

  useEffect(() => {
    cancelled.current = false
    setLoading(true)
    fetcher()
      .then((res) => {
        if (!cancelled.current) {
          setData(res?.data?.data ?? res?.data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled.current) {
          setError(err)
          setData(null)
        }
      })
      .finally(() => {
        if (!cancelled.current) setLoading(false)
      })
    return () => {
      cancelled.current = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, loading, error, setData }
}