import { useState, useEffect, useMemo, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, useInView } from 'framer-motion';
import {
  Search, ArrowRight, Sparkles, TrendingUp, Calendar,
  Music, Trophy, Drama, PartyPopper, Star, ChevronRight,
  ChevronLeft, Shield, Zap, Globe, Headphones, Clock,
  MapPin, Plane, Train, Bus, Loader2,
} from 'lucide-react';
import { Button, Input, Badge, Card, CardContent } from '@/components/ui';
import { EventCard } from '@/components/shared';
import SearchCard from '@/components/features/SearchCard';
import { api } from '@/lib/api';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/animations';

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

const categories = [
  { id: 'concert', label: 'Concerts', icon: Music, color: 'from-violet-500 to-purple-500' },
  { id: 'sports', label: 'Sports', icon: Trophy, color: 'from-emerald-500 to-green-500' },
  { id: 'theatre', label: 'Theatre', icon: Drama, color: 'from-rose-500 to-pink-500' },
  { id: 'festival', label: 'Festivals', icon: PartyPopper, color: 'from-amber-500 to-orange-500' },
  { id: 'show', label: 'Shows', icon: Sparkles, color: 'from-cyan-500 to-blue-500' },
];

const popularRoutes = [
  { from: 'New York', to: 'Los Angeles', price: 89, type: 'flight', icon: Plane, image: '/images/route-1.svg' },
  { from: 'London', to: 'Paris', price: 45, type: 'train', icon: Train, image: '/images/route-2.svg' },
  { from: 'San Francisco', to: 'Las Vegas', price: 35, type: 'bus', icon: Bus, image: '/images/route-3.svg' },
  { from: 'Tokyo', to: 'Osaka', price: 65, type: 'train', icon: Train, image: '/images/route-4.svg' },
  { from: 'Dubai', to: 'Abu Dhabi', price: 25, type: 'bus', icon: Bus, image: '/images/route-5.svg' },
  { from: 'Chicago', to: 'Miami', price: 120, type: 'flight', icon: Plane, image: '/images/route-6.svg' },
];

const features = [
  {
    icon: Shield,
    title: 'Secure Booking',
    description: 'End-to-end encrypted payments with money-back guarantee on every purchase.',
    color: 'from-emerald-500 to-teal-500',
  },
  {
    icon: Zap,
    title: 'Instant Confirmation',
    description: 'Real-time seat reservation with instant e-ticket delivery to your inbox.',
    color: 'from-amber-500 to-orange-500',
  },
  {
    icon: Globe,
    title: 'Global Coverage',
    description: 'Access thousands of events and routes across 50+ countries worldwide.',
    color: 'from-indigo-500 to-blue-500',
  },
];

