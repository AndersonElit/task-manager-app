import LoginForm from '../features/auth/LoginForm';

export default function LoginPage() {
  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="w-full max-w-sm rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
        <h1 className="mb-1 text-2xl font-bold text-gray-900">Task Manager</h1>
        <p className="mb-6 text-sm text-gray-500">Inicia sesión para continuar</p>
        <LoginForm />
      </div>
    </div>
  );
}
