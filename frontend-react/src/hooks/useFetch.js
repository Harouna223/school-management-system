import { useEffect, useState } from 'react'

/** Ignore les réponses obsolètes après un changement de dépendances ou démontage. */
export function useFetch(fetcher, deps = []) {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    Promise.resolve()
      .then(() => fetcher())
      .then((res) => {
        if (!cancelled) {
          setData(res?.data?.data ?? res?.data)
          setError(null)
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err)
          setData(null)
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)

  return { data, loading, error, setData }
}
