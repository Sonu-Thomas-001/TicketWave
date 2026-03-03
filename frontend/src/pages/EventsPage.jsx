import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Search, SlidersHorizontal, Grid3X3, List, X, Loader2 } from 'lucide-react';
import { Button, Input, Badge, Card, Separator } from '@/components/ui';
import { EventCard } from '@/components/shared';
import { api } from '@/lib/api';
import { staggerContainer, fadeInUp } from '@/lib/animations';

/** Map a schedule API object into the shape EventCard expects. */
function mapScheduleToEvent(s) {
  return {
    id: s.scheduleId,
    title: `${s.originCity} → ${s.destinationCity}`,
    venue: s.vehicleNumber,
    city: s.originCity,
    date: s.departureTime,
    imageUrl: `/images/event-${(s.transportMode || 'BUS').toLowerCase()}.svg`,
    price: { min: Number(s.dynamicPrice || s.baseFare), max: Number(s.dynamicPrice || s.baseFare) * 1.5 },
    totalSeats: s.totalSeats,
    availableSeats: s.availableSeats,
    tags: [s.transportMode || 'BUS'],
    category: (s.transportMode || 'BUS').toLowerCase(),
    featured: (s.availabilityPercentage ?? 100) < 30,
  };
}

const browseCategories = [
  { id: 'all', label: 'All Schedules' },
  { id: 'bus', label: 'Bus' },
  { id: 'train', label: 'Train' },
  { id: 'flight', label: 'Flight' },
];

