import '@testing-library/jest-dom'

/**
 * jsdom n'implémente pas ResizeObserver, dont dépendent les graphiques
 * (recharts / ResponsiveContainer) utilisés par les tableaux de bord.
 */
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

/**
 * ResponsiveContainer mesure son conteneur ; jsdom ne calcule aucune dimension,
 * donc on force une taille non nulle pour que les graphiques se rendent.
 */
if (!globalThis.Element.prototype.getBoundingClientRect.__patched) {
  const original = globalThis.Element.prototype.getBoundingClientRect
  const patched = function getBoundingClientRect() {
    const rect = original.call(this)
    if (rect.width === 0 && rect.height === 0) {
      return { ...rect, width: 800, height: 400, top: 0, left: 0, right: 800, bottom: 400 }
    }
    return rect
  }
  patched.__patched = true
  globalThis.Element.prototype.getBoundingClientRect = patched
}
