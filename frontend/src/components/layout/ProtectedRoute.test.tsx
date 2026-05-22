import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import React from 'react';
import ProtectedRoute from './ProtectedRoute';
import * as useAuthModule from '../../features/auth/useAuth';

vi.mock('../../features/auth/useAuth', async (importOriginal) => {
  const actual = await importOriginal<typeof useAuthModule>();
  return { ...actual, useAuth: vi.fn() };
});

const mockedUseAuth = vi.mocked(useAuthModule.useAuth);

function renderWithRoutes(authenticated: boolean) {
  mockedUseAuth.mockReturnValue({
    isAuthenticated: authenticated,
    username: authenticated ? 'user1' : null,
    login: vi.fn(),
    logout: vi.fn(),
  });

  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route element={<ProtectedRoute />}>
          <Route path="/protected" element={<div>Contenido protegido</div>} />
        </Route>
        <Route path="/login" element={<div>Página de login</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('ProtectedRoute', () => {
  // TC-PR-01: authenticated user sees the protected outlet content
  it('TC-PR-01: renders the Outlet when the user is authenticated', () => {
    renderWithRoutes(true);
    expect(screen.getByText('Contenido protegido')).toBeInTheDocument();
    expect(screen.queryByText('Página de login')).not.toBeInTheDocument();
  });

  // TC-PR-02: unauthenticated user is redirected to /login
  it('TC-PR-02: redirects to /login when the user is not authenticated', () => {
    renderWithRoutes(false);
    expect(screen.getByText('Página de login')).toBeInTheDocument();
    expect(screen.queryByText('Contenido protegido')).not.toBeInTheDocument();
  });
});
