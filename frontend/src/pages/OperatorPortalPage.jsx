import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  LayoutDashboard, Bus, Calendar, DollarSign, Tag, BarChart3,
  ChevronRight, Plus, Pencil, Trash2, Search, Filter,
  Users, TrendingUp, Ticket, Clock, MapPin, Star,
  Eye, MoreVertical, ArrowUpRight, ArrowDownRight,
  Settings, Bell, ChevronDown, X,
} from 'lucide-react';
import {
  Button, Input, Card, CardContent, CardHeader, CardTitle,
  Badge, Separator, Tabs, TabsList, TabsTrigger,
  Pagination,
} from '@/components/ui';
import { formatCurrency, cn } from '@/lib/utils';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/animations';

/* ============ Mock Data ============ */
const tabs = [
  { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { id: 'schedules', label: 'Schedules', icon: Calendar },
  { id: 'inventory', label: 'Seat Inventory', icon: Bus },
  { id: 'fares', label: 'Fare Rules', icon: DollarSign },
  { id: 'promos', label: 'Promo Codes', icon: Tag },
  { id: 'reports', label: 'Reports', icon: BarChart3 },
];

const mockSchedules = [
  { id: 'SCH-001', route: 'New York → Boston', departure: '06:00 AM', arrival: '10:30 AM', bus: 'Volvo Multi-Axle', seats: 42, booked: 38, status: 'Active' },
  { id: 'SCH-002', route: 'New York → Washington DC', departure: '08:00 AM', arrival: '12:30 PM', bus: 'Mercedes Sleeper', seats: 36, booked: 28, status: 'Active' },
  { id: 'SCH-003', route: 'Boston → Philadelphia', departure: '11:00 AM', arrival: '04:00 PM', bus: 'Scania AC', seats: 40, booked: 40, status: 'Full' },
  { id: 'SCH-004', route: 'Chicago → Detroit', departure: '02:00 PM', arrival: '06:30 PM', bus: 'Volvo Multi-Axle', seats: 42, booked: 15, status: 'Active' },
  { id: 'SCH-005', route: 'Los Angeles → San Francisco', departure: '07:00 AM', arrival: '01:00 PM', bus: 'Mercedes Sleeper', seats: 36, booked: 33, status: 'Active' },
];

const mockPromos = [
  { id: 'P-001', code: 'WAVE10', discount: '10%', type: 'Percentage', usageLimit: 500, used: 342, validUntil: '2025-03-31', status: 'Active' },
  { id: 'P-002', code: 'FIRST50', discount: '$50', type: 'Flat', usageLimit: 100, used: 100, validUntil: '2025-02-28', status: 'Exhausted' },
  { id: 'P-003', code: 'SUMMER25', discount: '25%', type: 'Percentage', usageLimit: 1000, used: 0, validUntil: '2025-07-31', status: 'Scheduled' },
];

const weeklyRevenue = [
  { day: 'Mon', value: 4200 },
  { day: 'Tue', value: 3800 },
  { day: 'Wed', value: 5100 },
  { day: 'Thu', value: 4700 },
  { day: 'Fri', value: 6200 },
  { day: 'Sat', value: 7800 },
  { day: 'Sun', value: 5500 },
];

/* ============ Sub-components ============ */

function StatCard({ title, value, change, changeType, icon: Icon, color }) {
  return (
    <Card className="p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs text-muted-foreground font-medium">{title}</p>
          <p className="text-2xl font-bold mt-1">{value}</p>
          {change && (
            <div className={cn(
              'flex items-center gap-1 text-xs mt-1',
              changeType === 'up' ? 'text-emerald-500' : 'text-destructive'
            )}>
              {changeType === 'up' ? <ArrowUpRight className="h-3 w-3" /> : <ArrowDownRight className="h-3 w-3" />}
              {change}
            </div>
          )}
        </div>
        <div className={cn('p-2.5 rounded-xl', color)}>
          <Icon className="h-5 w-5" />
        </div>
      </div>
    </Card>
  );
}

function MiniBarChart({ data }) {
  const max = Math.max(...data.map((d) => d.value));
  return (
    <div className="flex items-end gap-1.5 h-32">
      {data.map((d) => (
        <div key={d.day} className="flex-1 flex flex-col items-center gap-1">
          <motion.div
            initial={{ height: 0 }}
            animate={{ height: `${(d.value / max) * 100}%` }}
            transition={{ duration: 0.5, delay: 0.1 }}
            className="w-full rounded-t-md gradient-primary min-h-[4px]"
          />
          <span className="text-2xs text-muted-foreground">{d.day}</span>
        </div>
      ))}
    </div>
  );
}

/* ============ Dashboard Tab ============ */
function DashboardTab() {
  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Today's Revenue"
          value="$12,580"
          change="+12% vs yesterday"
          changeType="up"
          icon={DollarSign}
          color="bg-emerald-500/10 text-emerald-500"
        />
        <StatCard
          title="Tickets Sold"
          value="284"
          change="+8% vs yesterday"
          changeType="up"
          icon={Ticket}
          color="bg-indigo-500/10 text-indigo-500"
        />
        <StatCard
          title="Active Routes"
          value="12"
          icon={MapPin}
          color="bg-amber-500/10 text-amber-500"
        />
        <StatCard
          title="Avg Occupancy"
          value="78%"
          change="-2% vs last week"
          changeType="down"
          icon={Users}
          color="bg-pink-500/10 text-pink-500"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Weekly Revenue</CardTitle>
          </CardHeader>
          <CardContent>
            <MiniBarChart data={weeklyRevenue} />
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-base">Top Routes</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {mockSchedules.slice(0, 4).map((s, i) => (
              <div key={s.id} className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="text-xs font-bold text-muted-foreground w-5">#{i + 1}</span>
                  <div>
                    <p className="text-sm font-medium">{s.route}</p>
                    <p className="text-xs text-muted-foreground">{s.booked}/{s.seats} seats sold</p>
                  </div>
                </div>
                <div className="w-24 bg-muted rounded-full h-2">
                  <div
                    className="h-2 rounded-full gradient-primary"
                    style={{ width: `${(s.booked / s.seats) * 100}%` }}
                  />
                </div>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </motion.div>
  );
}

