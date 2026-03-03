import { useState, useMemo, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Calendar, MapPin, Clock, Users, Share2, Heart,
  ArrowLeft, ChevronRight, Tag, Shield, Ticket, Loader2,
} from 'lucide-react';
import {
  Button, Badge, Card, CardContent, CardHeader, CardTitle,
  Separator, Tabs, TabsList, TabsTrigger, TabsContent,
} from '@/components/ui';
import SeatMap from '@/components/features/SeatMap';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import { fadeInUp } from '@/lib/animations';
import { api } from '@/lib/api';

/**
 * Build a SeatMap-compatible structure from API seat data.
 * Groups seats by class (section) with rows/numbers.
 */
function buildSeatMap(apiSeats, baseFare) {
  const sections = {};
  const priceMap = { BUSINESS: 1.5, FIRST: 2.0, ECONOMY: 1.0 };
  const sectionNames = { BUSINESS: 'Premium', FIRST: 'VIP', ECONOMY: 'Standard' };

  apiSeats.forEach((s) => {
    const seatNum = s.seatNumber || '';
    const row = seatNum.replace(/[0-9]/g, '') || 'A';
    const num = parseInt(seatNum.replace(/[^0-9]/g, ''), 10) || 1;
    const sectionKey = sectionNames[s.seatClass] || 'Standard';
    const price = Math.round(Number(baseFare) * (priceMap[s.seatClass] || 1));

    let status = 'available';
    if (s.seatStatus === 'HELD') status = 'held';
    else if (s.seatStatus === 'BOOKED') status = 'sold';
    else if (s.seatStatus === 'BLOCKED') status = 'sold';

    if (!sections[sectionKey]) sections[sectionKey] = [];
    sections[sectionKey].push({
      id: s.id,
      section: sectionKey,
      row,
      number: num,
      label: seatNum,
      price,
      status,
    });
  });

  // Sort seats within each section
  Object.keys(sections).forEach((key) => {
    sections[key].sort((a, b) => a.row.localeCompare(b.row) || a.number - b.number);
  });

  return sections;
}

