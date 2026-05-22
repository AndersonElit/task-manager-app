import { http, HttpResponse } from 'msw';
import type { Task, TaskHistory } from '../../types/task';

export const mockTask: Task = {
  id: 'task-1',
  title: 'Tarea de prueba',
  description: 'Descripción de prueba',
  status: 'pendiente',
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

export const mockHistory: TaskHistory[] = [
  { id: 'hist-1', status: 'pendiente', date: '2024-01-01T00:00:00Z' },
];

export const handlers = [
  http.get('*/api/v1/tasks', () => HttpResponse.json([mockTask])),

  http.post('*/api/v1/tasks', () => HttpResponse.json(mockTask, { status: 201 })),

  http.get('*/api/v1/tasks/:id/history', () => HttpResponse.json(mockHistory)),

  http.get('*/api/v1/tasks/:id', ({ params }) =>
    HttpResponse.json({ ...mockTask, id: params.id as string })
  ),

  http.put('*/api/v1/tasks/:id', ({ params }) =>
    HttpResponse.json({ ...mockTask, id: params.id as string, title: 'Actualizada' })
  ),

  http.delete('*/api/v1/tasks/:id', () => new HttpResponse(null, { status: 204 })),
];
