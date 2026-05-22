import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { server } from '../test/mocks/server';
import {
  getAllTasks,
  getTaskById,
  createTask,
  updateTask,
  deleteTask,
  getTaskHistory,
} from './tasks.api';
import type { CreateTaskPayload, UpdateTaskPayload } from '../types/task';

// The apiClient uses VITE_API_URL as baseURL. In the jsdom environment
// import.meta.env.VITE_API_URL is undefined, so axios resolves requests
// against the empty string (effectively relative URLs). MSW's wildcard
// pattern "*/api/v1/…" matches these regardless of origin.

describe('tasks.api', () => {
  // TC-TK-01: getAllTasks() returns the task list
  it('TC-TK-01: getAllTasks returns list of tasks', async () => {
    const tasks = await getAllTasks();
    expect(Array.isArray(tasks)).toBe(true);
    expect(tasks).toHaveLength(1);
    expect(tasks[0].id).toBe('task-1');
    expect(tasks[0].title).toBe('Tarea de prueba');
  });

  // TC-TK-02: getAllTasks with status filter sends status param
  it('TC-TK-02: getAllTasks with status filter passes status query param', async () => {
    let capturedUrl: URL | null = null;

    server.use(
      http.get('*/api/v1/tasks', ({ request }) => {
        capturedUrl = new URL(request.url);
        return HttpResponse.json([]);
      })
    );

    await getAllTasks('pendiente');

    expect(capturedUrl).not.toBeNull();
    expect(capturedUrl!.searchParams.get('status')).toBe('pendiente');
  });

  // TC-TK-03: getTaskById returns a single task
  it('TC-TK-03: getTaskById returns the task for the given id', async () => {
    const task = await getTaskById('task-1');
    expect(task.id).toBe('task-1');
    expect(task.title).toBe('Tarea de prueba');
  });

  // TC-TK-04: getTaskById with 404 response rejects
  it('TC-TK-04: getTaskById rejects when task is not found (404)', async () => {
    server.use(
      http.get('*/api/v1/tasks/:id', () => new HttpResponse(null, { status: 404 }))
    );

    await expect(getTaskById('nonexistent')).rejects.toThrow();
  });

  // TC-TK-05: createTask returns the created task
  it('TC-TK-05: createTask returns the new task on success', async () => {
    const payload: CreateTaskPayload = { title: 'Nueva tarea', description: 'Desc' };
    const task = await createTask(payload);
    expect(task.id).toBe('task-1');
    expect(task.title).toBe('Tarea de prueba');
  });

  // TC-TK-06: createTask with 400 response rejects
  it('TC-TK-06: createTask rejects on bad request (400)', async () => {
    server.use(
      http.post('*/api/v1/tasks', () =>
        HttpResponse.json({ message: 'Validation error' }, { status: 400 })
      )
    );

    const payload: CreateTaskPayload = { title: '' };
    await expect(createTask(payload)).rejects.toThrow();
  });

  // TC-TK-07: updateTask returns the updated task
  it('TC-TK-07: updateTask returns the updated task on success', async () => {
    const payload: UpdateTaskPayload = { title: 'Actualizada' };
    const task = await updateTask('task-1', payload);
    expect(task.title).toBe('Actualizada');
    expect(task.id).toBe('task-1');
  });

  // TC-TK-08: deleteTask resolves without errors
  it('TC-TK-08: deleteTask resolves successfully on 204', async () => {
    await expect(deleteTask('task-1')).resolves.toBeUndefined();
  });

  // TC-TK-09: getTaskHistory returns history for a task
  it('TC-TK-09: getTaskHistory returns history entries for the given id', async () => {
    const history = await getTaskHistory('task-1');
    expect(Array.isArray(history)).toBe(true);
    expect(history).toHaveLength(1);
    expect(history[0].id).toBe('hist-1');
    expect(history[0].status).toBe('pendiente');
  });
});
