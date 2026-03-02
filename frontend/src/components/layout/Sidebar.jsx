import { Link, useLocation } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Home, CalendarDays, Search, Ticket, LayoutDashboard,
  Users, Settings, HelpCircle, X, Music, Trophy, Drama, PartyPopper, Sparkles,
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
  { path: '/events?cat=concert', label: 'Concerts', icon: Music },
  { path: '/events?cat=sports', label: 'Sports', icon: Trophy },
  { path: '/events?cat=theatre', label: 'Theatre', icon: Drama },
  { path: '/events?cat=festival', label: 'Festivals', icon: PartyPopper },
  { path: '/events?cat=show', label: 'Shows', icon: Sparkles },
];

const adminNav = [
  { path: '/admin', label: 'Dashboard', icon: LayoutDashboard },
  { path: '/admin/events', label: 'Manage Events', icon: Ticket },
  { path: '/admin/users', label: 'Users', icon: Users },
  { path: '/admin/settings', label: 'Settings', icon: Settings },
];

export default function Sidebar({ open, onClose }) {
  const location = useLocation();
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';

  const content = (
    <div className="flex flex-col h-full py-4">
      {/* Close on mobile */}
      <div className="flex items-center justify-between px-4 mb-4 lg:hidden">
        <span className="text-lg font-bold text-gradient">Menu</span>
        <Button variant="ghost" size="icon" onClick={onClose}>
          <X className="h-5 w-5" />
        </Button>
      </div>

      {/* Main nav */}
      <nav className="flex-1 space-y-1 px-3">
        <p className="px-3 mb-2 text-xs font-semibold uppercase text-muted-foreground tracking-wider">
          Navigation
        </p>
        {mainNav.map((item) => {
          const Icon = item.icon;
          const active = location.pathname === item.path;
          return (
            <Link key={item.path} to={item.path} onClick={onClose}>
              <div className={cn(
                'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
                active
                  ? 'gradient-primary text-white shadow-md shadow-indigo-500/20'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
              )}>
                <Icon className="h-4 w-4" />
                {item.label}
              </div>
            </Link>
          );
        })}

        <Separator className="my-4" />

        <p className="px-3 mb-2 text-xs font-semibold uppercase text-muted-foreground tracking-wider">
          Categories
        </p>
        {categoryNav.map((item) => {
          const Icon = item.icon;
          return (
            <Link key={item.path} to={item.path} onClick={onClose}>
              <div className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-all duration-200">
                <Icon className="h-4 w-4" />
                {item.label}
              </div>
            </Link>
          );
        })}

        {isAdmin && (
          <>
            <Separator className="my-4" />
            <p className="px-3 mb-2 text-xs font-semibold uppercase text-muted-foreground tracking-wider">
              Admin
            </p>
            {adminNav.map((item) => {
              const Icon = item.icon;
              const active = location.pathname === item.path;
              return (
                <Link key={item.path} to={item.path} onClick={onClose}>
                  <div className={cn(
                    'flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-all duration-200',
                    active
                      ? 'bg-accent text-accent-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground'
                  )}>
                    <Icon className="h-4 w-4" />
                    {item.label}
                  </div>
                </Link>
              );
            })}
          </>
        )}
      </nav>

      {/* Footer */}
      <div className="px-3 mt-4">
        <Link to="/help" onClick={onClose}>
          <div className="flex items-center gap-3 px-3 py-2 rounded-xl text-sm text-muted-foreground hover:bg-accent hover:text-accent-foreground transition-all">
            <HelpCircle className="h-4 w-4" />
            Help & Support
          </div>
        </Link>
      </div>
    </div>
  );

  return (
    <>
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex lg:w-64 lg:flex-col lg:fixed lg:inset-y-0 lg:top-16 lg:border-r bg-card/50 backdrop-blur-sm z-30">
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
              className="fixed inset-0 z-40 bg-black/50 backdrop-blur-sm lg:hidden"
              onClick={onClose}
            />
            <motion.aside
              initial={{ x: -280 }}
              animate={{ x: 0 }}
              exit={{ x: -280 }}
              transition={{ type: 'spring', damping: 25, stiffness: 300 }}
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
