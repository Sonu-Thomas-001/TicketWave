import { lazy, Suspense } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { MainLayout, AuthLayout } from '@/components/layout';
import { LoadingState } from '@/components/ui';

/* ── Lazy-loaded page chunks (each becomes its own JS file) ── */
const HomePage = lazy(() => import('@/pages/HomePage'));
const EventsPage = lazy(() => import('@/pages/EventsPage'));
const EventDetailPage = lazy(() => import('@/pages/EventDetailPage'));
const SearchResultsPage = lazy(() => import('@/pages/SearchResultsPage'));
const CheckoutPage = lazy(() => import('@/pages/CheckoutPage'));
const BookingConfirmationPage = lazy(() => import('@/pages/BookingConfirmationPage'));
const BookingsPage = lazy(() => import('@/pages/BookingsPage'));
const ProfilePage = lazy(() => import('@/pages/ProfilePage'));
const OperatorPortalPage = lazy(() => import('@/pages/OperatorPortalPage'));
const AdminDashboard = lazy(() => import('@/pages/AdminDashboard'));
const LoginPage = lazy(() => import('@/pages/LoginPage'));
const RegisterPage = lazy(() => import('@/pages/RegisterPage'));
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'));

/** Auth layout has its own Suspense boundary */
function AuthSuspense({ children }) {
  return (
    <Suspense fallback={<LoadingState message="Loading..." className="min-h-[60vh]" />}>
      {children}
    </Suspense>
  );
}

const router = createBrowserRouter([
  {
    element: <MainLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      { path: '/events', element: <EventsPage /> },
      { path: '/events/:id', element: <EventDetailPage /> },
      { path: '/search', element: <SearchResultsPage /> },
      { path: '/checkout', element: <CheckoutPage /> },
      { path: '/booking-confirmation', element: <BookingConfirmationPage /> },
      { path: '/bookings', element: <BookingsPage /> },
      { path: '/profile', element: <ProfilePage /> },
      { path: '/settings', element: <ProfilePage /> },
      { path: '/operator', element: <OperatorPortalPage /> },
      { path: '/operator/*', element: <OperatorPortalPage /> },
      { path: '/admin', element: <AdminDashboard /> },
      { path: '/admin/*', element: <AdminDashboard /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    element: <AuthLayout />,
    children: [
      { path: '/login', element: <AuthSuspense><LoginPage /></AuthSuspense> },
      { path: '/register', element: <AuthSuspense><RegisterPage /></AuthSuspense> },
    ],
  },
]);

export default function AppRouter() {
  return <RouterProvider router={router} />;
}
