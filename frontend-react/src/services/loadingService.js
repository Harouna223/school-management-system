let counter = 0
const listeners = new Set()

/**
 * Service de chargement global : compteur de requêtes en cours.
 * Indépendant de Redux pour éviter tout import circulaire avec le client Axios.
 */
const loadingService = {
  start() {
    counter += 1
    listeners.forEach((fn) => fn(counter))
  },
  stop() {
    counter = Math.max(0, counter - 1)
    listeners.forEach((fn) => fn(counter))
  },
  isPending() {
    return counter > 0
  },
  subscribe(fn) {
    listeners.add(fn)
    return () => listeners.delete(fn)
  },
}

export default loadingService