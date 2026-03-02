import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';

export function Stepper({ steps, currentStep, className }) {
  return (
    <div className={cn('flex items-center justify-between', className)}>
      {steps.map((step, i) => {
        const isCompleted = i < currentStep;
        const isCurrent = i === currentStep;
        const isLast = i === steps.length - 1;

        return (
          <div key={step.label || i} className="flex items-center flex-1 last:flex-none">
            <div className="flex items-center gap-3">
              {/* Step circle */}
              <div
                className={cn(
                  'flex items-center justify-center h-10 w-10 rounded-full text-sm font-semibold transition-all duration-300 shrink-0',
                  isCompleted && 'gradient-primary text-white shadow-md shadow-indigo-500/25',
                  isCurrent && 'gradient-primary text-white shadow-lg shadow-indigo-500/30 ring-4 ring-indigo-500/20',
                  !isCompleted && !isCurrent && 'bg-muted text-muted-foreground'
                )}
              >
                {isCompleted ? <Check className="h-5 w-5" /> : i + 1}
              </div>

              {/* Step label */}
              <div className="hidden sm:block">
                <p
                  className={cn(
                    'text-sm font-medium transition-colors',
                    (isCompleted || isCurrent) ? 'text-foreground' : 'text-muted-foreground'
                  )}
                >
                  {step.label}
                </p>
                {step.description && (
                  <p className="text-2xs text-muted-foreground">{step.description}</p>
                )}
              </div>
            </div>

            {/* Connector line */}
            {!isLast && (
              <div className="flex-1 mx-4 h-0.5 rounded-full overflow-hidden bg-muted">
                <div
                  className={cn(
                    'h-full rounded-full transition-all duration-500 gradient-primary',
                    isCompleted ? 'w-full' : 'w-0'
                  )}
                />
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}
