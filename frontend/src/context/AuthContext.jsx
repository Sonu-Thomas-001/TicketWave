import { createContext, useContext, useReducer, useCallback } from 'react';
import { api } from '@/lib/api';

const AuthContext = createContext(null);

const initialState = {
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: false,
};

function authReducer(state, action) {
  switch (action.type) {
    case 'LOGIN_START':
      return { ...state, isLoading: true };
    case 'LOGIN_SUCCESS':
      return {
        ...state,
        user: action.payload.user,
        token: action.payload.token,
        isAuthenticated: true,
        isLoading: false,
      };
    case 'AUTH_ERROR':
      return { ...initialState };
    case 'LOGOUT':
      return { ...initialState };
    case 'UPDATE_USER':
      return { ...state, user: { ...state.user, ...action.payload } };
    default:
      return state;
  }
}

export function AuthProvider({ children }) {
  const [state, dispatch] = useReducer(authReducer, initialState, () => {
    const token = localStorage.getItem('tw-token');
    const user = localStorage.getItem('tw-user');
    if (token && user) {
      return {
        ...initialState,
        token,
        user: JSON.parse(user),
        isAuthenticated: true,
      };
    }
    return initialState;
  });

  const login = useCallback(async (email, password) => {
    dispatch({ type: 'LOGIN_START' });
    try {
      const res = await api.post('/auth/login', { email, password });
      const { token, firstName, lastName } = res.data;
      const user = {
        id: res.data.id || email,
        name: `${firstName} ${lastName}`,
        email: res.data.email,
        firstName,
        lastName,
        role: res.data.role || 'USER',
        avatar: '/images/avatar-default.svg',
      };
      localStorage.setItem('tw-token', token);
      localStorage.setItem('tw-user', JSON.stringify(user));
      dispatch({ type: 'LOGIN_SUCCESS', payload: { user, token } });
      return user;
    } catch (err) {
      dispatch({ type: 'AUTH_ERROR' });
      throw err;
    }
  }, []);

  const register = useCallback(async ({ email, password, firstName, lastName, phoneNumber }) => {
    dispatch({ type: 'LOGIN_START' });
    try {
      const res = await api.post('/auth/register', { email, password, firstName, lastName, phoneNumber });
      const { token } = res.data;
      const user = {
        id: res.data.id || email,
        name: `${res.data.firstName} ${res.data.lastName}`,
        email: res.data.email,
        firstName: res.data.firstName,
        lastName: res.data.lastName,
        role: 'USER',
        avatar: '/images/avatar-default.svg',
      };
      localStorage.setItem('tw-token', token);
      localStorage.setItem('tw-user', JSON.stringify(user));
      dispatch({ type: 'LOGIN_SUCCESS', payload: { user, token } });
      return user;
    } catch (err) {
      dispatch({ type: 'AUTH_ERROR' });
      throw err;
    }
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('tw-token');
    localStorage.removeItem('tw-user');
    dispatch({ type: 'LOGOUT' });
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
