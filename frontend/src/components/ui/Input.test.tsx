import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import Input from './Input';

describe('Input', () => {
  // TC-UI-03: error prop renders error message and red-border class on the input
  it('TC-UI-03: renders the error message and applies red border class when error prop is set', () => {
    render(<Input label="Campo" error="Este campo es obligatorio" />);

    const errorMessage = screen.getByText('Este campo es obligatorio');
    expect(errorMessage).toBeInTheDocument();
    expect(errorMessage.tagName.toLowerCase()).toBe('p');

    const input = screen.getByLabelText('Campo');
    // The Input component adds 'border-red-400' class when error is present
    expect(input.className).toContain('border-red-400');
  });

  // No error prop — error message should not appear
  it('does not render an error message when the error prop is not set', () => {
    render(<Input label="Campo" />);
    expect(screen.queryByRole('paragraph')).not.toBeInTheDocument();
  });

  // label prop creates a linked label
  it('renders a label linked to the input via htmlFor', () => {
    render(<Input label="Correo" />);
    const input = screen.getByLabelText('Correo');
    expect(input).toBeInTheDocument();
  });
});