/* ============ Schedules Tab ============ */
function SchedulesTab() {
  const [search, setSearch] = useState('');
  const filtered = mockSchedules.filter((s) =>
    s.route.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search routes..." value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
        </div>
        <Button className="gap-2">
          <Plus className="h-4 w-4" /> Add Schedule
        </Button>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Route</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Departure</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Arrival</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Bus Type</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Occupancy</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Status</th>
                <th className="text-right p-4 text-xs font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((s) => (
                <tr key={s.id} className="border-b last:border-0 hover:bg-muted/30 transition-colors">
                  <td className="p-4 font-medium">{s.route}</td>
                  <td className="p-4 text-muted-foreground">{s.departure}</td>
                  <td className="p-4 text-muted-foreground">{s.arrival}</td>
                  <td className="p-4 text-muted-foreground">{s.bus}</td>
                  <td className="p-4">
                    <div className="flex items-center gap-2">
                      <div className="w-16 bg-muted rounded-full h-1.5">
                        <div
                          className={cn('h-1.5 rounded-full', s.booked === s.seats ? 'bg-destructive' : 'gradient-primary')}
                          style={{ width: `${(s.booked / s.seats) * 100}%` }}
                        />
                      </div>
                      <span className="text-xs text-muted-foreground">{s.booked}/{s.seats}</span>
                    </div>
                  </td>
                  <td className="p-4">
                    <Badge variant={s.status === 'Full' ? 'destructive' : 'success'} className="text-xs">
                      {s.status}
                    </Badge>
                  </td>
                  <td className="p-4 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="p-1.5 rounded-lg hover:bg-muted"><Pencil className="h-3.5 w-3.5 text-muted-foreground" /></button>
                      <button className="p-1.5 rounded-lg hover:bg-muted"><Trash2 className="h-3.5 w-3.5 text-destructive/60" /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </motion.div>
  );
}

