import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';
import AuthProvider from './AuthProvider';
import { useAuth } from './useAuth';
import * as authApi from '../../api/auth.api';

vi.mock('../../api/auth.api', () => ({
  login: vi.fn(),
}));

const mockedLogin = vi.mocked(authApi.login);

// Mock useNavigate so we don't need a full router context to track navigation
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function wrapper({ children }: { children: React.ReactNode }) {
  return React.createElement(
    MemoryRouter,
    { initialEntries: ['/'] },
    React.createElement(AuthProvider, null, children)
  );
}

describe('AuthProvider', () => {
  beforeEach(() => {
    sessionStorage.clear();
    mockNavigate.mockReset();
    mockedLogin.mockReset();
  });

  // TC-AU-01: successful login stores token, sets isAuthenticated, navigates to /tasks
  it('TC-AU-01: login stores id_token and navigates to /tasks on success', async () => {
    mockedLogin.mockResolvedValue('fake-token');

    const { result } = renderHook(() => useAuth(), { wrapper });

    await act(async () => {
      await result.current.login('user', 'pass');
    });

    expect(sessionStorage.getItem('id_token')).toBe('fake-token');
    expect(sessionStorage.getItem('username')).toBe('user');
    expect(mockNavigate).toHaveBeenCalledWith('/tasks');
  });

  // TC-AU-02: isAuthenticated reflects sessionStorage id_token
  it('TC-AU-02: isAuthenticated is true when id_token is present in sessionStorage', async () => {
    sessionStorage.setItem('id_token', 'existing-token');

    const { result } = renderHook(() => useAuth(), { wrapper });

    // isAuthenticated is derived synchronously from sessionStorage at render time
    expect(result.current.isAuthenticated).toBe(true);
  });

  // TC-AU-03: isAuthenticated is false when no id_token
  it('TC-AU-03: isAuthenticated is false when id_token is absent', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    expect(result.current.isAuthenticated).toBe(false);
  });

  // TC-AU-04: logout clears sessionStorage and navigates to /login
  it('TC-AU-04: logout removes id_token and username and navigates to /login', async () => {
    sessionStorage.setItem('id_token', 'some-token');
    sessionStorage.setItem('username', 'user1');

    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.logout();
    });

    expect(sessionStorage.getItem('id_token')).toBeNull();
    expect(sessionStorage.getItem('username')).toBeNull();
    expect(mockNavigate).toHaveBeenCalledWith('/login');
  });

  // TC-AU-05: failed login propagates the error without storing anything
  it('TC-AU-05: login rejects and does not store token when API throws', async () => {
    mockedLogin.mockRejectedValue(new Error('Credentials error'));

    const { result } = renderHook(() => useAuth(), { wrapper });

    await expect(
      act(async () => {
        await result.current.login('bad-user', 'bad-pass');
      })
    ).rejects.toThrow('Credentials error');

    expect(sessionStorage.getItem('id_token')).toBeNull();
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
