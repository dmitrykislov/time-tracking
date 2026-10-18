import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { BalanceChip } from '../src/components/BalanceChip';

describe('BalanceChip', () => {
  it('reads ahead, behind or on target with matching tone', () => {
    const { rerender } = render(<BalanceChip minutes={90} />);
    expect(screen.getByText('+1h 30m').closest('.chip')).toHaveClass('chip-ahead');
    expect(screen.getByText('+1h 30m').closest('.chip')).toHaveTextContent('+1h 30m ahead');

    rerender(<BalanceChip minutes={-45} />);
    expect(screen.getByText('−45m').closest('.chip')).toHaveClass('chip-behind');
    expect(screen.getByText('−45m').closest('.chip')).toHaveTextContent('behind');

    rerender(<BalanceChip minutes={0} />);
    expect(screen.getByText('0m').closest('.chip')).toHaveClass('chip-even');
    expect(screen.getByText('0m').closest('.chip')).toHaveTextContent('on target');
  });
});
