import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { server } from '../test/mocks/server';
import { useCreateTask } from './useCreateTask';
import { mockTask } from '../test/mocks/handlers';
import type { CreateTaskPayload } from '../types/task';

function makeWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe('useCreateTask', () => {
  // TC-HK-05: mutation succeeds and tasks cache is invalidated
  it('TC-HK-05: creates a task and invalidates the tasks query cache', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    // Pre-populate the ['tasks', undefined] cache entry so we can detect refetch
    queryClient.setQueryData(['tasks', undefined], []);

    const { result } = renderHook(() => useCreateTask(), {
      wrapper: makeWrapper(queryClient),
    });

    const payload: CreateTaskPayload = { title: 'Nueva tarea', description: 'Desc' };

    act(() => { result.current.mutate(payload); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toMatchObject({ id: mockTask.id, title: mockTask.title });

    // After invalidation the cache entry should be stale / undefined (invalidated)
    const state = queryClient.getQueryState(['tasks', undefined]);
    expect(state?.isInvalidated).toBe(true);
  });

  // TC-HK-06: returned task matches server response
  it('TC-HK-06: mutation data matches the task returned by the server', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    const { result } = renderHook(() => useCreateTask(), {
      wrapper: makeWrapper(queryClient),
    });

    const payload: CreateTaskPayload = { title: 'Test' };

    act(() => { result.current.mutate(payload); });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.id).toBe(mockTask.id);
    expect(result.current.data?.status).toBe(mockTask.status);
  });

  // TC-HK-07: mutation fails when server returns error
  it('TC-HK-07: isError is true when the server returns an error', async () => {
    server.use(
      http.post('*/api/v1/tasks', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })
      )
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    const { result } = renderHook(() => useCreateTask(), {
      wrapper: makeWrapper(queryClient),
    });

    act(() => { result.current.mutate({ title: 'Fail' }); });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
