/**
 * Utilitaires d'export : téléchargement de fichiers binaires (PDF, Excel, PNG…).
 */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

/**
 * Télécharge la réponse Axios (blob) d'une requête d'export.
 */
export async function downloadResponse(response, filename) {
  const disposition = response.headers?.['content-disposition'] || ''
  const match = disposition.match(/filename="?([^";]+)"?/)
  const name = match ? match[1] : filename
  downloadBlob(response.data, name)
}