import { useState, useRef, useEffect } from 'react';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Button } from './Button';

const MONTHS = ['January','February','March','April','May','June','July','August','September','October','November','December'];
const DAYS = ['Su','Mo','Tu','We','Th','Fr','Sa'];

function getDaysInMonth(year, month) {
  return new Date(year, month + 1, 0).getDate();
}

function getFirstDayOfMonth(year, month) {
  return new Date(year, month, 1).getDay();
}

export function DatePicker({ value, onChange, placeholder = 'Select date', className, minDate, ...props }) {
  const [open, setOpen] = useState(false);
  const [viewDate, setViewDate] = useState(() => {
    const d = value ? new Date(value) : new Date();
    return { year: d.getFullYear(), month: d.getMonth() };
  });
  const ref = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const daysInMonth = getDaysInMonth(viewDate.year, viewDate.month);
  const firstDay = getFirstDayOfMonth(viewDate.year, viewDate.month);

  const prevMonth = () => {
    setViewDate((v) => {
      if (v.month === 0) return { year: v.year - 1, month: 11 };
      return { ...v, month: v.month - 1 };
    });
  };

  const nextMonth = () => {
    setViewDate((v) => {
      if (v.month === 11) return { year: v.year + 1, month: 0 };
      return { ...v, month: v.month + 1 };
    });
  };

  const selectDate = (day) => {
    const selected = new Date(viewDate.year, viewDate.month, day);
    onChange?.(selected.toISOString().split('T')[0]);
    setOpen(false);
  };

  const isSelected = (day) => {
    if (!value) return false;
    const d = new Date(value);
    return d.getFullYear() === viewDate.year && d.getMonth() === viewDate.month && d.getDate() === day;
  };

  const isToday = (day) => {
    const today = new Date();
    return today.getFullYear() === viewDate.year && today.getMonth() === viewDate.month && today.getDate() === day;
  };

  const isDisabled = (day) => {
    if (!minDate) return false;
    const d = new Date(viewDate.year, viewDate.month, day);
    return d < new Date(minDate);
  };

  const displayValue = value
    ? new Date(value).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })
    : null;

  return (
    <div ref={ref} className={cn('relative', className)} {...props}>
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className={cn(
          'flex h-10 w-full items-center gap-2 rounded-xl border border-input bg-background px-3 py-2 text-sm',
          'ring-offset-background focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2',
          'transition-all duration-200 hover:border-primary/50',
          !displayValue && 'text-muted-foreground'
        )}
      >
        <CalendarIcon className="h-4 w-4 text-muted-foreground" />
        {displayValue || placeholder}
      </button>

      {open && (
        <div className="absolute top-full left-0 mt-2 z-50 w-72 rounded-2xl glass-card-strong p-4 animate-slide-up-fade shadow-xl">
          {/* Header */}
          <div className="flex items-center justify-between mb-4">
            <button type="button" onClick={prevMonth} className="p-1 rounded-lg hover:bg-accent transition-colors">
              <ChevronLeft className="h-4 w-4" />
            </button>
            <span className="text-sm font-semibold">
              {MONTHS[viewDate.month]} {viewDate.year}
            </span>
            <button type="button" onClick={nextMonth} className="p-1 rounded-lg hover:bg-accent transition-colors">
              <ChevronRight className="h-4 w-4" />
            </button>
          </div>

          {/* Day names */}
          <div className="grid grid-cols-7 gap-1 mb-2">
            {DAYS.map((d) => (
              <div key={d} className="text-center text-2xs font-medium text-muted-foreground py-1">
                {d}
              </div>
            ))}
          </div>

          {/* Days grid */}
          <div className="grid grid-cols-7 gap-1">
            {Array.from({ length: firstDay }).map((_, i) => (
              <div key={`empty-${i}`} />
            ))}
            {Array.from({ length: daysInMonth }).map((_, i) => {
              const day = i + 1;
              const disabled = isDisabled(day);
              return (
                <button
                  key={day}
                  type="button"
                  disabled={disabled}
                  onClick={() => selectDate(day)}
                  className={cn(
                    'h-8 w-8 rounded-lg text-sm font-medium transition-all duration-150 mx-auto',
                    'hover:bg-accent hover:text-accent-foreground',
                    isSelected(day) && 'gradient-primary text-white shadow-md hover:opacity-90',
                    isToday(day) && !isSelected(day) && 'border border-primary text-primary',
                    disabled && 'opacity-30 cursor-not-allowed hover:bg-transparent'
                  )}
                >
                  {day}
                </button>
              );
            })}
          </div>

          {/* Today button */}
          <div className="mt-3 pt-3 border-t">
            <Button
              variant="ghost"
              size="sm"
              className="w-full text-xs"
              onClick={() => {
                const today = new Date();
                setViewDate({ year: today.getFullYear(), month: today.getMonth() });
                selectDate(today.getDate());
              }}
            >
              Today
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
