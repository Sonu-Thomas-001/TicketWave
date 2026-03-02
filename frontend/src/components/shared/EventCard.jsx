import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Calendar, MapPin, Clock, Tag } from 'lucide-react';
import { Card, Badge } from '@/components/ui';
import { formatCurrency, formatDate, formatTime, cn } from '@/lib/utils';
import { staggerItem } from '@/lib/animations';

export default function EventCard({ event, index = 0 }) {
  const availabilityPct = (event.availableSeats / event.totalSeats) * 100;
  const isLow = availabilityPct < 20;

  return (
    <motion.div variants={staggerItem}>
      <Link to={`/events/${event.id}`}>
        <Card className="group overflow-hidden hover:shadow-xl hover:shadow-indigo-500/10 hover:-translate-y-1 transition-all duration-300 cursor-pointer">
          {/* Image */}
          <div className="relative h-48 overflow-hidden">
            <img
              src={event.imageUrl}
              alt={event.title}
              className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500"
              loading="lazy"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-transparent" />

            {/* Category badge */}
            <div className="absolute top-3 left-3">
              <Badge variant="secondary" className="bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm text-xs">
                {event.category}
              </Badge>
            </div>

            {/* Featured badge */}
            {event.featured && (
              <div className="absolute top-3 right-3">
                <Badge className="text-xs">Featured</Badge>
              </div>
            )}

            {/* Price */}
            <div className="absolute bottom-3 left-3 text-white">
              <p className="text-xs opacity-80">Starting from</p>
              <p className="text-lg font-bold">{formatCurrency(event.price.min)}</p>
            </div>

            {/* Availability */}
            {isLow && (
              <div className="absolute bottom-3 right-3">
                <Badge variant="warning" className="text-xs">Few left</Badge>
              </div>
            )}
          </div>

          {/* Content */}
          <div className="p-4 space-y-3">
            <h3 className="font-semibold text-base leading-tight line-clamp-2 group-hover:text-primary transition-colors">
              {event.title}
            </h3>

            <div className="space-y-1.5 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <Calendar className="h-3.5 w-3.5 flex-shrink-0" />
                <span>{formatDate(event.date)}</span>
              </div>
              <div className="flex items-center gap-2">
                <Clock className="h-3.5 w-3.5 flex-shrink-0" />
                <span>{formatTime(event.date)}</span>
              </div>
              <div className="flex items-center gap-2">
                <MapPin className="h-3.5 w-3.5 flex-shrink-0" />
                <span className="truncate">{event.venue}, {event.city}</span>
              </div>
            </div>

            {/* Tags */}
            <div className="flex items-center gap-1.5 flex-wrap">
              {event.tags.slice(0, 3).map((tag) => (
                <Badge key={tag} variant="secondary" className="text-[10px] px-2 py-0">
                  {tag}
                </Badge>
              ))}
            </div>

            {/* Availability bar */}
            <div className="pt-1">
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                <span>{event.availableSeats.toLocaleString()} seats left</span>
                <span>{Math.round(availabilityPct)}% available</span>
              </div>
              <div className="h-1.5 bg-secondary rounded-full overflow-hidden">
                <div
                  className={cn(
                    'h-full rounded-full transition-all duration-500',
                    isLow ? 'bg-amber-500' : 'gradient-primary'
                  )}
                  style={{ width: `${availabilityPct}%` }}
                />
              </div>
            </div>
          </div>
        </Card>
      </Link>
    </motion.div>
  );
}