/* ============ Inventory Tab ============ */
function InventoryTab() {
  const seatLayout = useMemo(() => {
    const rows = 10;
    const cols = 4;
    return Array.from({ length: rows }, (_, r) =>
      Array.from({ length: cols }, (_, c) => ({
        id: `${r + 1}-${c + 1}`,
        row: r + 1,
        col: c + 1,
        status: Math.random() > 0.7 ? 'booked' : Math.random() > 0.9 ? 'blocked' : 'available',
      }))
    );
  }, []);

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold">New York → Boston — 06:00 AM</h3>
          <p className="text-sm text-muted-foreground">Volvo Multi-Axle • 42 seats</p>
        </div>
        <Button variant="outline" size="sm">Change Schedule</Button>
      </div>

      {/* Legend */}
      <div className="flex items-center gap-6 text-sm">
        <div className="flex items-center gap-2"><div className="h-4 w-4 rounded bg-emerald-500/20 border border-emerald-500" /> Available</div>
        <div className="flex items-center gap-2"><div className="h-4 w-4 rounded bg-indigo-500" /> Booked</div>
        <div className="flex items-center gap-2"><div className="h-4 w-4 rounded bg-muted-foreground/20" /> Blocked</div>
      </div>

      <Card>
        <CardContent className="p-6">
          <div className="max-w-xs mx-auto space-y-2">
            {seatLayout.map((row, ri) => (
              <div key={ri} className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground w-6 text-right">{ri + 1}</span>
                <div className="flex gap-2">
                  {row.slice(0, 2).map((seat) => (
                    <button
                      key={seat.id}
                      className={cn(
                        'h-8 w-8 rounded-md text-2xs font-medium transition-colors',
                        seat.status === 'available' && 'bg-emerald-500/15 border border-emerald-500/40 hover:bg-emerald-500/30 text-emerald-600',
                        seat.status === 'booked' && 'bg-indigo-500 text-white cursor-not-allowed',
                        seat.status === 'blocked' && 'bg-muted-foreground/10 text-muted-foreground/40 cursor-not-allowed',
                      )}
                    >
                      {seat.col}
                    </button>
                  ))}
                </div>
                <div className="w-6" /> {/* Aisle */}
                <div className="flex gap-2">
                  {row.slice(2, 4).map((seat) => (
                    <button
                      key={seat.id}
                      className={cn(
                        'h-8 w-8 rounded-md text-2xs font-medium transition-colors',
                        seat.status === 'available' && 'bg-emerald-500/15 border border-emerald-500/40 hover:bg-emerald-500/30 text-emerald-600',
                        seat.status === 'booked' && 'bg-indigo-500 text-white cursor-not-allowed',
                        seat.status === 'blocked' && 'bg-muted-foreground/10 text-muted-foreground/40 cursor-not-allowed',
                      )}
                    >
                      {seat.col}
                    </button>
                  ))}
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

/* ============ Fares Tab ============ */
function FaresTab() {
  const rules = [
    { route: 'New York → Boston', basePrice: 45, peakMultiplier: 1.5, category: 'Regular', advance7Day: -10, advance30Day: -20 },
    { route: 'New York → Washington DC', basePrice: 55, peakMultiplier: 1.4, category: 'Regular', advance7Day: -12, advance30Day: -25 },
    { route: 'Boston → Philadelphia', basePrice: 60, peakMultiplier: 1.6, category: 'Premium', advance7Day: -15, advance30Day: -30 },
  ];

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Fare Rules</h3>
        <Button className="gap-2"><Plus className="h-4 w-4" /> Add Rule</Button>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Route</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Base Price</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Peak ×</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Category</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">7-Day Advance</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">30-Day Advance</th>
                <th className="text-right p-4 text-xs font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((r, i) => (
                <tr key={i} className="border-b last:border-0 hover:bg-muted/30 transition-colors">
                  <td className="p-4 font-medium">{r.route}</td>
                  <td className="p-4">{formatCurrency(r.basePrice)}</td>
                  <td className="p-4 text-amber-600">{r.peakMultiplier}×</td>
                  <td className="p-4"><Badge variant="secondary" className="text-xs">{r.category}</Badge></td>
                  <td className="p-4 text-emerald-500">{r.advance7Day}%</td>
                  <td className="p-4 text-emerald-500">{r.advance30Day}%</td>
                  <td className="p-4 text-right">
                    <button className="p-1.5 rounded-lg hover:bg-muted"><Pencil className="h-3.5 w-3.5 text-muted-foreground" /></button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </motion.div>
  );
}

/* ============ Promos Tab ============ */
function PromosTab() {
  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Promo Codes</h3>
        <Button className="gap-2"><Plus className="h-4 w-4" /> Create Promo</Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {mockPromos.map((p) => (
          <Card key={p.id} className="hover:shadow-md transition-shadow">
            <CardContent className="p-5 space-y-3">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <Tag className="h-4 w-4 text-primary" />
                  <span className="font-mono font-bold text-lg">{p.code}</span>
                </div>
                <Badge
                  variant={p.status === 'Active' ? 'success' : p.status === 'Exhausted' ? 'destructive' : 'secondary'}
                  className="text-xs"
                >
                  {p.status}
                </Badge>
              </div>
              <div className="text-3xl font-bold text-gradient">{p.discount}</div>
              <div className="space-y-1.5 text-sm text-muted-foreground">
                <div className="flex justify-between">
                  <span>Type</span>
                  <span>{p.type}</span>
                </div>
                <div className="flex justify-between">
                  <span>Usage</span>
                  <span>{p.used} / {p.usageLimit}</span>
                </div>
                <div className="flex justify-between">
                  <span>Valid until</span>
                  <span>{p.validUntil}</span>
                </div>
              </div>
              <div className="w-full bg-muted rounded-full h-1.5">
                <div
                  className="h-1.5 rounded-full gradient-primary"
                  style={{ width: `${(p.used / p.usageLimit) * 100}%` }}
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </motion.div>
  );
}

/* ============ Reports Tab ============ */
function ReportsTab() {
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'];
  const revenueData = [32000, 41000, 38000, 52000, 47000, 61000];
  const max = Math.max(...revenueData);

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard title="Total Revenue (YTD)" value="$271,000" change="+18% vs last year" changeType="up" icon={DollarSign} color="bg-emerald-500/10 text-emerald-500" />
        <StatCard title="Total Bookings" value="6,842" change="+23% vs last year" changeType="up" icon={Ticket} color="bg-indigo-500/10 text-indigo-500" />
        <StatCard title="Avg Rating" value="4.6 ★" icon={Star} color="bg-amber-500/10 text-amber-500" />
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Monthly Revenue</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-end gap-3 h-40">
            {months.map((m, i) => (
              <div key={m} className="flex-1 flex flex-col items-center gap-1">
                <span className="text-xs font-medium">{formatCurrency(revenueData[i] / 1000)}k</span>
                <motion.div
                  initial={{ height: 0 }}
                  animate={{ height: `${(revenueData[i] / max) * 100}%` }}
                  transition={{ duration: 0.5, delay: i * 0.05 }}
                  className="w-full rounded-t-lg gradient-primary min-h-[4px]"
                />
                <span className="text-2xs text-muted-foreground">{m}</span>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </motion.div>
  );
}

/* ============ Main Operator Portal ============ */
export default function OperatorPortalPage() {
  const [activeTab, setActiveTab] = useState('dashboard');

  const renderTab = () => {
    switch (activeTab) {
      case 'dashboard': return <DashboardTab />;
      case 'schedules': return <SchedulesTab />;
      case 'inventory': return <InventoryTab />;
      case 'fares':     return <FaresTab />;
      case 'promos':    return <PromosTab />;
      case 'reports':   return <ReportsTab />;
      default:          return <DashboardTab />;
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 md:px-6 py-8">
      {/* Header */}
      <motion.div {...fadeInUp} className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold flex items-center gap-2">
            <Bus className="h-6 w-6 text-primary" /> Operator Portal
          </h1>
          <p className="text-sm text-muted-foreground mt-1">Manage schedules, inventory, fares, and promotions</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" className="relative">
            <Bell className="h-4 w-4" />
            <span className="absolute -top-1 -right-1 h-4 w-4 rounded-full bg-destructive text-white text-2xs flex items-center justify-center">3</span>
          </Button>
          <Button variant="outline" size="icon"><Settings className="h-4 w-4" /></Button>
        </div>
      </motion.div>

      {/* Tab Navigation */}
      <motion.div {...fadeInUp} transition={{ delay: 0.1 }} className="mb-8">
        <div className="flex gap-1 overflow-x-auto pb-2 no-scrollbar">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={cn(
                'flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium whitespace-nowrap transition-all',
                activeTab === tab.id
                  ? 'gradient-primary text-white shadow-md shadow-indigo-500/20'
                  : 'text-muted-foreground hover:text-foreground hover:bg-muted'
              )}
            >
              <tab.icon className="h-4 w-4" />
              {tab.label}
            </button>
          ))}
        </div>
      </motion.div>

      {/* Tab Content */}
      <AnimatePresence mode="wait">
        <div key={activeTab}>
          {renderTab()}
        </div>
      </AnimatePresence>
    </div>
  );
}