export default function EventDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [liked, setLiked] = useState(false);
  const [schedule, setSchedule] = useState(null);
  const [seatMap, setSeatMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    setLoading(true);
    setError('');
    Promise.all([
      api.get(`/schedules/${id}`),
      api.get(`/schedules/${id}/seats`),
    ])
      .then(([schedRes, seatsRes]) => {
        const sched = schedRes.data;
        setSchedule(sched);
        const seats = seatsRes.data || [];
        const map = buildSeatMap(seats, sched.baseFare || sched.dynamicPrice || 100);
        setSeatMap(map);
      })
      .catch((err) => setError(err.message || 'Failed to load schedule'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <span className="ml-3 text-muted-foreground">Loading schedule...</span>
      </div>
    );
  }

  if (error || !schedule) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <h2 className="text-2xl font-bold mb-4">{error || 'Schedule not found'}</h2>
        <Button onClick={() => navigate(-1)}>Go Back</Button>
      </div>
    );
  }

  const totalPrice = selectedSeats.reduce((sum, s) => sum + s.price, 0);
  const isConcert = schedule.transportMode === 'CONCERT';
  const displayTitle = isConcert ? schedule.originCity : `${schedule.originCity} → ${schedule.destinationCity}`;
  const displaySubtitle = isConcert ? `${schedule.vehicleNumber}, ${schedule.destinationCity}` : `${schedule.vehicleNumber} • ${schedule.durationMinutes ? `${Math.floor(schedule.durationMinutes / 60)}h ${schedule.durationMinutes % 60}m` : ''}`;

  const handleProceedToCheckout = () => {
    navigate('/checkout', {
      state: { schedule, selectedSeats, totalPrice, scheduleId: id },
    });
  };

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-6 py-8 space-y-8">
      {/* Breadcrumb */}
      <motion.div {...fadeInUp} className="flex items-center gap-2 text-sm text-muted-foreground">
        <button onClick={() => navigate(-1)} className="flex items-center gap-1 hover:text-foreground transition-colors">
          <ArrowLeft className="h-4 w-4" /> Back
        </button>
        <ChevronRight className="h-3 w-3" />
        <span>Events</span>
        <ChevronRight className="h-3 w-3" />
        <span className="text-foreground">{displayTitle}</span>
      </motion.div>

      {/* Hero Banner */}
      <motion.div
        {...fadeInUp}
        transition={{ delay: 0.1 }}
        className="relative h-48 md:h-64 rounded-3xl overflow-hidden bg-gradient-to-r from-indigo-600 to-purple-600"
      >
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent" />
        <div className="absolute bottom-6 left-6 right-6 text-white">
          <div className="flex items-center gap-2 mb-3">
            <Badge className="text-xs">{schedule.vehicleNumber}</Badge>
            {schedule.demandFactor > 1 && <Badge variant="warning" className="text-xs">High Demand</Badge>}
          </div>
          <h1 className="text-3xl md:text-4xl font-bold mb-2">{displayTitle}</h1>
          <p className="text-lg text-white/80">{displaySubtitle}</p>
        </div>
        <div className="absolute top-4 right-4 flex gap-2">
          <Button
            variant="ghost"
            size="icon"
            className="bg-white/20 backdrop-blur-sm text-white hover:bg-white/30"
            onClick={() => setLiked(!liked)}
          >
            <Heart className={`h-5 w-5 ${liked ? 'fill-red-500 text-red-500' : ''}`} />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="bg-white/20 backdrop-blur-sm text-white hover:bg-white/30"
          >
            <Share2 className="h-5 w-5" />
          </Button>
        </div>
      </motion.div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Main content */}
        <div className="lg:col-span-2 space-y-8">
          {/* Event Info */}
          <motion.div {...fadeInUp} transition={{ delay: 0.2 }}>
            <Card>
              <CardContent className="p-6">
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-indigo-500/10">
                      <Calendar className="h-5 w-5 text-indigo-500" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">Departure</p>
                      <p className="font-medium">{schedule.departureTime ? new Date(schedule.departureTime).toLocaleDateString() : 'N/A'}</p>
                      <p className="text-xs text-muted-foreground">{schedule.departureTime ? new Date(schedule.departureTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-indigo-500/10">
                      <Clock className="h-5 w-5 text-indigo-500" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">Arrival</p>
                      <p className="font-medium">{schedule.arrivalTime ? new Date(schedule.arrivalTime).toLocaleDateString() : 'N/A'}</p>
                      <p className="text-xs text-muted-foreground">{schedule.arrivalTime ? new Date(schedule.arrivalTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-indigo-500/10">
                      <MapPin className="h-5 w-5 text-indigo-500" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">{isConcert ? 'Venue' : 'Route'}</p>
                      <p className="font-medium">{displayTitle}</p>
                      <p className="text-xs text-muted-foreground">{isConcert ? `${schedule.vehicleNumber}, ${schedule.destinationCity}` : schedule.vehicleNumber}</p>
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          </motion.div>

          {/* Seat Selection */}
          <motion.div {...fadeInUp} transition={{ delay: 0.3 }}>
            <Tabs defaultValue="seats" className="space-y-4">
              <TabsList>
                <TabsTrigger value="seats">Select Seats</TabsTrigger>
                <TabsTrigger value="info">Event Info</TabsTrigger>
                <TabsTrigger value="venue">Venue</TabsTrigger>
              </TabsList>

              <TabsContent value="seats">
                <Card>
                  <CardHeader>
                    <CardTitle className="text-lg">Choose Your Seats</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <SeatMap seatMap={seatMap} onSelectionChange={setSelectedSeats} />
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="info">
                <Card>
                  <CardContent className="p-6 prose dark:prose-invert max-w-none">
                    <h3>About this Event</h3>
                    <p className="text-muted-foreground">
                      {isConcert
                        ? `Live concert at ${schedule.vehicleNumber}, ${schedule.destinationCity}. Don't miss this amazing experience!`
                        : `Travel from ${schedule.originCity} to ${schedule.destinationCity} on vehicle ${schedule.vehicleNumber}.`}
                      {' '}Base fare starts at {formatCurrency(Number(schedule.baseFare || 0))} with dynamic pricing based on demand.
                    </p>
                    <h4>What to Expect</h4>
                    <ul className="text-muted-foreground space-y-2">
                      <li>World-class entertainment and production</li>
                      <li>State-of-the-art sound and lighting</li>
                      <li>Food and beverage options available</li>
                      <li>Accessible seating available upon request</li>
                    </ul>
                    <h4>Important Notes</h4>
                    <ul className="text-muted-foreground space-y-2">
                      <li>Doors open 1 hour before showtime</li>
                      <li>No professional cameras or recording equipment</li>
                      <li>All sales are subject to our refund policy</li>
                    </ul>
                  </CardContent>
                </Card>
              </TabsContent>

              <TabsContent value="venue">
                <Card>
                  <CardContent className="p-6">
                    <h3 className="font-semibold text-lg mb-4">{displayTitle}</h3>
                    <p className="text-muted-foreground mb-4">{isConcert ? `Venue: ${schedule.vehicleNumber}` : `Vehicle: ${schedule.vehicleNumber}`}</p>
                    <div className="h-48 bg-muted rounded-xl flex items-center justify-center text-muted-foreground">
                      <MapPin className="h-8 w-8 mr-2" />
                      {isConcert ? 'Venue map placeholder' : 'Route map placeholder'}
                    </div>
                  </CardContent>
                </Card>
              </TabsContent>
            </Tabs>
          </motion.div>
        </div>

        {/* Sidebar — Booking Summary */}
        <motion.div {...fadeInUp} transition={{ delay: 0.4 }} className="space-y-6">
          <Card className="sticky top-20">
            <CardHeader>
              <CardTitle className="text-lg">Booking Summary</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Price */}
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Base fare</span>
                <span className="font-medium">{formatCurrency(Number(schedule.baseFare || 0))}</span>
              </div>
              {schedule.dynamicPrice && Number(schedule.dynamicPrice) !== Number(schedule.baseFare) && (
                <div className="flex items-center justify-between">
                  <span className="text-sm text-muted-foreground">Dynamic price</span>
                  <span className="font-medium text-amber-600">{formatCurrency(Number(schedule.dynamicPrice))}</span>
                </div>
              )}

              {/* Availability */}
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Available</span>
                <div className="flex items-center gap-1.5">
                  <Users className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">{schedule.availableSeats || 0} / {schedule.totalSeats || 0}</span>
                </div>
              </div>

              <Separator />

              {/* Selected seats */}
              {selectedSeats.length > 0 ? (
                <div className="space-y-2">
                  <p className="text-sm font-medium">Selected Seats</p>
                  {selectedSeats.map((seat) => (
                    <div key={seat.id} className="flex items-center justify-between text-sm">
                      <span className="text-muted-foreground">
                        {seat.section} — Row {seat.row}, Seat {seat.number}
                      </span>
                      <span>{formatCurrency(seat.price)}</span>
                    </div>
                  ))}
                  <Separator />
                  <div className="flex items-center justify-between font-bold text-lg">
                    <span>Total</span>
                    <span className="text-gradient">{formatCurrency(totalPrice)}</span>
                  </div>
                </div>
              ) : (
                <div className="text-center py-4 text-sm text-muted-foreground">
                  <Ticket className="h-8 w-8 mx-auto mb-2 opacity-40" />
                  Select seats to see pricing
                </div>
              )}

              <Button
                size="lg"
                className="w-full"
                disabled={selectedSeats.length === 0}
                onClick={handleProceedToCheckout}
              >
                Proceed to Checkout
                <ChevronRight className="ml-2 h-4 w-4" />
              </Button>

              {/* Trust badges */}
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                <Shield className="h-4 w-4 text-emerald-500" />
                <span>Secure checkout • Money-back guarantee</span>
              </div>
            </CardContent>
          </Card>
        </motion.div>
      </div>
    </div>
  );
}
