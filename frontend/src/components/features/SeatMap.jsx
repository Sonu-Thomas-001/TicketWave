import { useState, useMemo, useCallback, useEffect, memo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';
import { Badge, Button } from '@/components/ui';
import { Tooltip, TooltipTrigger, TooltipContent } from '@/components/ui';
import { formatCurrency } from '@/lib/utils';
import { Timer, AlertTriangle } from 'lucide-react';

const sectionColors = {
  VIP: 'bg-amber-500',
  Premium: 'bg-indigo-500',
  Standard: 'bg-blue-500',
  Balcony: 'bg-emerald-500',
};

const statusColors = {
  available: 'bg-gray-200 dark:bg-gray-700 border-gray-300 dark:border-gray-600 hover:bg-blue-200 dark:hover:bg-blue-900 hover:border-blue-400 cursor-pointer',
  selected: 'bg-blue-500 text-white border-blue-600 shadow-md shadow-blue-500/30 cursor-pointer',
  held: 'bg-amber-400/30 border-amber-500/50 cursor-not-allowed',
  sold: 'bg-red-400/30 border-red-400/50 cursor-not-allowed opacity-60',
};

const statusLabels = {
  available: 'Available',
  selected: 'Selected',
  held: 'Held',
  sold: 'Booked',
};

/* Memoized individual seat for performance with 200+ seats */
const Seat = memo(function Seat({ seat, isSelected, onToggle }) {
  const seatStatus = isSelected ? 'selected' : seat.status;
  const isInteractive = seat.status === 'available' || isSelected;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <motion.button
          whileHover={isInteractive ? { scale: 1.2, zIndex: 10 } : {}}
          whileTap={isInteractive ? { scale: 0.9 } : {}}
          onClick={() => onToggle(seat)}
          disabled={!isInteractive}
          className={cn(
            'h-7 w-7 rounded-md border text-[10px] font-medium transition-colors duration-200 flex items-center justify-center relative',
            statusColors[seatStatus]
          )}
          aria-label={`Seat ${seat.label}, ${statusLabels[seatStatus]}, ${formatCurrency(seat.price)}`}
        >
          {seat.number}
          {isSelected && (
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              className="absolute -top-1 -right-1 h-2.5 w-2.5 rounded-full bg-blue-500 border-2 border-card"
            />
          )}
        </motion.button>
      </TooltipTrigger>
      <TooltipContent side="top" className="text-xs">
        <div className="space-y-0.5">
          <p className="font-semibold">Seat {seat.label}</p>
          <p>{seat.section} — {formatCurrency(seat.price)}</p>
          <p className="text-muted-foreground capitalize">{statusLabels[seatStatus]}</p>
        </div>
      </TooltipContent>
    </Tooltip>
  );
});

/* Countdown timer for seat hold */
function CountdownTimer({ durationMinutes = 10 }) {
  const [timeLeft, setTimeLeft] = useState(durationMinutes * 60);

  useEffect(() => {
    if (timeLeft <= 0) return;
    const interval = setInterval(() => {
      setTimeLeft((t) => Math.max(0, t - 1));
    }, 1000);
    return () => clearInterval(interval);
  }, [timeLeft]);

  const minutes = Math.floor(timeLeft / 60);
  const seconds = timeLeft % 60;
  const isLow = timeLeft < 120;
  const isUrgent = timeLeft < 60;

  return (
    <motion.div
      className={cn(
        'flex items-center gap-2 px-4 py-2.5 rounded-xl border text-sm font-medium transition-colors',
        isUrgent ? 'bg-red-500/10 border-red-500/30 text-red-600 dark:text-red-400' :
        isLow ? 'bg-amber-500/10 border-amber-500/30 text-amber-600 dark:text-amber-400' :
        'bg-blue-500/10 border-blue-500/30 text-blue-600 dark:text-blue-400'
      )}
    >
      <Timer className="h-4 w-4" />
      <span className={isUrgent ? 'animate-count-pulse' : ''}>
        {String(minutes).padStart(2, '0')}:{String(seconds).padStart(2, '0')}
      </span>
      <span className="text-xs font-normal opacity-70">remaining</span>
      {isUrgent && <AlertTriangle className="h-3.5 w-3.5 animate-pulse" />}
    </motion.div>
  );
}

