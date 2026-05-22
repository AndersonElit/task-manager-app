import { describe, it, expect } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { http, HttpResponse } from 'msw';
import React from 'react';
import { server } from '../test/mocks/server';
import { useTasks } from './useTasks';
import { mockTask } from '../test/mocks/handlers';

function makeWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return React.createElement(QueryClientProvider, { client: queryClient }, children);
  };
}

describe('useTasks', () => {
  // TC-HK-01: returns task list on successful fetch
  it('TC-HK-01: returns the list of tasks when the request succeeds', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useTasks(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toHaveLength(1);
    expect(result.current.data![0].id).toBe(mockTask.id);
  });

  // TC-HK-02: isLoading is true before data arrives
  it('TC-HK-02: isLoading is true while data is being fetched', () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useTasks(), {
      wrapper: makeWrapper(queryClient),
    });

    expect(result.current.isLoading).toBe(true);
  });

  // TC-HK-03: isError is true when the request fails
  it('TC-HK-03: isError is true when the server returns an error', async () => {
    server.use(
      http.get('*/api/v1/tasks', () => new HttpResponse(null, { status: 500 }))
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useTasks(), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });

  // TC-HK-04: status filter is forwarded to the API
  it('TC-HK-04: passes the status filter to the API request', async () => {
    let capturedUrl: URL | null = null;

    server.use(
      http.get('*/api/v1/tasks', ({ request }) => {
        capturedUrl = new URL(request.url);
        return HttpResponse.json([mockTask]);
      })
    );

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { result } = renderHook(() => useTasks('pendiente'), {
      wrapper: makeWrapper(queryClient),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(capturedUrl).not.toBeNull();
    expect(capturedUrl!.searchParams.get('status')).toBe('pendiente');
  });
});
