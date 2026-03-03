import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Home, CalendarDays, Search, Ticket, LayoutDashboard,
  Users, Settings, HelpCircle, X, Music, Trophy, Drama,
  PartyPopper, Sparkles, TrendingUp, Clock, Star,
} from 'lucide-react';
import { Button, Separator } from '@/components/ui';
import { useAuth } from '@/context/AuthContext';
import { cn } from '@/lib/utils';

const mainNav = [
  { path: '/', label: 'Home', icon: Home },
  { path: '/events', label: 'Browse Events', icon: Search },
  { path: '/bookings', label: 'My Bookings', icon: CalendarDays },
];

const categoryNav = [
  { id: 'concert',  path: '/events?cat=concert',  label: 'Concerts',  icon: Music,       color: 'from-violet-500 to-purple-600',  bg: 'bg-violet-500/10',  text: 'text-violet-500' },
  { id: 'sports',   path: '/events?cat=sports',    label: 'Sports',    icon: Trophy,      color: 'from-emerald-500 to-green-600',  bg: 'bg-emerald-500/10', text: 'text-emerald-500' },
  { id: 'theatre',  path: '/events?cat=theatre',   label: 'Theatre',   icon: Drama,       color: 'from-rose-500 to-pink-600',      bg: 'bg-rose-500/10',    text: 'text-rose-500' },
  { id: 'festival', path: '/events?cat=festival',  label: 'Festivals', icon: PartyPopper, color: 'from-amber-500 to-orange-600',   bg: 'bg-amber-500/10',   text: 'text-amber-500' },
  { id: 'show',     path: '/events?cat=show',      label: 'Shows',     icon: Sparkles,    color: 'from-cyan-500 to-blue-600',      bg: 'bg-cyan-500/10',    text: 'text-cyan-500' },
];

const quickLinks = [
  { path: '/events?sort=trending', label: 'Trending Now', icon: TrendingUp },
  { path: '/events?sort=upcoming', label: 'Coming Soon', icon: Clock },
  { path: '/events?sort=top',      label: 'Top Rated',    icon: Star },
];