export default function SeatMap({ seatMap, onSelectionChange, showCountdown = true }) {
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [activeSection, setActiveSection] = useState('Standard');
  const sections = Object.keys(seatMap);

  const selectedIds = useMemo(() => new Set(selectedSeats.map((s) => s.id)), [selectedSeats]);

  const toggleSeat = useCallback((seat) => {
    if (seat.status !== 'available' && !selectedIds.has(seat.id)) return;

    setSelectedSeats((prev) => {
      const exists = prev.find((s) => s.id === seat.id);
      let next;
      if (exists) {
        next = prev.filter((s) => s.id !== seat.id);
      } else {
        if (prev.length >= 8) return prev;
        next = [...prev, seat];
      }
      onSelectionChange?.(next);
      return next;
    });
  }, [onSelectionChange, selectedIds]);

  const sectionSeats = useMemo(() => seatMap[activeSection] || [], [seatMap, activeSection]);
  const rows = useMemo(() => {
    const grouped = {};
    sectionSeats.forEach((seat) => {
      if (!grouped[seat.row]) grouped[seat.row] = [];
      grouped[seat.row].push(seat);
    });
    return grouped;
  }, [sectionSeats]);

  const totalPrice = useMemo(
    () => selectedSeats.reduce((sum, s) => sum + s.price, 0),
    [selectedSeats]
  );

  const seatCounts = useMemo(() => {
    const counts = { available: 0, held: 0, sold: 0 };
    sectionSeats.forEach((s) => { if (counts[s.status] !== undefined) counts[s.status]++; });
    return counts;
  }, [sectionSeats]);

  return (
    <div className="space-y-6">
      {/* Timer + Legend row */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        {/* Legend */}
        <div className="flex items-center gap-4 flex-wrap text-sm">
          <div className="flex items-center gap-1.5">
            <div className="h-4 w-4 rounded bg-gray-200 dark:bg-gray-700 border border-gray-300 dark:border-gray-600" />
            <span className="text-muted-foreground">Available ({seatCounts.available})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="h-4 w-4 rounded bg-blue-500" />
            <span className="text-muted-foreground">Selected ({selectedSeats.length})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="h-4 w-4 rounded bg-amber-400/40 border border-amber-500/50" />
            <span className="text-muted-foreground">Held ({seatCounts.held})</span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="h-4 w-4 rounded bg-red-400/40 border border-red-400/50 opacity-60" />
            <span className="text-muted-foreground">Booked ({seatCounts.sold})</span>
          </div>
        </div>

        {/* Countdown */}
        {showCountdown && selectedSeats.length > 0 && <CountdownTimer durationMinutes={10} />}
      </div>

      {/* Section Tabs */}
      <div className="flex items-center gap-2 flex-wrap">
        {sections.map((section) => (
          <Button
            key={section}
            variant={activeSection === section ? 'default' : 'outline'}
            size="sm"
            onClick={() => setActiveSection(section)}
            className="gap-2"
          >
            <div className={cn('h-2.5 w-2.5 rounded-full', sectionColors[section])} />
            {section}
            <span className="text-xs opacity-70">
              {formatCurrency(seatMap[section]?.[0]?.price || 0)}
            </span>
          </Button>
        ))}
      </div>

      {/* Stage indicator */}
      <div className="flex justify-center">
        <div className="w-64 h-8 rounded-t-full bg-gradient-to-b from-indigo-500/20 to-transparent border-t border-l border-r border-indigo-500/30 flex items-center justify-center">
          <span className="text-xs text-muted-foreground font-medium uppercase tracking-wider">Stage</span>
        </div>
      </div>

      {/* Seat Grid — scrollable for mobile */}
      <div className="overflow-x-auto pb-4 -mx-4 px-4">
        <div className="min-w-[500px] space-y-1.5">
          {Object.entries(rows).map(([row, seats]) => (
            <div key={row} className="flex items-center gap-1 justify-center">
              <span className="w-6 text-center text-xs font-medium text-muted-foreground">{row}</span>
              <div className="flex gap-1">
                {/* Aisle gap in the middle */}
                {seats.map((seat, i) => (
                  <div key={seat.id} className={cn('flex', i === Math.floor(seats.length / 2) && 'ml-3')}>
                    <Seat
                      seat={seat}
                      isSelected={selectedIds.has(seat.id)}
                      onToggle={toggleSeat}
                    />
                  </div>
                ))}
              </div>
              <span className="w-6 text-center text-xs font-medium text-muted-foreground">{row}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Selection Summary — Sticky on mobile */}
      <AnimatePresence>
        {selectedSeats.length > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 20 }}
            className="glass-card-strong rounded-2xl p-5 sticky bottom-4"
          >
            <div className="flex items-center justify-between flex-wrap gap-4">
              <div>
                <p className="text-sm text-muted-foreground mb-1.5">
                  {selectedSeats.length} seat{selectedSeats.length > 1 ? 's' : ''} selected
                  <span className="text-xs ml-1">(max 8)</span>
                </p>
                <div className="flex items-center gap-1.5 flex-wrap">
                  {selectedSeats.map((s) => (
                    <motion.div
                      key={s.id}
                      initial={{ scale: 0 }}
                      animate={{ scale: 1 }}
                      exit={{ scale: 0 }}
                    >
                      <Badge
                        variant="secondary"
                        className="text-xs cursor-pointer hover:bg-destructive/20 transition-colors"
                        onClick={() => toggleSeat(s)}
                      >
                        {s.label} ({s.section}) — {formatCurrency(s.price)}
                        <span className="ml-1 text-muted-foreground">×</span>
                      </Badge>
                    </motion.div>
                  ))}
                </div>
              </div>
              <div className="text-right">
                <p className="text-sm text-muted-foreground">Total</p>
                <motion.p
                  key={totalPrice}
                  initial={{ scale: 1.1 }}
                  animate={{ scale: 1 }}
                  className="text-2xl font-bold text-gradient"
                >
                  {formatCurrency(totalPrice)}
                </motion.p>
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
