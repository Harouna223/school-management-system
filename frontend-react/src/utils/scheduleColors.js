const SCHEDULE_COLORS = [
  { background: '#dbeafe', text: '#1e3a8a', border: '#60a5fa', shadow: 'rgba(37,99,235,0.28)' },
  { background: '#dcfce7', text: '#14532d', border: '#4ade80', shadow: 'rgba(34,197,94,0.28)' },
  { background: '#ede9fe', text: '#4c1d95', border: '#a78bfa', shadow: 'rgba(139,92,246,0.28)' },
  { background: '#fef3c7', text: '#78350f', border: '#fbbf24', shadow: 'rgba(245,158,11,0.28)' },
  { background: '#cffafe', text: '#164e63', border: '#22d3ee', shadow: 'rgba(6,182,212,0.28)' },
  { background: '#ffedd5', text: '#7c2d12', border: '#fb923c', shadow: 'rgba(249,115,22,0.28)' },
  { background: '#ecfccb', text: '#365314', border: '#a3e635', shadow: 'rgba(132,204,22,0.28)' },
]

export const getScheduleColors = (slot) => {
  const subjectKey = String(slot?.subjectId ?? slot?.subjectName ?? slot?.id ?? 'schedule-slot')
  let hash = 0

  for (const char of subjectKey) {
    hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  }

  return SCHEDULE_COLORS[hash % SCHEDULE_COLORS.length]
}
