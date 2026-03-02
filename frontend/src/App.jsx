import { ThemeProvider } from '@/context/ThemeContext';
import { AuthProvider } from '@/context/AuthContext';
import { ToastProvider, TooltipProvider } from '@/components/ui';
import { ErrorBoundary } from '@/components/ui';
import AppRouter from '@/router';

export default function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider defaultTheme="light" storageKey="tw-theme">
        <TooltipProvider>
          <ToastProvider>
            <AuthProvider>
              <AppRouter />
            </AuthProvider>
          </ToastProvider>
        </TooltipProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}
