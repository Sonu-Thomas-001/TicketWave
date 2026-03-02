import { Component } from 'react';
import { motion } from 'framer-motion';
import { AlertTriangle, RefreshCcw, Home } from 'lucide-react';
import { Button } from './Button';

export class ErrorBoundary extends Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, errorInfo) {
    console.error('ErrorBoundary caught:', error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-[400px] flex items-center justify-center p-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="max-w-md text-center space-y-6"
          >
            <div className="inline-flex items-center justify-center h-16 w-16 rounded-2xl bg-destructive/10 mx-auto">
              <AlertTriangle className="h-8 w-8 text-destructive" />
            </div>
            <div>
              <h2 className="text-xl font-bold mb-2">Something went wrong</h2>
              <p className="text-muted-foreground text-sm">
                {this.props.fallbackMessage || 'An unexpected error occurred. Please try again.'}
              </p>
            </div>
            <div className="flex items-center justify-center gap-3">
              <Button
                variant="outline"
                onClick={() => window.location.href = '/'}
                className="gap-2"
              >
                <Home className="h-4 w-4" /> Go Home
              </Button>
              <Button
                onClick={() => {
                  this.setState({ hasError: false, error: null });
                  window.location.reload();
                }}
                className="gap-2"
              >
                <RefreshCcw className="h-4 w-4" /> Try Again
              </Button>
            </div>
          </motion.div>
        </div>
      );
    }

    return this.props.children;
  }
}

export function EmptyState({ icon: Icon, title, description, action, actionLabel, className }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      className={`flex flex-col items-center justify-center py-16 px-4 text-center ${className || ''}`}
    >
      {Icon && (
        <div className="inline-flex items-center justify-center h-16 w-16 rounded-2xl bg-muted mb-4">
          <Icon className="h-8 w-8 text-muted-foreground/50" />
        </div>
      )}
      <h3 className="text-lg font-semibold mb-1">{title}</h3>
      {description && <p className="text-muted-foreground text-sm max-w-sm mb-6">{description}</p>}
      {action && (
        <Button onClick={action} variant="outline">
          {actionLabel || 'Try Again'}
        </Button>
      )}
    </motion.div>
  );
}

export function LoadingState({ message = 'Loading...', className }) {
  return (
    <div className={`flex flex-col items-center justify-center py-16 ${className || ''}`}>
      <div className="relative h-12 w-12 mb-4">
        <div className="absolute inset-0 rounded-full border-4 border-muted" />
        <div className="absolute inset-0 rounded-full border-4 border-primary border-t-transparent animate-spin" />
      </div>
      <p className="text-sm text-muted-foreground">{message}</p>
    </div>
  );
}
