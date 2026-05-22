import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { server } from '../test/mocks/server';
import { useUpdateTask } from './useUpdateTask';
import { mockTask } from '../test/mocks/handlers';
import type { UpdateTaskPayload } from '../types/task';

function makeWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe('useUpdateTask', () => {
  // TC-HK-08: mutation succeeds and both task list and single-task caches are invalidated
  it('TC-HK-08: updates a task and invalidates the tasks list and single task caches', async () => {
    const taskId = 'task-1';
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    // Pre-populate caches to verify invalidation
    queryClient.setQueryData(['tasks', undefined], [mockTask]);
    queryClient.setQueryData(['tasks', taskId], mockTask);

    const { result } = renderHook(() => useUpdateTask(taskId), {
      wrapper: makeWrapper(queryClient),
    });

    const payload: UpdateTaskPayload = { title: 'Actualizada' };

    act(() => { result.current.mutate(payload); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.title).toBe('Actualizada');
    expect(result.current.data?.id).toBe(taskId);

    // Both queries should be marked as invalidated
    const listState = queryClient.getQueryState(['tasks', undefined]);
    const singleState = queryClient.getQueryState(['tasks', taskId]);

    expect(listState?.isInvalidated).toBe(true);
    expect(singleState?.isInvalidated).toBe(true);
  });

  // TC-HK-09: mutation fails when server returns an error
  it('TC-HK-09: isError is true when the server returns an error', async () => {
    server.use(
      http.put('*/api/v1/tasks/:id', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })
      )
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    const { result } = renderHook(() => useUpdateTask('task-1'), {
      wrapper: makeWrapper(queryClient),
    });

    act(() => { result.current.mutate({ title: 'Fail' }); });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