export default function EventsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [view, setView] = useState('grid');
  const [schedules, setSchedules] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [filters, setFilters] = useState({
    query: searchParams.get('q') || '',
    category: searchParams.get('cat') || 'all',
    priceRange: 'all',
    sortBy: 'date',
  });

  // Fetch all schedules from API on mount
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    api.get('/schedules/browse')
      .then((res) => {
        if (!cancelled) {
          const data = res.data ?? res;
          setSchedules(Array.isArray(data) ? data.map(mapScheduleToEvent) : []);
        }
      })
      .catch((err) => {
        if (!cancelled) setError(err.message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  const filteredEvents = useMemo(() => {
    let events = [...schedules];

    if (filters.query) {
      const q = filters.query.toLowerCase();
      events = events.filter(
        (e) =>
          e.title.toLowerCase().includes(q) ||
          e.venue.toLowerCase().includes(q) ||
          e.city.toLowerCase().includes(q)
      );
    }
    if (filters.category !== 'all') {
      events = events.filter((e) => e.category.toLowerCase() === filters.category);
    }
    if (filters.priceRange === 'budget') events = events.filter((e) => e.price.min < 100);
    else if (filters.priceRange === 'mid') events = events.filter((e) => e.price.min >= 100 && e.price.min < 300);
    else if (filters.priceRange === 'premium') events = events.filter((e) => e.price.min >= 300);

    if (filters.sortBy === 'price') events.sort((a, b) => a.price.min - b.price.min);
    else if (filters.sortBy === 'name') events.sort((a, b) => a.title.localeCompare(b.title));
    else events.sort((a, b) => new Date(a.date) - new Date(b.date));

    return events;
  }, [filters, schedules]);

  const updateFilter = (key, value) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
  };

  const clearFilters = () => {
    setFilters({ query: '', category: 'all', priceRange: 'all', sortBy: 'date' });
    setSearchParams({});
  };

  const hasActiveFilters = filters.query || filters.category !== 'all' || filters.priceRange !== 'all';

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-6 py-8 space-y-8">
      {/* Header */}
      <motion.div {...fadeInUp}>
        <h1 className="text-3xl font-bold mb-2">Browse Schedules</h1>
        <p className="text-muted-foreground">
          {loading ? 'Loading...' : `${filteredEvents.length} schedule${filteredEvents.length !== 1 ? 's' : ''} found`}
        </p>
      </motion.div>

      {/* Search & Filters */}
      <motion.div {...fadeInUp} transition={{ delay: 0.1 }}>
        <Card className="p-4 space-y-4">
          {/* Search row */}
          <div className="flex flex-col sm:flex-row gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search events..."
                value={filters.query}
                onChange={(e) => updateFilter('query', e.target.value)}
                className="pl-9"
              />
            </div>
            <div className="flex items-center gap-2">
              <select
                value={filters.sortBy}
                onChange={(e) => updateFilter('sortBy', e.target.value)}
                className="h-10 px-3 rounded-xl border border-input bg-background text-sm"
              >
                <option value="date">Sort by Date</option>
                <option value="price">Sort by Price</option>
                <option value="name">Sort by Name</option>
              </select>
              <div className="flex border rounded-xl">
                <Button
                  variant={view === 'grid' ? 'secondary' : 'ghost'}
                  size="icon"
                  onClick={() => setView('grid')}
                  className="rounded-r-none"
                >
                  <Grid3X3 className="h-4 w-4" />
                </Button>
                <Button
                  variant={view === 'list' ? 'secondary' : 'ghost'}
                  size="icon"
                  onClick={() => setView('list')}
                  className="rounded-l-none"
                >
                  <List className="h-4 w-4" />
                </Button>
              </div>
            </div>
          </div>

          {/* Category chips */}
          <div className="flex items-center gap-2 flex-wrap">
            {browseCategories.map((cat) => (
              <Badge
                key={cat.id}
                variant={filters.category === cat.id ? 'default' : 'secondary'}
                className="cursor-pointer transition-all hover:scale-105"
                onClick={() => updateFilter('category', cat.id)}
              >
                {cat.label}
              </Badge>
            ))}
          </div>

          {/* Price chips */}
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-sm text-muted-foreground mr-1">Price:</span>
            {[
              { id: 'all', label: 'Any' },
              { id: 'budget', label: 'Under $100' },
              { id: 'mid', label: '$100–$300' },
              { id: 'premium', label: '$300+' },
            ].map((p) => (
              <Badge
                key={p.id}
                variant={filters.priceRange === p.id ? 'default' : 'outline'}
                className="cursor-pointer transition-all hover:scale-105"
                onClick={() => updateFilter('priceRange', p.id)}
              >
                {p.label}
              </Badge>
            ))}
          </div>

          {/* Active filters */}
          {hasActiveFilters && (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" onClick={clearFilters} className="text-destructive gap-1">
                <X className="h-3 w-3" /> Clear All
              </Button>
            </div>
          )}
        </Card>
      </motion.div>

      {/* Results */}
      {loading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="h-8 w-8 animate-spin text-primary" />
          <span className="ml-3 text-muted-foreground">Loading schedules...</span>
        </div>
      ) : error ? (
        <motion.div {...fadeInUp} className="text-center py-20">
          <Search className="h-16 w-16 text-destructive/30 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">Failed to load schedules</h3>
          <p className="text-muted-foreground mb-6">{error}</p>
          <Button variant="outline" onClick={() => window.location.reload()}>Retry</Button>
        </motion.div>
      ) : filteredEvents.length > 0 ? (
        <motion.div
          variants={staggerContainer}
          initial="initial"
          animate="animate"
          className={
            view === 'grid'
              ? 'grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6'
              : 'space-y-4'
          }
        >
          {filteredEvents.map((event, i) => (
            <EventCard key={event.id} event={event} index={i} />
          ))}
        </motion.div>
      ) : (
        <motion.div {...fadeInUp} className="text-center py-20">
          <Search className="h-16 w-16 text-muted-foreground/30 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">No events found</h3>
          <p className="text-muted-foreground mb-6">Try adjusting your filters or search terms</p>
          <Button variant="outline" onClick={clearFilters}>Clear Filters</Button>
        </motion.div>
      )}
    </div>
  );
}
