import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Modal from './Modal';

function renderModal(
  open: boolean,
  onClose: () => void = vi.fn(),
  title = 'Mi modal'
) {
  return render(
    <Modal open={open} title={title} onClose={onClose}>
      <p>Contenido del modal</p>
    </Modal>
  );
}

describe('Modal', () => {
  // TC-UI-04: open=true renders children and title
  it('TC-UI-04: renders the title and children when open is true', () => {
    renderModal(true);
    expect(screen.getByText('Mi modal')).toBeInTheDocument();
    expect(screen.getByText('Contenido del modal')).toBeInTheDocument();
  });

  // TC-UI-04 extension: pressing Escape calls onClose
  it('TC-UI-04b: calls onClose when the Escape key is pressed', () => {
    const onClose = vi.fn();
    renderModal(true, onClose);

    fireEvent.keyDown(document, { key: 'Escape', code: 'Escape' });

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  // TC-UI-04 extension: clicking overlay calls onClose
  it('TC-UI-04c: calls onClose when the overlay backdrop is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal(true, onClose);

    // The overlay is an absolute div positioned behind the modal panel.
    // It is the first child of the outer container.
    const overlay = document.querySelector('.absolute.inset-0') as HTMLElement;
    expect(overlay).not.toBeNull();

    await user.click(overlay);

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  // TC-UI-05: open=false renders nothing
  it('TC-UI-05: does not render anything when open is false', () => {
    renderModal(false);
    expect(screen.queryByText('Mi modal')).not.toBeInTheDocument();
    expect(screen.queryByText('Contenido del modal')).not.toBeInTheDocument();
  });

  // Extra: close button inside modal header calls onClose
  it('calls onClose when the close (X) button inside the modal header is clicked', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderModal(true, onClose);

    // The X button is the only button in the modal header area
    const closeButton = screen.getByRole('button');
    await user.click(closeButton);

    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
