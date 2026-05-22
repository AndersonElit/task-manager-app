import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import TaskForm from './TaskForm';

function renderTaskForm(
  props: Partial<React.ComponentProps<typeof TaskForm>> = {}
) {
  const onSubmit = props.onSubmit ?? vi.fn().mockResolvedValue(undefined);
  const onCancel = props.onCancel ?? vi.fn();
  render(
    <TaskForm
      onSubmit={onSubmit}
      onCancel={onCancel}
      {...props}
    />
  );
  return { onSubmit, onCancel };
}

describe('TaskForm', () => {
  // TC-TF-01: fill fields and submit — onSubmit called with correct values
  it('TC-TF-01: calls onSubmit with title and description when form is valid', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderTaskForm({ onSubmit });

    await user.type(screen.getByLabelText('Título'), 'Mi título');
    await user.type(screen.getByLabelText('Descripción'), 'Desc');
    await user.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        { title: 'Mi título', description: 'Desc' },
        expect.anything()
      );
    });
  });

  // TC-TF-02: defaultValues pre-fill the fields
  it('TC-TF-02: pre-fills fields from defaultValues prop', () => {
    renderTaskForm({
      defaultValues: { title: 'Título inicial', description: 'Descripción inicial' },
    });

    expect(screen.getByLabelText('Título')).toHaveValue('Título inicial');
    expect(screen.getByLabelText('Descripción')).toHaveValue('Descripción inicial');
  });

  // TC-TF-03: submit without title shows required error, onSubmit not called
  it('TC-TF-03: shows required error and does not call onSubmit when title is empty', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderTaskForm({ onSubmit });

    await user.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(screen.getByText('El título es obligatorio')).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  // TC-TF-04: title exceeding 255 chars shows length error
  it('TC-TF-04: shows max-length error when title exceeds 255 characters', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderTaskForm({ onSubmit });

    const longTitle = 'a'.repeat(256);
    await user.type(screen.getByLabelText('Título'), longTitle);
    await user.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(screen.getByText('Máximo 255 caracteres')).toBeInTheDocument();
    });
    expect(onSubmit).not.toHaveBeenCalled();
  });

  // TC-TF-05: clicking cancel calls onCancel
  it('TC-TF-05: calls onCancel when the cancel button is clicked', async () => {
    const user = userEvent.setup();
    const onCancel = vi.fn();
    renderTaskForm({ onCancel });

    await user.click(screen.getByRole('button', { name: /cancelar/i }));

    expect(onCancel).toHaveBeenCalledTimes(1);
  });

  // TC-TF-06: submit with only title (no description) calls onSubmit successfully
  it('TC-TF-06: calls onSubmit with only title when description is omitted', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn().mockResolvedValue(undefined);
    renderTaskForm({ onSubmit });

    await user.type(screen.getByLabelText('Título'), 'Solo título');
    await user.click(screen.getByRole('button', { name: /guardar/i }));

    await waitFor(() => {
      expect(onSubmit).toHaveBeenCalledWith(
        expect.objectContaining({ title: 'Solo título' }),
        expect.anything()
      );
    });
  });

  // TC-TF-07: error message is associated with the field (visible in the DOM)
  it('TC-TF-07: error message for empty title is present in the DOM after failed submit', async () => {
    const user = userEvent.setup();
    renderTaskForm({});

    await user.click(screen.getByRole('button', { name: /guardar/i }));

    const errorMessage = await screen.findByText('El título es obligatorio');
    expect(errorMessage).toBeInTheDocument();
  });
});
