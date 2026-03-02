import { Outlet } from 'react-router-dom';
import { motion } from 'framer-motion';
import { Ticket } from 'lucide-react';

export default function AuthLayout() {
  return (
    <div className="min-h-screen flex">
      {/* Left side — branding panel */}
      <div className="hidden lg:flex lg:w-1/2 xl:w-[45%] relative overflow-hidden">
        <div className="absolute inset-0 gradient-primary" />
        <div className="absolute inset-0 bg-[url('data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjAiIGhlaWdodD0iNjAiIHZpZXdCb3g9IjAgMCA2MCA2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48ZyBmaWxsPSJub25lIiBmaWxsLXJ1bGU9ImV2ZW5vZGQiPjxnIGZpbGw9IiNmZmYiIGZpbGwtb3BhY2l0eT0iMC4wNSI+PHBhdGggZD0iTTM2IDM0djZoLTZ2LTZIMDZ6bTAtMTBoNnY2aC02di02em0xMiAwaDZ2NmgtNnYtNnoiLz48L2c+PC9nPjwvc3ZnPg==')] opacity-30" />
        <div className="relative flex flex-col items-center justify-center w-full p-12 text-white">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
            className="text-center"
          >
            <div className="flex items-center justify-center h-16 w-16 rounded-2xl bg-white/20 backdrop-blur-sm mx-auto mb-6 shadow-xl">
              <Ticket className="h-8 w-8" />
            </div>
            <h1 className="text-4xl font-bold mb-4">TicketWave</h1>
            <p className="text-lg text-white/80 max-w-md leading-relaxed">
              Your gateway to unforgettable experiences. Book premium event tickets with confidence.
            </p>
          </motion.div>

          {/* Floating decorative elements */}
          <div className="absolute top-20 left-10 h-32 w-32 rounded-full bg-white/5 blur-xl" />
          <div className="absolute bottom-20 right-10 h-48 w-48 rounded-full bg-white/5 blur-2xl" />
        </div>
      </div>

      {/* Right side — auth form */}
      <div className="flex-1 flex items-center justify-center p-6 bg-background">
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.25, ease: 'easeOut' }}
          className="w-full max-w-md"
        >
          <Outlet />
        </motion.div>
      </div>
    </div>
  );
}
