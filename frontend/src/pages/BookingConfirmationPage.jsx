import { useEffect, useState, useRef } from 'react';
import { useLocation, useNavigate, Link } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  CheckCircle2, Download, Share2, CalendarPlus, Ticket,
  Calendar, MapPin, Clock, ArrowRight, Copy, ExternalLink,
  Mail, Printer, QrCode,
} from 'lucide-react';
import {
  Button, Card, CardContent, Badge, Separator,
} from '@/components/ui';
import { formatCurrency, formatDate, formatTime, cn } from '@/lib/utils';
import { fadeInUp } from '@/lib/animations';

/* ---------- tiny confetti ---------- */
function Confetti() {
  const colors = ['#6366f1', '#f59e0b', '#10b981', '#ef4444', '#ec4899', '#8b5cf6'];
  return (
    <div className="fixed inset-0 pointer-events-none z-50 overflow-hidden" aria-hidden="true">
      {Array.from({ length: 80 }).map((_, i) => {
        const color = colors[i % colors.length];
        const left = Math.random() * 100;
        const delay = Math.random() * 2;
        const size = 6 + Math.random() * 8;
        const duration = 2.5 + Math.random() * 2;
        return (
          <motion.div
            key={i}
            className="absolute rounded-sm"
            style={{ left: `${left}%`, width: size, height: size, backgroundColor: color }}
            initial={{ y: -20, opacity: 1, rotate: 0 }}
            animate={{ y: '110vh', opacity: 0, rotate: 360 + Math.random() * 720 }}
            transition={{ duration, delay, ease: 'easeIn' }}
          />
        );
      })}
    </div>
  );
}