const adminNav = [
  { path: '/admin', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/admin/events', label: 'Manage Events', icon: Ticket },
  { path: '/admin/users', label: 'Users', icon: Users },
  { path: '/admin/settings', label: 'Settings', icon: Settings },
];

export default function Sidebar({ open, onClose }) {
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const activeCat = searchParams.get('cat');

  const content = (
    <div className="flex flex-col h-full">
      {/* Mobile close header */}
      <div className="flex items-center justify-between px-5 py-4 lg:hidden border-b">
        <span className="text-lg font-bold bg-gradient-to-r from-indigo-500 to-purple-600 bg-clip-text text-transparent">
          TicketWave
        </span>
        <Button variant="ghost" size="icon" onClick={onClose} className="rounded-full hover:bg-destructive/10 hover:text-destructive">
          <X className="h-5 w-5" />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto px-3 py-4 space-y-1">
        {/* ── Main Navigation ── */}
        <p className="px-3 mb-2 text-[11px] font-semibold uppercase text-muted-foreground/70 tracking-widest">
          Navigation
        </p>
        {mainNav.map((item) => {
          const Icon = item.icon;
          const active = location.pathname === item.path && !activeCat;
          return (
            <Link key={item.path} to={item.path} onClick={onClose}>
              <div
                className={cn(
                  'group flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
                  active
                    ? 'gradient-primary text-white shadow-lg shadow-indigo-500/25'
                    : 'text-muted-foreground hover:bg-accent/80 hover:text-foreground'
                )}
              >
                <Icon className={cn('h-[18px] w-[18px] transition-transform duration-200', !active && 'group-hover:scale-110')} />
                <span>{item.label}</span>
              </div>
            </Link>
          );
        })}

        <div className="py-3"><Separator /></div>

        {/* ── Categories with colored icons ── */}
        <p className="px-3 mb-3 text-[11px] font-semibold uppercase text-muted-foreground/70 tracking-widest">
          Categories
        </p>
        <div className="space-y-1">
          {categoryNav.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === '/events' && activeCat === item.id;
            return (
              <Link key={item.id} to={item.path} onClick={onClose}>
                <div
                  className={cn(
                    'group flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
                    isActive
                      ? `bg-gradient-to-r ${item.color} text-white shadow-lg`
                      : 'text-muted-foreground hover:bg-accent/80 hover:text-foreground'
                  )}
                >
                  <div
                    className={cn(
                      'flex items-center justify-center h-8 w-8 rounded-lg transition-all duration-200',
                      isActive ? 'bg-white/20' : `${item.bg} group-hover:scale-110`
                    )}
                  >
                    <Icon className={cn('h-4 w-4', isActive ? 'text-white' : item.text)} />
                  </div>
                  <span>{item.label}</span>
                  {isActive && (
                    <motion.div
                      layoutId="catIndicator"
                      className="ml-auto h-2 w-2 rounded-full bg-white"
                    />
                  )}
                </div>
              </Link>
            );
          })}
        </div>

        <div className="py-3"><Separator /></div>

        {/* ── Discover / Quick Links ── */}
        <p className="px-3 mb-2 text-[11px] font-semibold uppercase text-muted-foreground/70 tracking-widest">
          Discover
        </p>
        {quickLinks.map((item) => {
          const Icon = item.icon;
          return (
            <Link key={item.path} to={item.path} onClick={onClose}>
              <div className="group flex items-center gap-3 px-3 py-2 rounded-xl text-sm text-muted-foreground hover:bg-accent/80 hover:text-foreground transition-all duration-200">
                <Icon className="h-4 w-4 group-hover:scale-110 transition-transform duration-200" />
                <span>{item.label}</span>
              </div>
            </Link>
          );
        })}

        {isAdmin && (
          <>
            <div className="py-3"><Separator /></div>
            <p className="px-3 mb-2 text-[11px] font-semibold uppercase text-muted-foreground/70 tracking-widest">
              Admin
            </p>
            {adminNav.map((item) => {
              const Icon = item.icon;
              const active = location.pathname === item.path;
              return (
                <Link key={item.path} to={item.path} onClick={onClose}>
                  <div
                    className={cn(
                      'group flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
                      active
                        ? 'bg-accent text-accent-foreground'
                        : 'text-muted-foreground hover:bg-accent/80 hover:text-foreground'
                    )}
                  >
                    <Icon className="h-4 w-4 group-hover:scale-110 transition-transform" />
                    {item.label}
                  </div>
                </Link>
              );
            })}
          </>
        )}
      </div>

      {/* Footer */}
      <div className="px-3 py-3 border-t">
        <Link to="/help" onClick={onClose}>
          <div className="flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm text-muted-foreground hover:bg-accent/80 hover:text-foreground transition-all duration-200">
            <HelpCircle className="h-4 w-4" />
            Help & Support
          </div>
        </Link>
        <p className="px-3 pt-2 text-[10px] text-muted-foreground/50 text-center">&copy; 2026 TicketWave</p>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:w-64 lg:flex-col lg:fixed lg:inset-y-0 lg:top-16 lg:border-r bg-card/60 backdrop-blur-md z-30">
        {content}
      </aside>

      {/* Mobile overlay sidebar */}
      <AnimatePresence>
        {open && (
          <>
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm lg:hidden"
              onClick={onClose}
            />
            <motion.aside
              initial={{ x: -300 }}
              animate={{ x: 0 }}
              exit={{ x: -300 }}
              transition={{ type: 'spring', damping: 28, stiffness: 350 }}
              className="fixed inset-y-0 left-0 z-50 w-72 bg-card border-r shadow-2xl lg:hidden"
            >
              {content}
            </motion.aside>
          </>
        )}
      </AnimatePresence>
    </>
  );
}
