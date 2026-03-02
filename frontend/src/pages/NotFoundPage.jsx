import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Home, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui';
import { fadeInUp } from '@/lib/animations';

export default function NotFoundPage() {
  return (
    <div className="flex items-center justify-center min-h-[60vh] px-4">
      <motion.div {...fadeInUp} className="text-center space-y-6">
        <div className="text-8xl font-extrabold text-gradient">404</div>
        <h1 className="text-3xl font-bold">Page Not Found</h1>
        <p className="text-muted-foreground max-w-md mx-auto">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <div className="flex items-center justify-center gap-4">
          <Link to="/">
            <Button className="gap-2">
              <Home className="h-4 w-4" /> Go Home
            </Button>
          </Link>
          <Button variant="outline" onClick={() => window.history.back()} className="gap-2">
            <ArrowLeft className="h-4 w-4" /> Go Back
          </Button>
        </div>
      </motion.div>
    </div>
  );
}
