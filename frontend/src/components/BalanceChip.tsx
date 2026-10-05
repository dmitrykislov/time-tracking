import { fmtSigned } from '../lib/time';

export function BalanceChip({ minutes, title }: { minutes: number; title?: string }) {
  const tone = minutes > 0 ? 'ahead' : minutes < 0 ? 'behind' : 'even';
  const word = minutes > 0 ? 'ahead' : minutes < 0 ? 'behind' : 'on target';
  return (
    <span className={`chip chip-${tone}`} title={title}>
      <strong>{fmtSigned(minutes)}</strong> {word}
    </span>
  );
}