export default function HomePage() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [carouselIndex, setCarouselIndex] = useState(0);
  const featuresRef = useRef(null);
  const featuresInView = useInView(featuresRef, { once: true, margin: '-100px' });

  const [allEvents, setAllEvents] = useState([]);
  const [loadingSchedules, setLoadingSchedules] = useState(true);

  // Fetch schedules from real API
  useEffect(() => {
    let cancelled = false;
    api.get('/schedules/browse')
      .then((res) => {
        if (!cancelled) {
          const data = res.data ?? res;
          setAllEvents(Array.isArray(data) ? data.map(mapScheduleToEvent) : []);
        }
      })
      .catch(() => { /* homepage degrades gracefully */ })
      .finally(() => { if (!cancelled) setLoadingSchedules(false); });
    return () => { cancelled = true; };
  }, []);

  const featuredEvents = useMemo(() => allEvents.filter((e) => e.featured).slice(0, 3), [allEvents]);
  const trendingEvents = useMemo(() => [...allEvents].sort((a, b) => a.availableSeats - b.availableSeats).slice(0, 4), [allEvents]);

  const visibleRoutes = 3;
  const maxCarouselIndex = Math.max(0, popularRoutes.length - visibleRoutes);

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(`/events?q=${encodeURIComponent(searchQuery.trim())}`);
    }
  };

  return (
    <div className="space-y-20 pb-20">
      {/* Hero Section with Animated Background */}
      <section className="relative overflow-hidden min-h-[600px] flex items-center">
        {/* Animated background shapes */}
        <div className="absolute inset-0 gradient-primary opacity-[0.03]" />
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-indigo-500/10 via-transparent to-transparent" />

        {/* Floating shapes */}
        <motion.div
          className="absolute top-20 left-[10%] h-72 w-72 rounded-full bg-gradient-to-br from-indigo-500/10 to-blue-500/10 blur-3xl"
          animate={{ y: [0, -30, 0], x: [0, 15, 0] }}
          transition={{ duration: 8, repeat: Infinity, ease: 'easeInOut' }}
        />
        <motion.div
          className="absolute bottom-20 right-[10%] h-96 w-96 rounded-full bg-gradient-to-br from-purple-500/10 to-pink-500/10 blur-3xl"
          animate={{ y: [0, 20, 0], x: [0, -20, 0] }}
          transition={{ duration: 10, repeat: Infinity, ease: 'easeInOut' }}
        />
        <motion.div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-[500px] w-[500px] rounded-full bg-gradient-to-br from-cyan-500/5 to-indigo-500/5 blur-3xl"
          animate={{ scale: [1, 1.1, 1], rotate: [0, 5, 0] }}
          transition={{ duration: 12, repeat: Infinity, ease: 'easeInOut' }}
        />

        <div className="relative max-w-6xl mx-auto px-4 md:px-6 pt-8 pb-16 w-full">
          <div className="text-center mb-12">
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5 }}
            >
              <Badge variant="secondary" className="mb-6 px-4 py-1.5 text-sm">
                <Sparkles className="h-3.5 w-3.5 mr-1.5" />
                #1 Travel & Events Platform
              </Badge>
            </motion.div>

            <motion.h1
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.1, duration: 0.5 }}
              className="text-4xl sm:text-5xl lg:text-7xl font-extrabold tracking-tight mb-6"
            >
              Book Travel & Events
              <span className="text-gradient block mt-2">Effortlessly</span>
            </motion.h1>

            <motion.p
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.2, duration: 0.5 }}
              className="text-lg md:text-xl text-muted-foreground max-w-2xl mx-auto mb-10"
            >
              Search thousands of routes and events. Book with confidence.
              Your journey starts here.
            </motion.p>

            {/* Animated CTA */}
            <motion.div
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.3, duration: 0.5 }}
              className="flex items-center justify-center gap-4 mb-12"
            >
              <Link to="/events">
                <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                  <Button size="xl" className="gap-2 shadow-xl shadow-indigo-500/25">
                    Explore Events
                    <motion.span
                      animate={{ x: [0, 5, 0] }}
                      transition={{ duration: 1.5, repeat: Infinity }}
                    >
                      <ArrowRight className="h-5 w-5" />
                    </motion.span>
                  </Button>
                </motion.div>
              </Link>
              <Link to="/search">
                <Button size="xl" variant="outline" className="gap-2">
                  <Search className="h-5 w-5" />
                  Search Routes
                </Button>
              </Link>
            </motion.div>
          </div>

          {/* Search Card */}
          <SearchCard className="max-w-5xl mx-auto" />

          {/* Quick tags */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.7 }}
            className="flex items-center justify-center gap-2 mt-6 flex-wrap"
          >
            <span className="text-sm text-muted-foreground">Popular:</span>
            {['Taylor Swift', 'NBA Finals', 'NY → LA', 'Coachella'].map((tag) => (
              <Badge
                key={tag}
                variant="outline"
                className="cursor-pointer hover:bg-accent transition-all hover:scale-105 text-xs"
                onClick={() => {
                  setSearchQuery(tag);
                  navigate(`/events?q=${encodeURIComponent(tag)}`);
                }}
              >
                {tag}
              </Badge>
            ))}
          </motion.div>
        </div>
      </section>

      {/* Popular Routes / Events Carousel */}
      <section className="max-w-7xl mx-auto px-4 md:px-6">
        <div className="flex items-center justify-between mb-8">
          <motion.div
            initial={{ opacity: 0, x: -20 }}
            whileInView={{ opacity: 1, x: 0 }}
            viewport={{ once: true }}
          >
            <div className="flex items-center gap-2 mb-1">
              <TrendingUp className="h-5 w-5 text-indigo-500" />
              <h2 className="text-2xl md:text-3xl font-bold">Popular Routes & Events</h2>
            </div>
            <p className="text-muted-foreground text-sm">Trending destinations and experiences</p>
          </motion.div>

          <div className="flex items-center gap-2">
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCarouselIndex(Math.max(0, carouselIndex - 1))}
              disabled={carouselIndex === 0}
              className="h-9 w-9"
            >
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              onClick={() => setCarouselIndex(Math.min(maxCarouselIndex, carouselIndex + 1))}
              disabled={carouselIndex >= maxCarouselIndex}
              className="h-9 w-9"
            >
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>

        <div className="overflow-hidden">
          <motion.div
            className="flex gap-6"
            animate={{ x: `-${carouselIndex * (100 / visibleRoutes)}%` }}
            transition={{ type: 'spring', damping: 30, stiffness: 200 }}
          >
            {popularRoutes.map((route, i) => {
              const RouteIcon = route.icon;
              return (
                <motion.div
                  key={i}
                  initial={{ opacity: 0, y: 20 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true }}
                  transition={{ delay: i * 0.1 }}
                  className="min-w-[calc(33.333%-1rem)] flex-shrink-0"
                  style={{ minWidth: `calc(${100 / visibleRoutes}% - ${((visibleRoutes - 1) * 24) / visibleRoutes}px)` }}
                >
                  <Card className="group overflow-hidden hover:shadow-xl hover:-translate-y-1 transition-all duration-300 cursor-pointer">
                    <div className="relative h-40 overflow-hidden">
                      <img
                        src={route.image}
                        alt={`${route.from} to ${route.to}`}
                        className="w-full h-full object-cover group-hover:scale-110 transition-transform duration-700"
                        loading="lazy"
                      />
                      <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent" />
                      <div className="absolute top-3 right-3">
                        <Badge variant="secondary" className="bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm text-xs gap-1">
                          <RouteIcon className="h-3 w-3" />
                          {route.type}
                        </Badge>
                      </div>
                      <div className="absolute bottom-3 left-3 right-3 text-white">
                        <div className="flex items-center gap-2 text-sm">
                          <span className="font-semibold">{route.from}</span>
                          <ArrowRight className="h-3.5 w-3.5 text-white/70" />
                          <span className="font-semibold">{route.to}</span>
                        </div>
                      </div>
                    </div>
                    <CardContent className="p-4 flex items-center justify-between">
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <Clock className="h-3.5 w-3.5" />
                        <span>Multiple departures</span>
                      </div>
                      <div className="text-right">
                        <p className="text-xs text-muted-foreground">from</p>
                        <p className="text-lg font-bold text-gradient">${route.price}</p>
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              );
            })}
          </motion.div>
        </div>
      </section>

      {/* Categories */}
      <section className="max-w-7xl mx-auto px-4 md:px-6">
        <div className="flex items-center justify-between mb-8">
          <div>
            <h2 className="text-2xl md:text-3xl font-bold">Browse by Category</h2>
            <p className="text-muted-foreground text-sm mt-1">Find events that match your interests</p>
          </div>
        </div>

        <motion.div
          variants={staggerContainer}
          initial="initial"
          whileInView="animate"
          viewport={{ once: true }}
          className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-4"
        >
          {categories.map((cat) => {
            const Icon = cat.icon;
            return (
              <motion.div key={cat.id} variants={staggerItem}>
                <Link to={`/events?cat=${cat.id}`}>
                  <Card className="group p-6 text-center hover:shadow-lg hover:-translate-y-1 transition-all duration-300 cursor-pointer">
                    <div className={`inline-flex items-center justify-center h-14 w-14 rounded-2xl bg-gradient-to-br ${cat.color} text-white mb-3 shadow-lg group-hover:scale-110 transition-transform duration-300`}>
                      <Icon className="h-6 w-6" />
                    </div>
                    <h3 className="font-semibold text-sm">{cat.label}</h3>
                  </Card>
                </Link>
              </motion.div>
            );
          })}
        </motion.div>
      </section>

      {/* Featured Events */}
      <section className="max-w-7xl mx-auto px-4 md:px-6">
        <div className="flex items-center justify-between mb-8">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Star className="h-5 w-5 text-amber-500" />
              <h2 className="text-2xl md:text-3xl font-bold">Featured Events</h2>
            </div>
            <p className="text-muted-foreground text-sm">Hand-picked events you don't want to miss</p>
          </div>
          <Link to="/events">
            <Button variant="ghost" className="gap-1">
              View All <ChevronRight className="h-4 w-4" />
            </Button>
          </Link>
        </div>

        <motion.div
          variants={staggerContainer}
          initial="initial"
          whileInView="animate"
          viewport={{ once: true }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6"
        >
          {featuredEvents.length > 0 ? featuredEvents.map((event, i) => (
            <EventCard key={event.id} event={event} index={i} />
          )) : loadingSchedules ? (
            <div className="col-span-full flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
              <span className="ml-2 text-muted-foreground">Loading...</span>
            </div>
          ) : (
            <p className="col-span-full text-center text-muted-foreground py-8">No featured schedules available</p>
          )}
        </motion.div>
      </section>

      {/* Features Section — 3 Column Layout */}
      <section ref={featuresRef} className="max-w-7xl mx-auto px-4 md:px-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={featuresInView ? { opacity: 1, y: 0 } : {}}
          transition={{ duration: 0.5 }}
          className="text-center mb-12"
        >
          <h2 className="text-2xl md:text-3xl font-bold mb-3">Why Choose TicketWave?</h2>
          <p className="text-muted-foreground max-w-xl mx-auto">
            Join millions of travelers who trust us for seamless booking experiences
          </p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
          {features.map((feature, i) => {
            const Icon = feature.icon;
            return (
              <motion.div
                key={feature.title}
                initial={{ opacity: 0, y: 30 }}
                animate={featuresInView ? { opacity: 1, y: 0 } : {}}
                transition={{ delay: 0.2 + i * 0.15, duration: 0.5 }}
              >
                <Card className="relative overflow-hidden p-8 text-center hover:shadow-xl hover:-translate-y-1 transition-all duration-300 group h-full">
                  {/* Glow effect */}
                  <div className={`absolute inset-0 bg-gradient-to-br ${feature.color} opacity-0 group-hover:opacity-5 transition-opacity duration-500`} />

                  <div className={`inline-flex items-center justify-center h-16 w-16 rounded-2xl bg-gradient-to-br ${feature.color} text-white mb-5 shadow-lg group-hover:scale-110 transition-transform duration-300`}>
                    <Icon className="h-7 w-7" />
                  </div>
                  <h3 className="text-lg font-bold mb-2">{feature.title}</h3>
                  <p className="text-muted-foreground text-sm leading-relaxed">{feature.description}</p>
                </Card>
              </motion.div>
            );
          })}
        </div>
      </section>

      {/* Trending Events */}
      <section className="max-w-7xl mx-auto px-4 md:px-6">
        <div className="flex items-center justify-between mb-8">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <TrendingUp className="h-5 w-5 text-emerald-500" />
              <h2 className="text-2xl md:text-3xl font-bold">Trending Now</h2>
            </div>
            <p className="text-muted-foreground text-sm">Events selling fast — grab your tickets</p>
          </div>
        </div>

        <motion.div
          variants={staggerContainer}
          initial="initial"
          whileInView="animate"
          viewport={{ once: true }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6"
        >
          {trendingEvents.length > 0 ? trendingEvents.map((event, i) => (
            <EventCard key={event.id} event={event} index={i} />
          )) : loadingSchedules ? (
            <div className="col-span-full flex items-center justify-center py-12">
              <Loader2 className="h-6 w-6 animate-spin text-primary" />
              <span className="ml-2 text-muted-foreground">Loading...</span>
            </div>
          ) : (
            <p className="col-span-full text-center text-muted-foreground py-8">No trending schedules right now</p>
          )}
        </motion.div>
      </section>

      {/* CTA Banner */}
      <section className="max-w-7xl mx-auto px-4 md:px-6">
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true }}
          className="relative overflow-hidden rounded-3xl gradient-primary p-8 md:p-12 lg:p-16 text-white"
        >
          {/* Pattern background */}
          <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PHBhdGggZD0iTTM2IDM0djZoLTZ2LTZIMDZ6bTAtMTBoNnY2aC02di02em0xMiAwaDZ2NmgtNnYtNnoiLz48L2c+PC9nPjwvc3ZnPg==')] opacity-30" />

          {/* Floating accent */}
          <motion.div
            className="absolute -top-10 -right-10 h-40 w-40 rounded-full bg-white/10 blur-2xl"
            animate={{ scale: [1, 1.3, 1] }}
            transition={{ duration: 5, repeat: Infinity }}
          />

          <div className="relative flex flex-col md:flex-row items-center justify-between gap-8">
            <div className="max-w-lg">
              <h2 className="text-2xl md:text-4xl font-bold mb-3">Never Miss an Event</h2>
              <p className="text-white/80 text-lg">
                Create an account to get personalized recommendations, early access to tickets,
                and exclusive deals.
              </p>
            </div>
            <Link to="/register">
              <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Button size="xl" className="bg-white text-indigo-600 hover:bg-white/90 shadow-xl whitespace-nowrap">
                  Get Started Free
                  <ArrowRight className="ml-2 h-5 w-5" />
                </Button>
              </motion.div>
            </Link>
          </div>
        </motion.div>
      </section>
    </div>
  );
}
