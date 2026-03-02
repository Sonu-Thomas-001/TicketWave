import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import { MapPin, Calendar, Users, Search, ArrowRight, ArrowRightLeft } from 'lucide-react';
import { Button, Input, DatePicker } from '@/components/ui';
import {
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui';
import { cn } from '@/lib/utils';

export default function SearchCard({ className, compact = false }) {
  const navigate = useNavigate();
  const [origin, setOrigin] = useState('');
  const [destination, setDestination] = useState('');
  const [date, setDate] = useState('');
  const [travelClass, setTravelClass] = useState('economy');

  const handleSwap = () => {
    setOrigin(destination);
    setDestination(origin);
  };

  const handleSearch = (e) => {
    e.preventDefault();
    const params = new URLSearchParams();
    if (origin) params.set('from', origin);
    if (destination) params.set('to', destination);
    if (date) params.set('date', date);
    if (travelClass) params.set('class', travelClass);
    navigate(`/search?${params.toString()}`);
  };

  return (
    <motion.div
      initial={{ opacity: 0, y: 30 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      className={cn(
        'glass-card-strong rounded-3xl p-6 md:p-8 shadow-2xl shadow-indigo-500/10',
        className
      )}
    >
      <form onSubmit={handleSearch}>
        <div className={cn(
          'grid gap-4',
          compact ? 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-5' : 'grid-cols-1 md:grid-cols-2 lg:grid-cols-5'
        )}>
          {/* Origin */}
          <div className="relative lg:col-span-1">
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">From</label>
            <div className="relative">
              <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-indigo-500" />
              <Input
                placeholder="Origin city"
                value={origin}
                onChange={(e) => setOrigin(e.target.value)}
                className="pl-10 h-12 bg-background/60"
              />
            </div>
          </div>

          {/* Swap button */}
          <div className="flex items-end justify-center lg:col-span-0 lg:absolute lg:left-[calc(20%-12px)] lg:bottom-[calc(50%-12px)]">
            <button
              type="button"
              onClick={handleSwap}
              className="flex items-center justify-center h-8 w-8 rounded-full border bg-background hover:bg-accent transition-colors shadow-sm lg:hidden"
            >
              <ArrowRightLeft className="h-3.5 w-3.5" />
            </button>
          </div>

          {/* Destination */}
          <div className="relative lg:col-span-1">
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">To</label>
            <div className="relative">
              <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-rose-500" />
              <Input
                placeholder="Destination city"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                className="pl-10 h-12 bg-background/60"
              />
            </div>
          </div>

          {/* Date */}
          <div className="lg:col-span-1">
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Date</label>
            <DatePicker
              value={date}
              onChange={setDate}
              placeholder="Travel date"
              minDate={new Date().toISOString().split('T')[0]}
              className="[&>button]:h-12 [&>button]:bg-background/60"
            />
          </div>

          {/* Class */}
          <div className="lg:col-span-1">
            <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Class</label>
            <Select value={travelClass} onValueChange={setTravelClass}>
              <SelectTrigger className="h-12 bg-background/60">
                <SelectValue placeholder="Select class" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="economy">Economy</SelectItem>
                <SelectItem value="premium">Premium Economy</SelectItem>
                <SelectItem value="business">Business</SelectItem>
                <SelectItem value="first">First Class</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Search Button */}
          <div className="flex items-end lg:col-span-1">
            <Button type="submit" size="lg" className="w-full h-12 rounded-xl gap-2 text-base">
              <Search className="h-5 w-5" />
              Search
            </Button>
          </div>
        </div>
      </form>
    </motion.div>
  );
}
