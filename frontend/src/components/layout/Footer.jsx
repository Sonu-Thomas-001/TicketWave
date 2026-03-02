import { Link } from 'react-router-dom';
import { Ticket, Github, Twitter } from 'lucide-react';
import { Separator } from '@/components/ui';

export default function Footer() {
  return (
    <footer className="border-t bg-card/50 backdrop-blur-sm">
      <div className="max-w-7xl mx-auto px-4 md:px-6 py-12">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-8">
          {/* Brand */}
          <div className="col-span-2 md:col-span-1">
            <div className="flex items-center gap-2 mb-4">
              <div className="flex items-center justify-center h-9 w-9 rounded-xl gradient-primary">
                <Ticket className="h-5 w-5 text-white" />
              </div>
              <span className="text-xl font-bold text-gradient">TicketWave</span>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Premium event ticket booking. Discover, book, and experience the best events across the globe.
            </p>
          </div>

          {/* Links */}
          <div>
            <h4 className="font-semibold mb-3 text-sm">Explore</h4>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li><Link to="/events" className="hover:text-foreground transition-colors">All Events</Link></li>
              <li><Link to="/events?cat=concert" className="hover:text-foreground transition-colors">Concerts</Link></li>
              <li><Link to="/events?cat=sports" className="hover:text-foreground transition-colors">Sports</Link></li>
              <li><Link to="/events?cat=theatre" className="hover:text-foreground transition-colors">Theatre</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="font-semibold mb-3 text-sm">Company</h4>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li><Link to="/about" className="hover:text-foreground transition-colors">About Us</Link></li>
              <li><Link to="/careers" className="hover:text-foreground transition-colors">Careers</Link></li>
              <li><Link to="/press" className="hover:text-foreground transition-colors">Press</Link></li>
              <li><Link to="/contact" className="hover:text-foreground transition-colors">Contact</Link></li>
            </ul>
          </div>
          <div>
            <h4 className="font-semibold mb-3 text-sm">Legal</h4>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li><Link to="/terms" className="hover:text-foreground transition-colors">Terms of Service</Link></li>
              <li><Link to="/privacy" className="hover:text-foreground transition-colors">Privacy Policy</Link></li>
              <li><Link to="/refund-policy" className="hover:text-foreground transition-colors">Refund Policy</Link></li>
            </ul>
          </div>
        </div>

        <Separator className="my-8" />

        <div className="flex flex-col md:flex-row items-center justify-between gap-4 text-sm text-muted-foreground">
          <p>&copy; {new Date().getFullYear()} TicketWave. All rights reserved.</p>
          <div className="flex items-center gap-4">
            <a href="#" className="hover:text-foreground transition-colors" aria-label="Twitter">
              <Twitter className="h-5 w-5" />
            </a>
            <a href="#" className="hover:text-foreground transition-colors" aria-label="GitHub">
              <Github className="h-5 w-5" />
            </a>
          </div>
        </div>
      </div>
    </footer>
  );
}
