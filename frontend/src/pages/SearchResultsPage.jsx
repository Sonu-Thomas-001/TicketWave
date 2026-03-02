import { useState, useMemo } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Search, SlidersHorizontal, X, Clock, MapPin, ArrowRight,
  ChevronDown, Star, Filter, Bus, Train, Plane, Users,
} from 'lucide-react';
import {
  Button, Input, Badge, Card, CardContent, Separator,
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/animations';
import { formatCurrency, cn } from '@/lib/utils';

/* --- Mock search results --- */
const mockResults = [
  { id: 'r1', operator: 'SpeedLine Express', type: 'bus', icon: Bus, from: 'New York', to: 'Los Angeles', departure: '06:00 AM', arrival: '02:30 PM', duration: '8h 30m', price: 89, rating: 4.5, seats: 12, amenities: ['WiFi', 'AC', 'USB'], classType: 'Premium' },
  { id: 'r2', operator: 'RailConnect', type: 'train', icon: Train, from: 'New York', to: 'Los Angeles', departure: '08:00 AM', arrival: '06:00 PM', duration: '10h 00m', price: 120, rating: 4.8, seats: 35, amenities: ['WiFi', 'Food', 'Power'], classType: 'Standard' },
  { id: 'r3', operator: 'SkyJet Airlines', type: 'flight', icon: Plane, from: 'New York', to: 'Los Angeles', departure: '10:30 AM', arrival: '01:45 PM', duration: '3h 15m', price: 250, rating: 4.3, seats: 8, amenities: ['Meal', 'Entertainment'], classType: 'Economy' },
  { id: 'r4', operator: 'BudgetBus Co.', type: 'bus', icon: Bus, from: 'New York', to: 'Los Angeles', departure: '11:00 PM', arrival: '07:30 AM', duration: '8h 30m', price: 45, rating: 3.9, seats: 22, amenities: ['AC', 'Recliner'], classType: 'Economy' },
  { id: 'r5', operator: 'Premium Rails', type: 'train', icon: Train, from: 'New York', to: 'Los Angeles', departure: '02:00 PM', arrival: '11:00 PM', duration: '9h 00m', price: 180, rating: 4.7, seats: 5, amenities: ['WiFi', 'Food', 'Sleeper'], classType: 'Business' },
  { id: 'r6', operator: 'EcoFly', type: 'flight', icon: Plane, from: 'New York', to: 'Los Angeles', departure: '04:00 PM', arrival: '07:00 PM', duration: '3h 00m', price: 195, rating: 4.1, seats: 18, amenities: ['WiFi', 'Snacks'], classType: 'Economy' },
  { id: 'r7', operator: 'NightRider', type: 'bus', icon: Bus, from: 'New York', to: 'Los Angeles', departure: '09:00 PM', arrival: '06:00 AM', duration: '9h 00m', price: 55, rating: 4.0, seats: 30, amenities: ['AC', 'Blanket', 'USB'], classType: 'Standard' },
  { id: 'r8', operator: 'LuxAir', type: 'flight', icon: Plane, from: 'New York', to: 'Los Angeles', departure: '07:00 AM', arrival: '10:30 AM', duration: '3h 30m', price: 450, rating: 4.9, seats: 3, amenities: ['Lounge', 'Meal', 'WiFi', 'Priority'], classType: 'First Class' },
];

export default function SearchResultsPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [sortBy, setSortBy] = useState('price');
  const [mobileFilterOpen, setMobileFilterOpen] = useState(false);
  const [filters, setFilters] = useState({
    priceRange: [0, 500],
    classTypes: [],
    timeSlots: [],
    operators: [],
  });

  const from = searchParams.get('from') || 'New York';
  const to = searchParams.get('to') || 'Los Angeles';
  const date = searchParams.get('date') || 'Today';

  const filteredResults = useMemo(() => {
    let results = [...mockResults];

    // Price filter
    results = results.filter(
      (r) => r.price >= filters.priceRange[0] && r.price <= filters.priceRange[1]
    );

    // Class filter
    if (filters.classTypes.length > 0) {
      results = results.filter((r) => filters.classTypes.includes(r.classType));
    }

    // Operator filter
    if (filters.operators.length > 0) {
      results = results.filter((r) => filters.operators.includes(r.operator));
    }

    // Sort
    if (sortBy === 'price') results.sort((a, b) => a.price - b.price);
    else if (sortBy === 'duration') results.sort((a, b) => a.duration.localeCompare(b.duration));
    else if (sortBy === 'departure') results.sort((a, b) => a.departure.localeCompare(b.departure));
    else if (sortBy === 'rating') results.sort((a, b) => b.rating - a.rating);

    return results;
  }, [sortBy, filters]);

  const toggleFilter = (key, value) => {
    setFilters((prev) => {
      const arr = prev[key];
      if (arr.includes(value)) {
        return { ...prev, [key]: arr.filter((v) => v !== value) };
      }
      return { ...prev, [key]: [...arr, value] };
    });
  };

  const uniqueClasses = [...new Set(mockResults.map((r) => r.classType))];
  const uniqueOperators = [...new Set(mockResults.map((r) => r.operator))];

  const FilterPanel = ({ className }) => (
    <div className={cn('space-y-6', className)}>
      {/* Price Range */}
      <div>
        <h4 className="text-sm font-semibold mb-3">Price Range</h4>
        <div className="space-y-3">
          <input
            type="range"
            min="0"
            max="500"
            value={filters.priceRange[1]}
            onChange={(e) => setFilters((prev) => ({ ...prev, priceRange: [prev.priceRange[0], parseInt(e.target.value)] }))}
            className="w-full"
          />
          <div className="flex items-center justify-between text-sm text-muted-foreground">
            <span>{formatCurrency(filters.priceRange[0])}</span>
            <span>{formatCurrency(filters.priceRange[1])}</span>
          </div>
        </div>
      </div>

      <Separator />

      {/* Class Type */}
      <div>
        <h4 className="text-sm font-semibold mb-3">Travel Class</h4>
        <div className="space-y-2">
          {uniqueClasses.map((cls) => (
            <label key={cls} className="flex items-center gap-2 cursor-pointer group">
              <div className={cn(
                'h-4 w-4 rounded border-2 transition-all flex items-center justify-center',
                filters.classTypes.includes(cls) ? 'bg-primary border-primary' : 'border-input group-hover:border-primary/50'
              )}>
                {filters.classTypes.includes(cls) && (
                  <svg className="h-3 w-3 text-white" viewBox="0 0 12 12" fill="none">
                    <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </div>
              <span className="text-sm">{cls}</span>
            </label>
          ))}
        </div>
      </div>

      <Separator />

      {/* Time Slots */}
      <div>
        <h4 className="text-sm font-semibold mb-3">Departure Time</h4>
        <div className="grid grid-cols-2 gap-2">
          {[
            { label: 'Morning', desc: '6AM–12PM', value: 'morning' },
            { label: 'Afternoon', desc: '12PM–6PM', value: 'afternoon' },
            { label: 'Evening', desc: '6PM–10PM', value: 'evening' },
            { label: 'Night', desc: '10PM–6AM', value: 'night' },
          ].map((slot) => (
            <button
              key={slot.value}
              onClick={() => toggleFilter('timeSlots', slot.value)}
              className={cn(
                'p-2.5 rounded-xl border text-center text-xs transition-all',
                filters.timeSlots.includes(slot.value)
                  ? 'border-primary bg-primary/10 text-primary'
                  : 'border-input hover:border-primary/50'
              )}
            >
              <p className="font-medium">{slot.label}</p>
              <p className="text-muted-foreground">{slot.desc}</p>
            </button>
          ))}
        </div>
      </div>

      <Separator />

      {/* Operators */}
      <div>
        <h4 className="text-sm font-semibold mb-3">Operators</h4>
        <div className="space-y-2">
          {uniqueOperators.map((op) => (
            <label key={op} className="flex items-center gap-2 cursor-pointer group">
              <div className={cn(
                'h-4 w-4 rounded border-2 transition-all flex items-center justify-center',
                filters.operators.includes(op) ? 'bg-primary border-primary' : 'border-input group-hover:border-primary/50'
              )}>
                {filters.operators.includes(op) && (
                  <svg className="h-3 w-3 text-white" viewBox="0 0 12 12" fill="none">
                    <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                  </svg>
                )}
              </div>
              <span className="text-sm">{op}</span>
            </label>
          ))}
        </div>
      </div>

      {/* Clear filters */}
      <Button
        variant="outline"
        className="w-full"
        onClick={() => setFilters({ priceRange: [0, 500], classTypes: [], timeSlots: [], operators: [] })}
      >
        Clear All Filters
      </Button>
    </div>
  );

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-6 py-8">
      {/* Header */}
      <motion.div {...fadeInUp} className="mb-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
          <MapPin className="h-4 w-4" />
          <span className="font-medium text-foreground">{from}</span>
          <ArrowRight className="h-3.5 w-3.5" />
          <span className="font-medium text-foreground">{to}</span>
          <span className="mx-2">•</span>
          <Clock className="h-4 w-4" />
          <span>{date}</span>
        </div>
        <h1 className="text-2xl md:text-3xl font-bold mb-1">
          {filteredResults.length} results found
        </h1>
      </motion.div>

      <div className="flex gap-8">
        {/* Desktop Filter Sidebar */}
        <motion.aside
          {...fadeInUp}
          transition={{ delay: 0.1 }}
          className="hidden lg:block w-72 flex-shrink-0"
        >
          <div className="sticky top-20">
            <Card className="p-5">
              <div className="flex items-center gap-2 mb-4">
                <SlidersHorizontal className="h-4 w-4" />
                <h3 className="font-semibold">Filters</h3>
              </div>
              <FilterPanel />
            </Card>
          </div>
        </motion.aside>

        {/* Results */}
        <div className="flex-1 min-w-0">
          {/* Sort & mobile filter bar */}
          <motion.div {...fadeInUp} transition={{ delay: 0.1 }} className="flex items-center gap-3 mb-6">
            <Button
              variant="outline"
              className="lg:hidden gap-2"
              onClick={() => setMobileFilterOpen(true)}
            >
              <Filter className="h-4 w-4" /> Filters
            </Button>

            <div className="flex-1" />

            <span className="text-sm text-muted-foreground hidden sm:inline">Sort by:</span>
            <Select value={sortBy} onValueChange={setSortBy}>
              <SelectTrigger className="w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="price">Price: Low to High</SelectItem>
                <SelectItem value="duration">Duration: Shortest</SelectItem>
                <SelectItem value="departure">Departure: Earliest</SelectItem>
                <SelectItem value="rating">Rating: Highest</SelectItem>
              </SelectContent>
            </Select>
          </motion.div>

          {/* Result Cards */}
          <motion.div
            variants={staggerContainer}
            initial="initial"
            animate="animate"
            className="space-y-4"
          >
            {filteredResults.map((result) => {
              const TypeIcon = result.icon;
              return (
                <motion.div key={result.id} variants={staggerItem}>
                  <Card className="group hover:shadow-lg hover:shadow-indigo-500/5 transition-all duration-300 overflow-hidden">
                    <CardContent className="p-0">
                      <div className="flex flex-col md:flex-row">
                        {/* Main info */}
                        <div className="flex-1 p-5 md:p-6">
                          <div className="flex items-start justify-between mb-4">
                            <div className="flex items-center gap-3">
                              <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-primary/10">
                                <TypeIcon className="h-5 w-5 text-primary" />
                              </div>
                              <div>
                                <h3 className="font-semibold">{result.operator}</h3>
                                <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                                  <Star className="h-3 w-3 text-amber-500 fill-amber-500" />
                                  <span>{result.rating}</span>
                                  <span>•</span>
                                  <Badge variant="secondary" className="text-2xs px-1.5 py-0">{result.classType}</Badge>
                                </div>
                              </div>
                            </div>
                          </div>

                          {/* Time info */}
                          <div className="flex items-center gap-4">
                            <div className="text-center">
                              <p className="text-lg font-bold">{result.departure}</p>
                              <p className="text-xs text-muted-foreground">{result.from}</p>
                            </div>

                            <div className="flex-1 flex items-center gap-2 px-2">
                              <div className="h-2 w-2 rounded-full bg-primary" />
                              <div className="flex-1 border-t border-dashed border-muted-foreground/30 relative">
                                <span className="absolute -top-4 left-1/2 -translate-x-1/2 text-2xs text-muted-foreground bg-card px-2">
                                  {result.duration}
                                </span>
                              </div>
                              <div className="h-2 w-2 rounded-full bg-emerald-500" />
                            </div>

                            <div className="text-center">
                              <p className="text-lg font-bold">{result.arrival}</p>
                              <p className="text-xs text-muted-foreground">{result.to}</p>
                            </div>
                          </div>

                          {/* Amenities */}
                          <div className="flex items-center gap-2 mt-4 flex-wrap">
                            {result.amenities.map((a) => (
                              <Badge key={a} variant="outline" className="text-2xs px-2 py-0.5">
                                {a}
                              </Badge>
                            ))}
                            {result.seats <= 10 && (
                              <Badge variant="warning" className="text-2xs">
                                <Users className="h-3 w-3 mr-1" />
                                Only {result.seats} left
                              </Badge>
                            )}
                          </div>
                        </div>

                        {/* Price & CTA */}
                        <div className="flex md:flex-col items-center justify-between md:justify-center gap-3 p-5 md:p-6 md:w-48 md:border-l bg-muted/30">
                          <div className="text-center">
                            <p className="text-xs text-muted-foreground">Starting from</p>
                            <p className="text-2xl md:text-3xl font-bold">
                              <span className="text-gradient">{formatCurrency(result.price)}</span>
                            </p>
                            <p className="text-2xs text-muted-foreground">per person</p>
                          </div>
                          <Button
                            onClick={() => navigate(`/events/1`)}
                            className="gap-2 md:w-full"
                          >
                            Select Seats
                            <ArrowRight className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              );
            })}
          </motion.div>

          {filteredResults.length === 0 && (
            <motion.div {...fadeInUp} className="text-center py-20">
              <Search className="h-16 w-16 text-muted-foreground/30 mx-auto mb-4" />
              <h3 className="text-xl font-semibold mb-2">No results found</h3>
              <p className="text-muted-foreground mb-6">Try adjusting your filters</p>
              <Button
                variant="outline"
                onClick={() => setFilters({ priceRange: [0, 500], classTypes: [], timeSlots: [], operators: [] })}
              >
                Clear Filters
              </Button>
            </motion.div>
          )}
        </div>
      </div>

      {/* Mobile Filter Drawer */}
      <AnimatePresence>
        {mobileFilterOpen && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm lg:hidden"
              onClick={() => setMobileFilterOpen(false)}
            />
            <motion.div
              initial={{ x: -320 }}
              animate={{ x: 0 }}
              exit={{ x: -320 }}
              transition={{ type: 'spring', damping: 25, stiffness: 300 }}
              className="fixed inset-y-0 left-0 z-50 w-80 bg-card border-r shadow-2xl lg:hidden overflow-y-auto p-6"
            >
              <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-bold">Filters</h3>
                <Button variant="ghost" size="icon" onClick={() => setMobileFilterOpen(false)}>
                  <X className="h-5 w-5" />
                </Button>
              </div>
              <FilterPanel />
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
}
