// Mock data for demo/development purposes

export const mockEvents = [
  {
    id: '1',
    title: 'Taylor Swift | The Eras Tour',
    artist: 'Taylor Swift',
    category: 'Concert',
    venue: 'SoFi Stadium',
    city: 'Los Angeles',
    date: '2026-04-15T19:30:00',
    imageUrl: '/images/event-1.svg',
    price: { min: 89, max: 450 },
    totalSeats: 72000,
    availableSeats: 12400,
    tags: ['Music', 'Pop', 'Stadium'],
    featured: true,
  },
  {
    id: '2',
    title: 'Hamilton — Broadway',
    artist: 'Lin-Manuel Miranda',
    category: 'Theatre',
    venue: 'Richard Rodgers Theatre',
    city: 'New York',
    date: '2026-05-20T20:00:00',
    imageUrl: '/images/event-2.svg',
    price: { min: 120, max: 800 },
    totalSeats: 1319,
    availableSeats: 245,
    tags: ['Theatre', 'Musical', 'Broadway'],
    featured: true,
  },
  {
    id: '3',
    title: 'NBA Finals — Game 7',
    artist: null,
    category: 'Sports',
    venue: 'Chase Center',
    city: 'San Francisco',
    date: '2026-06-18T18:00:00',
    imageUrl: '/images/event-3.svg',
    price: { min: 200, max: 2500 },
    totalSeats: 18064,
    availableSeats: 3200,
    tags: ['Sports', 'Basketball', 'NBA'],
    featured: true,
  },
  {
    id: '4',
    title: 'Coldplay — Music of the Spheres',
    artist: 'Coldplay',
    category: 'Concert',
    venue: 'Wembley Stadium',
    city: 'London',
    date: '2026-07-10T20:00:00',
    imageUrl: '/images/event-4.svg',
    price: { min: 75, max: 350 },
    totalSeats: 90000,
    availableSeats: 28500,
    tags: ['Music', 'Rock', 'Stadium'],
    featured: false,
  },
  {
    id: '5',
    title: 'Cirque du Soleil — Luzia',
    artist: 'Cirque du Soleil',
    category: 'Show',
    venue: 'Grand Chapiteau',
    city: 'Las Vegas',
    date: '2026-03-22T19:00:00',
    imageUrl: '/images/event-5.svg',
    price: { min: 60, max: 180 },
    totalSeats: 2500,
    availableSeats: 890,
    tags: ['Show', 'Circus', 'Entertainment'],
    featured: false,
  },
  {
    id: '6',
    title: 'Coachella 2026',
    artist: null,
    category: 'Festival',
    venue: 'Empire Polo Club',
    city: 'Indio',
    date: '2026-04-10T12:00:00',
    imageUrl: '/images/event-6.svg',
    price: { min: 450, max: 1200 },
    totalSeats: 125000,
    availableSeats: 45000,
    tags: ['Festival', 'Music', 'Outdoor'],
    featured: true,
  },
];

export const mockCategories = [
  { id: 'all', label: 'All Events', icon: 'Grid3X3' },
  { id: 'concert', label: 'Concerts', icon: 'Music' },
  { id: 'sports', label: 'Sports', icon: 'Trophy' },
  { id: 'theatre', label: 'Theatre', icon: 'Drama' },
  { id: 'festival', label: 'Festivals', icon: 'PartyPopper' },
  { id: 'show', label: 'Shows', icon: 'Sparkles' },
];

export const mockBookings = [
  {
    id: 'BK-001',
    eventTitle: 'Taylor Swift | The Eras Tour',
    venue: 'SoFi Stadium',
    date: '2026-04-15T19:30:00',
    seats: ['A12', 'A13'],
    total: 380,
    status: 'CONFIRMED',
    createdAt: '2026-02-28T14:30:00',
  },
  {
    id: 'BK-002',
    eventTitle: 'Hamilton — Broadway',
    venue: 'Richard Rodgers Theatre',
    date: '2026-05-20T20:00:00',
    seats: ['E5'],
    total: 240,
    status: 'PENDING',
    createdAt: '2026-03-01T09:15:00',
  },
  {
    id: 'BK-003',
    eventTitle: 'Coachella 2026',
    venue: 'Empire Polo Club',
    date: '2026-04-10T12:00:00',
    seats: ['GA'],
    total: 450,
    status: 'CANCELLED',
    createdAt: '2026-01-15T18:00:00',
  },
];

// Seat map data for interactive seat selection
export function generateSeatMap() {
  const sections = ['VIP', 'Premium', 'Standard', 'Balcony'];
  const rows = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
  const seatsPerRow = 12;
  const seatMap = {};

  sections.forEach((section) => {
    seatMap[section] = [];
    const sectionRows = section === 'VIP' ? rows.slice(0, 3) : section === 'Premium' ? rows.slice(0, 5) : rows;
    sectionRows.forEach((row) => {
      for (let i = 1; i <= seatsPerRow; i++) {
        const statuses = ['available', 'available', 'available', 'available', 'held', 'sold'];
        const status = statuses[Math.floor(Math.random() * statuses.length)];
        const prices = { VIP: 450, Premium: 250, Standard: 120, Balcony: 89 };
        seatMap[section].push({
          id: `${section}-${row}${i}`,
          section,
          row,
          number: i,
          label: `${row}${i}`,
          status,
          price: prices[section],
        });
      }
    });
  });

  return seatMap;
}

export const mockAdminStats = {
  totalRevenue: 1245600,
  totalBookings: 8432,
  activeEvents: 24,
  totalUsers: 15230,
  revenueChange: 12.5,
  bookingChange: 8.3,
  eventChange: -2.1,
  userChange: 15.7,
};
