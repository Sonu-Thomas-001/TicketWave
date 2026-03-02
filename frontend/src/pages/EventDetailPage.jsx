import { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  Calendar, MapPin, Clock, Users, Share2, Heart,
  ArrowLeft, ChevronRight, Tag, Shield, Ticket,
} from 'lucide-react';
import {
  Button, Badge, Card, CardContent, CardHeader, CardTitle,
  Separator, Tabs, TabsList, TabsTrigger, TabsContent,
} from '@/components/ui';
import SeatMap from '@/components/features/SeatMap';
import { mockEvents, generateSeatMap } from '@/lib/mockData';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import { fadeInUp } from '@/lib/animations';

export default function EventDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [selectedSeats, setSelectedSeats] = useState([]);
  const [liked, setLiked] = useState(false);

  const event = mockEvents.find((e) => e.id === id);
  const seatMap = useMemo(() => generateSeatMap(), []);

  if (!event) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <h2 className="text-2xl font-bold mb-4">Event not found</h2>
        <Button onClick={() => navigate('/events')}>Browse Events</Button>
      </div>
    );
  }

  const totalPrice = selectedSeats.reduce((sum, s) => sum + s.price, 0);

  const handleProceedToCheckout = () => {
    navigate('/checkout', {
      state: { event, selectedSeats, totalPrice },
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
        <span className="text-foreground">{event.title}</span>
      </motion.div>

      {/* Hero Banner */}
      <motion.div
        {...fadeInUp}
        transition={{ delay: 0.1 }}
        className="relative h-64 md:h-80 lg:h-96 rounded-3xl overflow-hidden"
      >
        <img
          src={event.imageUrl}
          alt={event.title}
          className="w-full h-full object-cover"
          loading="lazy"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent" />
        <div className="absolute bottom-6 left-6 right-6 text-white">
          <div className="flex items-center gap-2 mb-3">
            <Badge className="text-xs">{event.category}</Badge>
            {event.featured && <Badge variant="warning" className="text-xs">Featured</Badge>}
          </div>
          <h1 className="text-3xl md:text-4xl font-bold mb-2">{event.title}</h1>
          {event.artist && (
            <p className="text-lg text-white/80">by {event.artist}</p>
          )}
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
                      <p className="text-sm text-muted-foreground">Date</p>
                      <p className="font-medium">{formatDate(event.date)}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-indigo-500/10">
                      <Clock className="h-5 w-5 text-indigo-500" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">Time</p>
                      <p className="font-medium">{formatTime(event.date)}</p>
                    </div>
                  </div>
                  <div className="flex items-start gap-3">
                    <div className="flex items-center justify-center h-10 w-10 rounded-xl bg-indigo-500/10">
                      <MapPin className="h-5 w-5 text-indigo-500" />
                    </div>
                    <div>
                      <p className="text-sm text-muted-foreground">Venue</p>
                      <p className="font-medium">{event.venue}</p>
                      <p className="text-xs text-muted-foreground">{event.city}</p>
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
                      Experience an unforgettable evening at {event.venue} in {event.city}.
                      This {event.category.toLowerCase()} event promises to deliver an extraordinary
                      performance that you'll remember for years to come.
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
                    <h3 className="font-semibold text-lg mb-4">{event.venue}</h3>
                    <p className="text-muted-foreground mb-4">{event.city}</p>
                    <div className="h-48 bg-muted rounded-xl flex items-center justify-center text-muted-foreground">
                      <MapPin className="h-8 w-8 mr-2" />
                      Map placeholder — integrates with Google Maps
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
              {/* Price range */}
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Price range</span>
                <span className="font-medium">
                  {formatCurrency(event.price.min)} – {formatCurrency(event.price.max)}
                </span>
              </div>

              {/* Availability */}
              <div className="flex items-center justify-between">
                <span className="text-sm text-muted-foreground">Available</span>
                <div className="flex items-center gap-1.5">
                  <Users className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">{event.availableSeats.toLocaleString()}</span>
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
