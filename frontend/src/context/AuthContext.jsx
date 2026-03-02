import { createContext, useContext, useReducer, useCallback } from 'react';

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
    // Simulated auth — replace with real API call
    const mockUser = { id: '1', name: 'Alex Johnson', email, role: 'USER', avatar: '/images/avatar-default.svg' };
    const mockToken = 'mock-jwt-token';
    localStorage.setItem('tw-token', mockToken);
    localStorage.setItem('tw-user', JSON.stringify(mockUser));
    dispatch({ type: 'LOGIN_SUCCESS', payload: { user: mockUser, token: mockToken } });
    return mockUser;
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('tw-token');
    localStorage.removeItem('tw-user');
    dispatch({ type: 'LOGOUT' });
  }, []);

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
