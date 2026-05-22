import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';
import TaskCard from './TaskCard';
import type { Task } from '../../types/task';

// Mock hooks that perform network requests
vi.mock('../../hooks/useUpdateTask', () => ({
  useUpdateTask: vi.fn(() => ({
    mutateAsync: vi.fn().mockResolvedValue({}),
    isPending: false,
  })),
}));

vi.mock('../../hooks/useDeleteTask', () => ({
  useDeleteTask: vi.fn(() => ({
    mutateAsync: vi.fn().mockResolvedValue(undefined),
    isPending: false,
  })),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const baseTask: Task = {
  id: 'task-1',
  title: 'Tarea de prueba',
  description: 'Descripción de prueba',
  status: 'pendiente',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

function renderCard(task: Task = baseTask) {
  return render(
    <MemoryRouter>
      <TaskCard task={task} />
    </MemoryRouter>
  );
}

describe('TaskCard', () => {
  beforeEach(() => {
    mockNavigate.mockReset();
  });

  // TC-TC-01: renders the task title and description
  it('TC-TC-01: renders the task title and description', () => {
    renderCard();
    expect(screen.getByText('Tarea de prueba')).toBeInTheDocument();
    expect(screen.getByText('Descripción de prueba')).toBeInTheDocument();
  });

  // TC-TC-02: renders the correct status badge
  it('TC-TC-02: shows "pendiente" badge for pending tasks', () => {
    renderCard({ ...baseTask, status: 'pendiente' });
    expect(screen.getByText('pendiente')).toBeInTheDocument();
  });

  it('TC-TC-02b: shows "completada" badge for completed tasks', () => {
    renderCard({ ...baseTask, status: 'completada' });
    expect(screen.getByText('completada')).toBeInTheDocument();
  });

  // TC-TC-03: clicking the delete (trash) button opens the confirmation modal
  it('TC-TC-03: clicking the delete button shows a confirmation modal', async () => {
    const user = userEvent.setup();
    renderCard();

    // The trash icon button is a ghost Button inside TaskDeleteButton.
    // We look for any button that contains an SVG with a red stroke path (trash icon).
    // A more resilient approach: look for all buttons and click the last ghost one
    // (delete is the second action button after edit).
    const buttons = screen.getAllByRole('button');
    // The trash button is the last ghost button rendered by TaskDeleteButton
    const trashButton = buttons[buttons.length - 1];
    await user.click(trashButton);

    await waitFor(() => {
      expect(screen.getByText(/eliminar/i, { selector: 'h2' })).toBeInTheDocument();
    });

    // The confirmation dialog should show the task title
    expect(screen.getByText(/"Tarea de prueba"/i)).toBeInTheDocument();
  });

  // TC-TC-04: when description is null no extra empty paragraph is shown
  it('TC-TC-04: does not render a description paragraph when description is null', () => {
    renderCard({ ...baseTask, description: null });
    expect(screen.queryByText('Descripción de prueba')).not.toBeInTheDocument();
  });
});
