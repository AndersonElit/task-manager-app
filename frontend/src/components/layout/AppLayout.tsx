import { Outlet } from 'react-router-dom';
import { useAuth } from '../../features/auth/useAuth';
import Button from '../ui/Button';

export default function AppLayout() {
  const { username, logout } = useAuth();

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3">
          <span className="text-lg font-semibold text-indigo-600">Task Manager</span>
          <div className="flex items-center gap-3">
            <span className="text-sm text-gray-600">{username}</span>
            <Button variant="ghost" size="sm" onClick={logout}>
              Cerrar sesión
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-5xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
