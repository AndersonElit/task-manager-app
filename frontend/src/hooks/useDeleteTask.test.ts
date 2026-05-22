import { describe, it, expect } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { server } from '../test/mocks/server';
import { useDeleteTask } from './useDeleteTask';
import { mockTask } from '../test/mocks/handlers';

function makeWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe('useDeleteTask', () => {
  // TC-HK-10: mutation succeeds and tasks cache is invalidated
  it('TC-HK-10: deletes a task and invalidates the tasks query cache', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    // Pre-populate cache so we can verify it is invalidated
    queryClient.setQueryData(['tasks', undefined], [mockTask]);

    const { result } = renderHook(() => useDeleteTask(), {
      wrapper: makeWrapper(queryClient),
    });

    await act(async () => {
      await result.current.mutateAsync('task-1');
    });

    expect(result.current.isSuccess).toBe(true);

    const state = queryClient.getQueryState(['tasks', undefined]);
    expect(state?.isInvalidated).toBe(true);
  });

  // TC-HK-11: mutation fails when server returns an error
  it('TC-HK-11: isError is true when the server returns an error', async () => {
    server.use(
      http.delete('*/api/v1/tasks/:id', () =>
        HttpResponse.json({ message: 'Server error' }, { status: 500 })
      )
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    const { result } = renderHook(() => useDeleteTask(), {
      wrapper: makeWrapper(queryClient),
    });

    await act(async () => {
      await result.current.mutate('task-1');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
