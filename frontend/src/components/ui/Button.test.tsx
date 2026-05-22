import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Button from './Button';

describe('Button', () => {
  // TC-UI-01: loading state renders spinner and disables the button
  it('TC-UI-01: shows a spinner SVG and disables the button when loading is true', () => {
    render(<Button loading>Guardar</Button>);

    const button = screen.getByRole('button', { name: /guardar/i });
    expect(button).toBeDisabled();

    // The spinner is an SVG rendered inside the button when loading=true
    const svgs = button.querySelectorAll('svg');
    expect(svgs.length).toBeGreaterThan(0);
  });

  // TC-UI-02: onClick fires when the button is not disabled
  it('TC-UI-02: calls onClick when the button is clicked and not disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Acción</Button>);

    await user.click(screen.getByRole('button', { name: /acción/i }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  // Extra: onClick does not fire when button is disabled
  it('does not call onClick when the button is disabled', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button disabled onClick={onClick}>Acción</Button>);

    await user.click(screen.getByRole('button', { name: /acción/i }));

    expect(onClick).not.toHaveBeenCalled();
  });

  // Extra: onClick does not fire when loading
  it('does not call onClick when the button is in loading state', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<Button loading onClick={onClick}>Cargando</Button>);

    await user.click(screen.getByRole('button', { name: /cargando/i }));

    expect(onClick).not.toHaveBeenCalled();
  });
});
