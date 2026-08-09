export type StabilityBadgeTone = 'neutral' | 'progress' | 'success' | 'warning' | 'danger'

interface StabilityBadgeProps {
  label: string
  tone?: StabilityBadgeTone
}

// Puramente apresentacional — não sabe nada de caminhão/ator/estado real,
// só renderiza um pill colorido a partir do que recebe via props.
export function StabilityBadge({ label, tone = 'neutral' }: StabilityBadgeProps) {
  return <span className={`stability-badge stability-badge--${tone}`}>{label}</span>
}