export default function BookingConfirmationPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { event, schedule, selectedSeats, totalPrice, passengers, bookingId: apiBid, pnr: apiPnr, bookingStatus } = location.state || {};
  const scheduleData = schedule || event;
  const [copied, setCopied] = useState(false);
  const [showConfetti, setShowConfetti] = useState(true);

  const bookingId = useRef(apiBid || `TW-${Date.now().toString(36).toUpperCase()}`).current;
  const pnr = useRef(
    apiPnr || Math.random().toString(36).substring(2, 8).toUpperCase()
  ).current;

  useEffect(() => {
    const timer = setTimeout(() => setShowConfetti(false), 5000);
    return () => clearTimeout(timer);
  }, []);

  const handleCopyPNR = () => {
    navigator.clipboard?.writeText(pnr);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  if (!scheduleData || !selectedSeats?.length) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <Ticket className="h-16 w-16 mx-auto mb-4 text-muted-foreground/30" />
        <h2 className="text-2xl font-bold mb-4">No booking found</h2>
        <Button onClick={() => navigate('/events')}>Browse Events</Button>
      </div>
    );
  }

  return (
    <>
      <AnimatePresence>{showConfetti && <Confetti />}</AnimatePresence>

      <div className="max-w-3xl mx-auto px-4 md:px-6 py-12 space-y-8">
        {/* Success Hero */}
        <motion.div {...fadeInUp} className="text-center space-y-4">
          <motion.div
            initial={{ scale: 0 }}
            animate={{ scale: 1 }}
            transition={{ type: 'spring', damping: 12, stiffness: 180, delay: 0.3 }}
            className="inline-flex items-center justify-center h-24 w-24 rounded-full bg-emerald-500/15 mx-auto"
          >
            <motion.div
              initial={{ pathLength: 0 }}
              animate={{ pathLength: 1 }}
              transition={{ duration: 0.6, delay: 0.6 }}
            >
              <CheckCircle2 className="h-12 w-12 text-emerald-500" />
            </motion.div>
          </motion.div>

          <motion.h1
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.5 }}
            className="text-3xl font-bold"
          >
            Booking Confirmed!
          </motion.h1>

          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ delay: 0.7 }}
            className="text-muted-foreground max-w-md mx-auto"
          >
            Your tickets have been booked successfully. A confirmation email has been sent to your registered email address.
          </motion.p>
        </motion.div>

        {/* PNR Badge */}
        <motion.div {...fadeInUp} transition={{ delay: 0.4 }} className="flex justify-center">
          <div className="glass-card-strong rounded-2xl px-8 py-5 text-center space-y-1">
            <p className="text-xs uppercase tracking-wider text-muted-foreground font-medium">Your PNR</p>
            <div className="flex items-center gap-3">
              <span className="text-3xl font-mono font-bold tracking-widest text-gradient">{pnr}</span>
              <button
                onClick={handleCopyPNR}
                className="p-2 rounded-xl hover:bg-muted transition-colors"
                title="Copy PNR"
              >
                {copied ? <CheckCircle2 className="h-4 w-4 text-emerald-500" /> : <Copy className="h-4 w-4 text-muted-foreground" />}
              </button>
            </div>
          </div>
        </motion.div>

        {/* Ticket Summary */}
        <motion.div {...fadeInUp} transition={{ delay: 0.5 }}>
          <Card>
            <CardContent className="p-6 space-y-5">
              <div className="flex gap-4">
                <div className="w-28 h-28 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 flex items-center justify-center text-white font-bold text-xl">
                  {scheduleData.transportMode === 'CONCERT' ? '🎵' : `${scheduleData.originCity?.charAt(0)}${scheduleData.destinationCity?.charAt(0)}`}
                </div>
                <div className="space-y-1.5">
                  <h3 className="font-semibold text-lg">{scheduleData.transportMode === 'CONCERT' ? scheduleData.originCity : `${scheduleData.originCity} → ${scheduleData.destinationCity}`}</h3>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Calendar className="h-3.5 w-3.5" /> {scheduleData.departureTime ? new Date(scheduleData.departureTime).toLocaleDateString() : 'N/A'}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <Clock className="h-3.5 w-3.5" /> {scheduleData.departureTime ? new Date(scheduleData.departureTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                  </div>
                  <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    <MapPin className="h-3.5 w-3.5" /> {scheduleData.vehicleNumber || 'N/A'}
                  </div>
                </div>
              </div>

              <Separator />

              {/* Seats & Passengers */}
              <div>
                <h4 className="font-medium mb-3">Tickets</h4>
                <div className="space-y-2">
                  {selectedSeats.map((seat, i) => (
                    <div key={seat.id} className="flex items-center justify-between py-2 px-4 rounded-xl bg-muted/50">
                      <div className="flex items-center gap-3">
                        <Badge variant="secondary" className="text-xs">{seat.section}</Badge>
                        <div>
                          <span className="text-sm font-medium">Row {seat.row}, Seat {seat.number}</span>
                          {passengers?.[i] && (
                            <p className="text-xs text-muted-foreground">{passengers[i].name}</p>
                          )}
                        </div>
                      </div>
                      <span className="font-medium">{formatCurrency(seat.price)}</span>
                    </div>
                  ))}
                </div>
              </div>

              <Separator />

              <div className="space-y-2 text-sm">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Booking ID</span>
                  <span className="font-mono text-xs">{bookingId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Status</span>
                  <Badge className="bg-emerald-500/15 text-emerald-600 dark:text-emerald-400 border-0">Confirmed</Badge>
                </div>
                <div className="flex justify-between font-bold text-lg pt-2">
                  <span>Total Paid</span>
                  <span className="text-gradient">{formatCurrency(totalPrice)}</span>
                </div>
              </div>
            </CardContent>
          </Card>
        </motion.div>

        {/* QR placeholder */}
        <motion.div {...fadeInUp} transition={{ delay: 0.6 }} className="flex justify-center">
          <div className="glass-card rounded-2xl p-6 text-center space-y-3 w-48">
            <QrCode className="h-20 w-20 mx-auto text-muted-foreground/40" />
            <p className="text-xs text-muted-foreground">Scan at venue entry</p>
          </div>
        </motion.div>

        {/* Actions */}
        <motion.div {...fadeInUp} transition={{ delay: 0.7 }} className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <Button variant="outline" className="gap-2 h-auto py-3 flex-col">
            <Download className="h-5 w-5" />
            <span className="text-xs">Download PDF</span>
          </Button>
          <Button variant="outline" className="gap-2 h-auto py-3 flex-col">
            <CalendarPlus className="h-5 w-5" />
            <span className="text-xs">Add to Calendar</span>
          </Button>
          <Button variant="outline" className="gap-2 h-auto py-3 flex-col">
            <Mail className="h-5 w-5" />
            <span className="text-xs">Email Ticket</span>
          </Button>
          <Button variant="outline" className="gap-2 h-auto py-3 flex-col">
            <Printer className="h-5 w-5" />
            <span className="text-xs">Print</span>
          </Button>
        </motion.div>

        {/* Navigation */}
        <motion.div {...fadeInUp} transition={{ delay: 0.8 }} className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-4">
          <Button variant="outline" size="lg" onClick={() => navigate('/bookings')}>
            View My Bookings
          </Button>
          <Button size="lg" onClick={() => navigate('/events')}>
            Browse More Events <ArrowRight className="ml-2 h-4 w-4" />
          </Button>
        </motion.div>
      </div>
    </>
  );
}
