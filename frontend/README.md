# TicketWave Frontend

Modern, production-grade UI for the TicketWave ticket booking system.

## Tech Stack

- **React 18** (functional components + hooks)
- **Vite 5** — blazing-fast build tool
- **Tailwind CSS 3.4** — utility-first styling
- **shadcn/ui** — Radix UI primitives with custom styling
- **Framer Motion** — smooth animations and transitions
- **React Router 6** — client-side routing
- **Lucide React** — beautiful icon library

## Design Features

- Dark & Light mode with system preference detection
- Glassmorphism + subtle gradients
- Responsive mobile-first layout
- WCAG-friendly accessible components
- Smooth page transitions via Framer Motion
- Premium SaaS aesthetic

## Getting Started

### Prerequisites

- Node.js 18+ and npm

### Installation

```bash
cd frontend
npm install
```

### Development

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Production Build

```bash
npm run build
npm run preview
```

## Project Structure

```
src/
├── components/
│   ├── features/          # Feature-specific components (SeatMap, etc.)
│   ├── layout/            # Layout components (Navbar, Sidebar, Footer)
│   ├── shared/            # Shared/reusable components (EventCard, etc.)
│   └── ui/                # Base UI primitives (Button, Card, Input, etc.)
├── context/               # React contexts (Theme, Auth)
├── lib/                   # Utilities, API client, mock data, animations
├── pages/                 # Page-level components
├── App.jsx                # Root app component
├── router.jsx             # Route definitions
├── main.jsx               # Entry point
└── index.css              # Global styles + Tailwind + CSS variables
```

## Pages

| Route             | Page              | Description                    |
| ----------------- | ----------------- | ------------------------------ |
| `/`               | HomePage          | Hero, search, featured events  |
| `/events`         | EventsPage        | Browse/filter/search events    |
| `/events/:id`     | EventDetailPage   | Event detail + seat map        |
| `/checkout`       | CheckoutPage      | Multi-step checkout flow       |
| `/login`          | LoginPage         | Sign in                        |
| `/register`       | RegisterPage      | Create account                 |
| `/bookings`       | BookingsPage      | User's booking history         |
| `/profile`        | ProfilePage       | Account settings               |
| `/admin`          | AdminDashboard    | Admin analytics dashboard      |

## API Integration

The app is pre-configured to proxy `/api` requests to `http://localhost:8080` (Spring Boot backend). Replace mock data implementations in `src/lib/mockData.js` with real API calls via `src/lib/api.js`.
