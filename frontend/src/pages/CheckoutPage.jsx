import { useState, useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowLeft, CreditCard, Lock, CheckCircle2, User,
  Calendar, MapPin, Clock, ChevronRight, Shield, Ticket,
  Tag, Plus, Trash2, AlertCircle,
} from 'lucide-react';
import {
  Button, Input, Card, CardContent, CardHeader, CardTitle,
  Badge, Separator, Stepper,
} from '@/components/ui';
import { formatCurrency, formatDate, formatTime } from '@/lib/utils';
import { fadeInUp } from '@/lib/animations';
import { api } from '@/lib/api';

const checkoutSteps = [
  { label: 'Passenger Details', description: 'Who is traveling?' },
  { label: 'Review Booking', description: 'Confirm your order' },
  { label: 'Payment', description: 'Secure checkout' },
];

const savedPassengers = [
  { id: '1', name: 'Alex Johnson', email: 'alex@example.com', phone: '+1 555-0123', age: 28 },
];

export default function CheckoutPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { event, schedule, selectedSeats, totalPrice, scheduleId } = location.state || {};
  const scheduleData = schedule || event;
  const effectiveScheduleId = scheduleId || scheduleData?.scheduleId;
  const [currentStep, setCurrentStep] = useState(0);
  const [isProcessing, setIsProcessing] = useState(false);
  const [promoCode, setPromoCode] = useState('');
  const [promoApplied, setPromoApplied] = useState(false);
  const [promoDiscount, setPromoDiscount] = useState(0);
  const [passengers, setPassengers] = useState(
    selectedSeats?.map((_, i) => ({
      name: i === 0 ? 'Alex Johnson' : '',
      email: i === 0 ? 'alex@example.com' : '',
      phone: i === 0 ? '+1 555-0123' : '',
      age: '',
    })) || []
  );
  const [paymentData, setPaymentData] = useState({
    cardName: '', cardNumber: '', expiry: '', cvc: '', email: '',
  });

  if (!scheduleData || !selectedSeats?.length) {
    return (
      <div className="max-w-3xl mx-auto px-4 py-20 text-center">
        <Ticket className="h-16 w-16 mx-auto mb-4 text-muted-foreground/30" />
        <h2 className="text-2xl font-bold mb-4">No seats selected</h2>
        <Button onClick={() => navigate('/events')}>Browse Events</Button>
      </div>
    );
  }

  const serviceFee = totalPrice * 0.05;
  const discount = promoApplied ? promoDiscount : 0;
  const finalTotal = totalPrice + serviceFee - discount;

  const handleApplyPromo = () => {
    if (promoCode.toLowerCase() === 'wave10') {
      setPromoApplied(true);
      setPromoDiscount(totalPrice * 0.1);
    }
  };

  const updatePassenger = (index, field, value) => {
    setPassengers((prev) => {
      const updated = [...prev];
      updated[index] = { ...updated[index], [field]: value };
      return updated;
    });
  };

  const fillFromSaved = (index, saved) => {
    setPassengers((prev) => {
      const updated = [...prev];
      updated[index] = { name: saved.name, email: saved.email, phone: saved.phone, age: String(saved.age) };
      return updated;
    });
  };

  const handlePayment = async () => {
    setIsProcessing(true);
    try {
      const token = localStorage.getItem('tw-token');
      const authHeaders = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
      };

      // Step 1: Hold the selected seats in Redis
      const holdRes = await fetch(`/api/v1/schedules/${effectiveScheduleId}/hold`, {
        method: 'POST',
        headers: authHeaders,
        body: JSON.stringify({ seatIds: selectedSeats.map((s) => s.id) }),
      });
      const holdData = await holdRes.json();
      if (!holdData.success || !holdData.data) {
        throw new Error(holdData.message || 'Failed to hold seats');
      }

      // Step 2: Create booking using the valid holds
      const seatHolds = holdData.data.map((h) => ({
        seatId: h.seatId,
        userId: null,
        holdToken: h.holdToken,
        heldAt: h.heldAt,
        expiresAt: h.expiresAt,
      }));

      const idempotencyKey = crypto.randomUUID();
      const res = await fetch('/api/v1/bookings', {
        method: 'POST',
        headers: {
          ...authHeaders,
          'X-Idempotency-Key': idempotencyKey,
        },
        body: JSON.stringify({
          scheduleId: effectiveScheduleId,
          seatHolds,
          passengerBookings: {},
        }),
      });
      const data = await res.json();

      if (data.success && data.data) {
        // Simulate payment confirmation via webhook
        const intentId = data.data.paymentIntent?.intentId;
        if (intentId) {
          await api.post('/webhooks/payment/confirmed', {
            eventId: crypto.randomUUID(),
            eventType: 'payment.confirmed',
            intentId,
            transactionId: 'txn_' + crypto.randomUUID().slice(0, 8),
            amount: finalTotal,
            paymentMethod: 'card',
            timestamp: new Date().toISOString(),
          });
        }

        navigate('/booking-confirmation', {
          state: {
            schedule: scheduleData,
            selectedSeats,
            totalPrice: finalTotal,
            passengers,
            bookingId: data.data.bookingId,
            pnr: data.data.pnr,
            bookingStatus: data.data.bookingStatus,
          },
        });
      } else {
        alert('Booking failed: ' + (data.message || 'Unknown error'));
      }
    } catch (err) {
      alert('Booking error: ' + err.message);
    } finally {
      setIsProcessing(false);
    }
  };

  const canProceedFromStep0 = passengers.every((p) => p.name && p.email);
  const canProceedFromStep1 = true;

  return (
    <div className="max-w-5xl mx-auto px-4 md:px-6 py-8 space-y-8">
      {/* Header */}
      <motion.div {...fadeInUp} className="flex items-center gap-4">
        <button onClick={() => navigate(-1)} className="flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground transition-colors">
          <ArrowLeft className="h-4 w-4" /> Back
        </button>
        <h1 className="text-2xl font-bold">Checkout</h1>
      </motion.div>

      {/* Stepper */}
      <motion.div {...fadeInUp} transition={{ delay: 0.1 }}>
        <Stepper steps={checkoutSteps} currentStep={currentStep} className="max-w-2xl mx-auto" />
      </motion.div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Main Content */}
        <div className="lg:col-span-2">
          <AnimatePresence mode="wait">
            {/* Step 1: Passenger Details */}
            {currentStep === 0 && (
              <motion.div key="passengers" {...fadeInUp} className="space-y-6">
                {passengers.map((passenger, i) => (
                  <Card key={i}>
                    <CardHeader className="pb-4">
                      <div className="flex items-center justify-between">
                        <CardTitle className="text-base flex items-center gap-2">
                          <User className="h-4 w-4 text-primary" />
                          Passenger {i + 1}
                          <Badge variant="secondary" className="text-2xs">{selectedSeats[i]?.label}</Badge>
                        </CardTitle>
                        {savedPassengers.length > 0 && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-xs gap-1"
                            onClick={() => fillFromSaved(i, savedPassengers[0])}
                          >
                            <Plus className="h-3 w-3" /> Use Saved
                          </Button>
                        )}
                      </div>
                    </CardHeader>
                    <CardContent className="space-y-4">
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Full Name *</label>
                          <Input
                            placeholder="John Doe"
                            value={passenger.name}
                            onChange={(e) => updatePassenger(i, 'name', e.target.value)}
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Email *</label>
                          <Input
                            type="email"
                            placeholder="john@example.com"
                            value={passenger.email}
                            onChange={(e) => updatePassenger(i, 'email', e.target.value)}
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Phone</label>
                          <Input
                            placeholder="+1 555-0123"
                            value={passenger.phone}
                            onChange={(e) => updatePassenger(i, 'phone', e.target.value)}
                          />
                        </div>
                        <div>
                          <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Age</label>
                          <Input
                            type="number"
                            placeholder="28"
                            value={passenger.age}
                            onChange={(e) => updatePassenger(i, 'age', e.target.value)}
                          />
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}

                <div className="flex justify-end">
                  <Button
                    size="lg"
                    onClick={() => setCurrentStep(1)}
                    disabled={!canProceedFromStep0}
                  >
                    Continue to Review <ChevronRight className="ml-2 h-4 w-4" />
                  </Button>
                </div>
              </motion.div>
            )}

            {/* Step 2: Review Booking */}
            {currentStep === 1 && (
              <motion.div key="review" {...fadeInUp} className="space-y-6">
                <Card>
                  <CardHeader>
                    <CardTitle className="text-lg">Review Your Order</CardTitle>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="flex gap-4">
                      <div className="w-24 h-24 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 flex items-center justify-center text-white font-bold text-sm">
                        {['CONCERT','SPORTS','THEATRE','FESTIVAL','SHOW'].includes(scheduleData.transportMode) ? '🎭' : `${scheduleData.originCity?.charAt(0)}${scheduleData.destinationCity?.charAt(0)}`}
                      </div>
                      <div>
                        <h3 className="font-semibold">{['CONCERT','SPORTS','THEATRE','FESTIVAL','SHOW'].includes(scheduleData.transportMode) ? scheduleData.originCity : `${scheduleData.originCity} \u2192 ${scheduleData.destinationCity}`}</h3>
                        <div className="flex items-center gap-2 text-sm text-muted-foreground mt-1">
                          <Calendar className="h-3.5 w-3.5" /> {scheduleData.departureTime ? new Date(scheduleData.departureTime).toLocaleDateString() : 'N/A'}
                        </div>
                        <div className="flex items-center gap-2 text-sm text-muted-foreground">
                          <Clock className="h-3.5 w-3.5" /> {scheduleData.departureTime ? new Date(scheduleData.departureTime).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                        </div>
                        <div className="flex items-center gap-2 text-sm text-muted-foreground">
                          <MapPin className="h-3.5 w-3.5" /> {scheduleData.vehicleNumber}
                        </div>
                      </div>
                    </div>

                    <Separator />

                    {/* Seats + Passengers */}
                    <div>
                      <h4 className="font-medium mb-3">Seats & Passengers</h4>
                      <div className="space-y-2">
                        {selectedSeats.map((seat, i) => (
                          <div key={seat.id} className="flex items-center justify-between py-2 px-3 rounded-xl bg-muted/50">
                            <div className="flex items-center gap-3">
                              <Badge variant="secondary" className="text-xs">{seat.section}</Badge>
                              <div>
                                <span className="text-sm font-medium">Row {seat.row}, Seat {seat.number}</span>
                                <p className="text-xs text-muted-foreground">{passengers[i]?.name || 'Passenger'}</p>
                              </div>
                            </div>
                            <span className="font-medium">{formatCurrency(seat.price)}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <div className="flex justify-between">
                  <Button variant="outline" onClick={() => setCurrentStep(0)}>
                    <ArrowLeft className="mr-2 h-4 w-4" /> Back
                  </Button>
                  <Button size="lg" onClick={() => setCurrentStep(2)}>
                    Continue to Payment <ChevronRight className="ml-2 h-4 w-4" />
                  </Button>
                </div>
              </motion.div>
            )}

            {/* Step 3: Payment */}
            {currentStep === 2 && (
              <motion.div key="payment" {...fadeInUp} className="space-y-6">
                <Card>
                  <CardHeader>
                    <div className="flex items-center gap-2">
                      <CreditCard className="h-5 w-5 text-indigo-500" />
                      <CardTitle className="text-lg">Payment Details</CardTitle>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div>
                      <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Email for receipt</label>
                      <Input
                        type="email"
                        placeholder="your@email.com"
                        value={paymentData.email}
                        onChange={(e) => setPaymentData({ ...paymentData, email: e.target.value })}
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Name on Card</label>
                      <Input
                        placeholder="John Doe"
                        value={paymentData.cardName}
                        onChange={(e) => setPaymentData({ ...paymentData, cardName: e.target.value })}
                      />
                    </div>
                    <div>
                      <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Card Number</label>
                      <div className="relative">
                        <Input
                          placeholder="4242 4242 4242 4242"
                          value={paymentData.cardNumber}
                          onChange={(e) => setPaymentData({ ...paymentData, cardNumber: e.target.value })}
                          className="pr-20"
                        />
                        <div className="absolute right-3 top-1/2 -translate-y-1/2 flex gap-1">
                          <div className="h-5 w-8 rounded bg-blue-600 flex items-center justify-center text-white text-2xs font-bold">VISA</div>
                          <div className="h-5 w-8 rounded bg-red-500 flex items-center justify-center text-white text-2xs font-bold">MC</div>
                        </div>
                      </div>
                    </div>
                    <div className="grid grid-cols-2 gap-4">
                      <div>
                        <label className="text-xs font-medium text-muted-foreground mb-1.5 block">Expiry</label>
                        <Input
                          placeholder="MM/YY"
                          value={paymentData.expiry}
                          onChange={(e) => setPaymentData({ ...paymentData, expiry: e.target.value })}
                        />
                      </div>
                      <div>
                        <label className="text-xs font-medium text-muted-foreground mb-1.5 block">CVC</label>
                        <Input
                          placeholder="123"
                          type="password"
                          value={paymentData.cvc}
                          onChange={(e) => setPaymentData({ ...paymentData, cvc: e.target.value })}
                        />
                      </div>
                    </div>

                    {/* Security badges */}
                    <div className="flex items-center gap-4 pt-2 text-xs text-muted-foreground">
                      <div className="flex items-center gap-1.5">
                        <Lock className="h-3.5 w-3.5 text-emerald-500" />
                        <span>SSL Encrypted</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <Shield className="h-3.5 w-3.5 text-emerald-500" />
                        <span>PCI Compliant</span>
                      </div>
                      <div className="flex items-center gap-1.5">
                        <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                        <span>Money-back Guarantee</span>
                      </div>
                    </div>
                  </CardContent>
                </Card>

                <div className="flex justify-between">
                  <Button variant="outline" onClick={() => setCurrentStep(1)}>
                    <ArrowLeft className="mr-2 h-4 w-4" /> Back
                  </Button>
                  <Button
                    size="lg"
                    onClick={handlePayment}
                    disabled={isProcessing}
                    className="min-w-[200px]"
                  >
                    {isProcessing ? (
                      <span className="flex items-center gap-2">
                        <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
                          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
                          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                        </svg>
                        Processing...
                      </span>
                    ) : (
                      <>
                        Pay {formatCurrency(finalTotal)}
                        <Lock className="ml-2 h-4 w-4" />
                      </>
                    )}
                  </Button>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </div>

        {/* Order Summary Sidebar */}
        <motion.div {...fadeInUp} transition={{ delay: 0.2 }}>
          <div className="sticky top-20 space-y-4">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Order Summary</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex gap-3">
                  <div className="w-16 h-16 rounded-xl bg-gradient-to-r from-indigo-600 to-purple-600 flex items-center justify-center text-white font-bold text-xs">
                    {['CONCERT','SPORTS','THEATRE','FESTIVAL','SHOW'].includes(scheduleData.transportMode) ? '🎭' : `${scheduleData.originCity?.charAt(0)}${scheduleData.destinationCity?.charAt(0)}`}
                  </div>
                  <div>
                    <p className="font-medium text-sm line-clamp-2">{['CONCERT','SPORTS','THEATRE','FESTIVAL','SHOW'].includes(scheduleData.transportMode) ? scheduleData.originCity : `${scheduleData.originCity} \u2192 ${scheduleData.destinationCity}`}</p>
                    <p className="text-xs text-muted-foreground">{scheduleData.departureTime ? new Date(scheduleData.departureTime).toLocaleDateString() : ''}</p>
                  </div>
                </div>

                <Separator />

                {/* Price breakdown */}
                <div className="space-y-2 text-sm">
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Tickets ({selectedSeats.length})</span>
                    <span>{formatCurrency(totalPrice)}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-muted-foreground">Service fee (5%)</span>
                    <span>{formatCurrency(serviceFee)}</span>
                  </div>
                  {promoApplied && (
                    <div className="flex justify-between text-emerald-600 dark:text-emerald-400">
                      <span className="flex items-center gap-1">
                        <Tag className="h-3.5 w-3.5" /> Promo discount
                      </span>
                      <span>-{formatCurrency(discount)}</span>
                    </div>
                  )}
                </div>

                <Separator />

                <div className="flex justify-between font-bold text-lg">
                  <span>Total</span>
                  <span className="text-gradient">{formatCurrency(finalTotal)}</span>
                </div>

                {/* Promo code */}
                {!promoApplied ? (
                  <div className="flex gap-2">
                    <Input
                      placeholder="Promo code"
                      value={promoCode}
                      onChange={(e) => setPromoCode(e.target.value)}
                      className="text-sm"
                    />
                    <Button variant="outline" size="sm" onClick={handleApplyPromo}>Apply</Button>
                  </div>
                ) : (
                  <div className="flex items-center gap-2 text-xs text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 rounded-xl px-3 py-2">
                    <CheckCircle2 className="h-4 w-4" />
                    <span>WAVE10 applied — 10% off!</span>
                    <button
                      onClick={() => { setPromoApplied(false); setPromoDiscount(0); setPromoCode(''); }}
                      className="ml-auto"
                    >
                      <Trash2 className="h-3.5 w-3.5 text-muted-foreground hover:text-foreground" />
                    </button>
                  </div>
                )}

                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  <Shield className="h-4 w-4 text-emerald-500" />
                  100% Secure • Money-back guarantee
                </div>
              </CardContent>
            </Card>

            {/* Trust info */}
            <div className="text-center text-2xs text-muted-foreground space-y-1">
              <p>Secured by 256-bit SSL encryption</p>
              <p>Try promo code: WAVE10</p>
            </div>
          </div>
        </motion.div>
      </div>
    </div>
  );
}
