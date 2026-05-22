import { useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from './useAuth';
import { login as loginApi } from '../../api/auth.api';

export default function AuthProvider({ children }: { children: React.ReactNode }) {
  const navigate = useNavigate();
  const [username, setUsername] = useState<string | null>(
    () => sessionStorage.getItem('username')
  );

  const isAuthenticated = !!sessionStorage.getItem('id_token');

  const login = useCallback(async (user: string, password: string) => {
    const token = await loginApi(user, password);
    sessionStorage.setItem('id_token', token);
    sessionStorage.setItem('username', user);
    setUsername(user);
    navigate('/tasks');
  }, [navigate]);

  const logout = useCallback(() => {
    sessionStorage.removeItem('id_token');
    sessionStorage.removeItem('username');
    setUsername(null);
    navigate('/login');
  }, [navigate]);

  return (
    <AuthContext.Provider value={{ isAuthenticated, username, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}
