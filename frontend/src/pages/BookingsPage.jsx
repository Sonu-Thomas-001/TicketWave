import { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Calendar, MapPin, Clock, Search, Filter, X,
  Ticket, ChevronRight, Download, RefreshCw,
  XCircle, AlertTriangle, RotateCcw, Eye,
  ArrowUpDown, CheckCircle2, Timer, Ban,
  ReceiptText, Mail,
} from 'lucide-react';
import {
  Button, Input, Card, CardContent, CardHeader, CardTitle,
  Badge, Separator,
  Tabs, TabsList, TabsTrigger, TabsContent,
  Pagination,
} from '@/components/ui';
import { mockBookings } from '@/lib/mockData';
import { formatCurrency, formatDate, formatTime, cn } from '@/lib/utils';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/animations';

const statusConfig = {
  CONFIRMED: { variant: 'success', icon: CheckCircle2, label: 'Confirmed' },
  PENDING:   { variant: 'warning', icon: Timer,        label: 'Pending' },
  CANCELLED: { variant: 'destructive', icon: Ban,      label: 'Cancelled' },
};

const PAGE_SIZE = 5;

/* ---------- PNR Lookup Card ---------- */
function PNRLookup() {
  const [pnr, setPnr] = useState('');
  const [result, setResult] = useState(null);
  const [notFound, setNotFound] = useState(false);

  const handleLookup = () => {
    const found = mockBookings.find(
      (b) => b.id.toLowerCase() === pnr.toLowerCase()
    );
    if (found) {
      setResult(found);
      setNotFound(false);
    } else {
      setResult(null);
      setNotFound(true);
    }
  };

  return (
    <Card className="border-dashed">
      <CardContent className="p-5 space-y-3">
        <div className="flex items-center gap-2 text-sm font-medium">
          <Search className="h-4 w-4 text-primary" />
          Quick PNR / Booking Lookup
        </div>
        <div className="flex gap-2">
          <Input
            placeholder="Enter PNR or Booking ID"
            value={pnr}
            onChange={(e) => { setPnr(e.target.value); setNotFound(false); }}
            onKeyDown={(e) => e.key === 'Enter' && handleLookup()}
            className="font-mono"
          />
          <Button onClick={handleLookup} disabled={!pnr.trim()}>Lookup</Button>
        </div>
        {notFound && (
          <p className="text-sm text-destructive flex items-center gap-1.5">
            <AlertTriangle className="h-3.5 w-3.5" /> No booking found for "{pnr}"
          </p>
        )}
        {result && (
          <div className="rounded-xl bg-muted/50 p-3 flex items-center justify-between">
            <div>
              <p className="font-medium text-sm">{result.eventTitle}</p>
              <p className="text-xs text-muted-foreground">{result.id} • {formatDate(result.date)}</p>
            </div>
            <Badge variant={statusConfig[result.status]?.variant} className="text-xs">{result.status}</Badge>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

/* ---------- Cancel Confirmation Dialog ---------- */
function CancelDialog({ booking, onClose, onConfirm }) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm p-4"
      onClick={onClose}
    >
      <motion.div
        initial={{ scale: 0.95, y: 20 }}
        animate={{ scale: 1, y: 0 }}
        exit={{ scale: 0.95, y: 20 }}
        className="bg-background rounded-2xl p-6 max-w-md w-full shadow-xl space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-xl bg-destructive/10">
            <AlertTriangle className="h-5 w-5 text-destructive" />
          </div>
          <h3 className="font-semibold text-lg">Cancel Booking</h3>
        </div>
        <p className="text-sm text-muted-foreground">
          Are you sure you want to cancel <span className="font-medium text-foreground">{booking.eventTitle}</span>?
          This action cannot be undone. A refund will be processed within 5-7 business days.
        </p>
        <div className="flex justify-end gap-3 pt-2">
          <Button variant="outline" onClick={onClose}>Keep Booking</Button>
          <Button variant="destructive" onClick={() => { onConfirm(booking.id); onClose(); }}>
            <XCircle className="mr-2 h-4 w-4" /> Cancel Booking
          </Button>
        </div>
      </motion.div>
    </motion.div>
  );
}

/* ---------- Timeline Tracker ---------- */
function BookingTimeline({ booking }) {
  const steps = [
    { label: 'Booked', done: true, date: booking.date },
    { label: 'Payment', done: booking.status !== 'PENDING', date: booking.date },
    { label: 'Confirmed', done: booking.status === 'CONFIRMED', date: booking.date },
    ...(booking.status === 'CANCELLED'
      ? [{ label: 'Cancelled', done: true, cancelled: true }]
      : [{ label: 'Event Day', done: false }]),
  ];

  return (
    <div className="flex items-center gap-1 py-2">
      {steps.map((step, i) => (
        <div key={i} className="flex items-center gap-1">
          <div className={cn(
            'h-2.5 w-2.5 rounded-full transition-colors',
            step.cancelled ? 'bg-destructive' :
            step.done ? 'bg-emerald-500' : 'bg-muted-foreground/20'
          )} />
          <span className={cn(
            'text-2xs whitespace-nowrap',
            step.cancelled ? 'text-destructive' :
            step.done ? 'text-foreground' : 'text-muted-foreground/40'
          )}>{step.label}</span>
          {i < steps.length - 1 && (
            <div className={cn(
              'w-6 h-0.5 rounded',
              step.done ? 'bg-emerald-500' : 'bg-muted-foreground/10'
            )} />
          )}
        </div>
      ))}
    </div>
  );
}

/* ---------- Main Page ---------- */
export default function BookingsPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState('date-desc');
  const [page, setPage] = useState(1);
  const [cancelTarget, setCancelTarget] = useState(null);
  const [expandedId, setExpandedId] = useState(null);
  const [cancelledIds, setCancelledIds] = useState(new Set());

  const bookingsWithCancels = useMemo(
    () => mockBookings.map((b) =>
      cancelledIds.has(b.id) ? { ...b, status: 'CANCELLED' } : b
    ),
    [cancelledIds]
  );

  const filtered = useMemo(() => {
    let list = bookingsWithCancels.filter((b) => {
      if (filter !== 'all' && b.status !== filter) return false;
      if (search) {
        const q = search.toLowerCase();
        return b.eventTitle.toLowerCase().includes(q) || b.id.toLowerCase().includes(q);
      }
      return true;
    });
    list.sort((a, b) => {
      if (sortBy === 'date-desc') return new Date(b.date) - new Date(a.date);
      if (sortBy === 'date-asc')  return new Date(a.date) - new Date(b.date);
      if (sortBy === 'price-high') return b.total - a.total;
      if (sortBy === 'price-low')  return a.total - b.total;
      return 0;
    });
    return list;
  }, [bookingsWithCancels, filter, search, sortBy]);

  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);
  const paginated = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const stats = useMemo(() => ({
    total: bookingsWithCancels.length,
    confirmed: bookingsWithCancels.filter((b) => b.status === 'CONFIRMED').length,
    pending: bookingsWithCancels.filter((b) => b.status === 'PENDING').length,
    cancelled: bookingsWithCancels.filter((b) => b.status === 'CANCELLED').length,
  }), [bookingsWithCancels]);

  const handleCancel = (id) => {
    setCancelledIds((prev) => new Set([...prev, id]));
  };

  return (
    <div className="max-w-5xl mx-auto px-4 md:px-6 py-8 space-y-8">
      {/* Header */}
      <motion.div {...fadeInUp}>
        <h1 className="text-3xl font-bold mb-1">My Bookings</h1>
        <p className="text-muted-foreground">View, manage, cancel, or reschedule your ticket bookings</p>
      </motion.div>

      {/* Stats Strip */}
      <motion.div {...fadeInUp} transition={{ delay: 0.05 }} className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Total', value: stats.total, icon: Ticket, color: 'text-primary' },
          { label: 'Confirmed', value: stats.confirmed, icon: CheckCircle2, color: 'text-emerald-500' },
          { label: 'Pending', value: stats.pending, icon: Timer, color: 'text-amber-500' },
          { label: 'Cancelled', value: stats.cancelled, icon: Ban, color: 'text-destructive' },
        ].map((s) => (
          <Card key={s.label} className="p-4 flex items-center gap-3">
            <s.icon className={cn('h-5 w-5', s.color)} />
            <div>
              <p className="text-xl font-bold">{s.value}</p>
              <p className="text-xs text-muted-foreground">{s.label}</p>
            </div>
          </Card>
        ))}
      </motion.div>

      {/* PNR Lookup */}
      <motion.div {...fadeInUp} transition={{ delay: 0.1 }}>
        <PNRLookup />
      </motion.div>

      {/* Filters */}
      <motion.div {...fadeInUp} transition={{ delay: 0.15 }} className="flex flex-col sm:flex-row gap-4">
        <div className="relative flex-1">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by event name or booking ID..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(1); }}
            className="pl-9"
          />
          {search && (
            <button
              onClick={() => { setSearch(''); setPage(1); }}
              className="absolute right-3 top-1/2 -translate-y-1/2"
            >
              <X className="h-4 w-4 text-muted-foreground hover:text-foreground" />
            </button>
          )}
        </div>
        <div className="flex gap-2">
          <select
            value={sortBy}
            onChange={(e) => setSortBy(e.target.value)}
            className="h-10 rounded-xl border border-input bg-background px-3 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
          >
            <option value="date-desc">Newest first</option>
            <option value="date-asc">Oldest first</option>
            <option value="price-high">Price: High → Low</option>
            <option value="price-low">Price: Low → High</option>
          </select>
        </div>
      </motion.div>

      <Tabs value={filter} onValueChange={(v) => { setFilter(v); setPage(1); }}>
        <TabsList>
          <TabsTrigger value="all">All ({stats.total})</TabsTrigger>
          <TabsTrigger value="CONFIRMED">Confirmed ({stats.confirmed})</TabsTrigger>
          <TabsTrigger value="PENDING">Pending ({stats.pending})</TabsTrigger>
          <TabsTrigger value="CANCELLED">Cancelled ({stats.cancelled})</TabsTrigger>
        </TabsList>
      </Tabs>

      {/* Booking Cards */}
      {paginated.length > 0 ? (
        <motion.div variants={staggerContainer} initial="initial" animate="animate" className="space-y-4">
          {paginated.map((booking) => {
            const cfg = statusConfig[booking.status] || statusConfig.CONFIRMED;
            const isExpanded = expandedId === booking.id;

            return (
              <motion.div key={booking.id} variants={staggerItem}>
                <Card className="hover:shadow-md transition-shadow overflow-hidden">
                  <CardContent className="p-6">
                    <div className="flex flex-col md:flex-row md:items-center gap-4">
                      <div className="flex-1 space-y-3">
                        <div className="flex items-start justify-between">
                          <div>
                            <h3 className="font-semibold text-lg">{booking.eventTitle}</h3>
                            <p className="text-sm text-muted-foreground font-mono">{booking.id}</p>
                          </div>
                          <Badge variant={cfg.variant} className="text-xs gap-1">
                            <cfg.icon className="h-3 w-3" /> {cfg.label}
                          </Badge>
                        </div>

                        <div className="flex items-center gap-6 text-sm text-muted-foreground flex-wrap">
                          <div className="flex items-center gap-1.5">
                            <Calendar className="h-3.5 w-3.5" /> {formatDate(booking.date)}
                          </div>
                          <div className="flex items-center gap-1.5">
                            <Clock className="h-3.5 w-3.5" /> {formatTime(booking.date)}
                          </div>
                          <div className="flex items-center gap-1.5">
                            <MapPin className="h-3.5 w-3.5" /> {booking.venue}
                          </div>
                        </div>

                        <div className="flex items-center gap-3">
                          <div className="flex items-center gap-1.5">
                            <Ticket className="h-4 w-4 text-indigo-500" />
                            <span className="text-sm font-medium">Seats: {booking.seats.join(', ')}</span>
                          </div>
                          <Separator orientation="vertical" className="h-4" />
                          <span className="text-lg font-bold text-gradient">{formatCurrency(booking.total)}</span>
                        </div>

                        <BookingTimeline booking={booking} />
                      </div>

                      {/* Actions */}
                      <div className="flex flex-wrap items-center gap-2 md:flex-col md:items-end shrink-0">
                        {booking.status === 'CONFIRMED' && (
                          <>
                            <Button variant="outline" size="sm" className="gap-1.5 text-xs">
                              <Download className="h-3.5 w-3.5" /> E-Ticket
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="gap-1.5 text-xs text-amber-600 border-amber-200 dark:border-amber-800 hover:bg-amber-50 dark:hover:bg-amber-900/20"
                            >
                              <RotateCcw className="h-3.5 w-3.5" /> Reschedule
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="gap-1.5 text-xs text-destructive border-destructive/30 hover:bg-destructive/5"
                              onClick={() => setCancelTarget(booking)}
                            >
                              <XCircle className="h-3.5 w-3.5" /> Cancel
                            </Button>
                          </>
                        )}
                        {booking.status === 'PENDING' && (
                          <Button size="sm" className="gap-1.5 text-xs">
                            <RefreshCw className="h-3.5 w-3.5" /> Retry Payment
                          </Button>
                        )}
                        {booking.status === 'CANCELLED' && (
                          <Badge variant="outline" className="text-xs gap-1 text-muted-foreground">
                            <ReceiptText className="h-3 w-3" /> Refund processing
                          </Badge>
                        )}
                        <Button
                          variant="ghost"
                          size="sm"
                          className="gap-1.5 text-xs"
                          onClick={() => setExpandedId(isExpanded ? null : booking.id)}
                        >
                          <Eye className="h-3.5 w-3.5" /> {isExpanded ? 'Hide' : 'Details'}
                        </Button>
                      </div>
                    </div>

                    {/* Expanded Details */}
                    <AnimatePresence>
                      {isExpanded && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          className="overflow-hidden"
                        >
                          <Separator className="my-4" />
                          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
                            <div>
                              <p className="text-xs text-muted-foreground mb-0.5">Payment Method</p>
                              <p className="font-medium">Visa •••• 4242</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-0.5">Booked On</p>
                              <p className="font-medium">{formatDate(booking.date)}</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-0.5">Passengers</p>
                              <p className="font-medium">{booking.seats.length}</p>
                            </div>
                            <div>
                              <p className="text-xs text-muted-foreground mb-0.5">Contact</p>
                              <p className="font-medium flex items-center gap-1">
                                <Mail className="h-3 w-3" /> alex@example.com
                              </p>
                            </div>
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </CardContent>
                </Card>
              </motion.div>
            );
          })}
        </motion.div>
      ) : (
        <motion.div {...fadeInUp} className="text-center py-20">
          <Ticket className="h-16 w-16 text-muted-foreground/30 mx-auto mb-4" />
          <h3 className="text-xl font-semibold mb-2">No bookings found</h3>
          <p className="text-muted-foreground mb-6">
            {search ? `No results for "${search}"` : filter !== 'all' ? 'Try changing your filters' : "You haven't booked any events yet"}
          </p>
          <Link to="/events">
            <Button>Browse Events</Button>
          </Link>
        </motion.div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex justify-center">
          <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
        </div>
      )}

      {/* Cancel Dialog */}
      <AnimatePresence>
        {cancelTarget && (
          <CancelDialog
            booking={cancelTarget}
            onClose={() => setCancelTarget(null)}
            onConfirm={handleCancel}
          />
        )}
      </AnimatePresence>
    </div>
  );
}
