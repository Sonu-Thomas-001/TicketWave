import { useState, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  DollarSign, Ticket, Calendar, Users, TrendingUp,
  TrendingDown, ArrowUpRight, ArrowDownRight, BarChart3,
  Activity, PieChart, Eye, Shield, Search, Plus,
  Pencil, Trash2, Ban, CheckCircle2, Clock, FileText,
  Settings, AlertTriangle, RefreshCw, XCircle,
  UserCheck, UserX, MoreVertical, Download,
} from 'lucide-react';
import {
  Card, CardContent, CardHeader, CardTitle, CardDescription,
  Badge, Separator, Tabs, TabsList, TabsTrigger, TabsContent,
  Input, Button, Pagination,
} from '@/components/ui';
import { mockAdminStats, mockBookings, mockEvents } from '@/lib/mockData';
import { formatCurrency, formatDate, cn } from '@/lib/utils';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/animations';

/* ============ Stat Cards Config ============ */
const statCards = [
  { title: 'Total Revenue', value: formatCurrency(mockAdminStats.totalRevenue), change: mockAdminStats.revenueChange, icon: DollarSign, color: 'from-indigo-500 to-blue-500' },
  { title: 'Total Bookings', value: mockAdminStats.totalBookings.toLocaleString(), change: mockAdminStats.bookingChange, icon: Ticket, color: 'from-emerald-500 to-green-500' },
  { title: 'Active Events', value: mockAdminStats.activeEvents.toString(), change: mockAdminStats.eventChange, icon: Calendar, color: 'from-amber-500 to-orange-500' },
  { title: 'Total Users', value: mockAdminStats.totalUsers.toLocaleString(), change: mockAdminStats.userChange, icon: Users, color: 'from-rose-500 to-pink-500' },
];

/* ============ Mock Users ============ */
const mockUsers = [
  { id: 'U001', name: 'Alex Johnson', email: 'alex@example.com', role: 'User', status: 'Active', bookings: 12, joined: '2024-06-10' },
  { id: 'U002', name: 'Sarah Williams', email: 'sarah@example.com', role: 'User', status: 'Active', bookings: 8, joined: '2024-07-22' },
  { id: 'U003', name: 'CityLink Travels', email: 'ops@citylink.com', role: 'Operator', status: 'Active', bookings: 0, joined: '2024-05-01' },
  { id: 'U004', name: 'Mike Chen', email: 'mike@example.com', role: 'User', status: 'Suspended', bookings: 3, joined: '2024-08-15' },
  { id: 'U005', name: 'Express Coaches', email: 'admin@express.com', role: 'Operator', status: 'Pending', bookings: 0, joined: '2024-12-01' },
  { id: 'U006', name: 'Luna Tours', email: 'hello@luna.com', role: 'Operator', status: 'Active', bookings: 0, joined: '2024-03-18' },
];

/* ============ Mock Audit Logs ============ */
const mockAuditLogs = [
  { id: 'AL-001', timestamp: '2025-01-15 14:32:18', actor: 'admin@ticketwave.com', action: 'REFUND_APPROVED', target: 'BK-1001', detail: 'Full refund $89.00 processed' },
  { id: 'AL-002', timestamp: '2025-01-15 14:10:05', actor: 'system', action: 'SEAT_HOLD_EXPIRED', target: 'HOLD-4521', detail: 'Auto-released 4 seats for Event #12' },
  { id: 'AL-003', timestamp: '2025-01-15 13:45:22', actor: 'admin@ticketwave.com', action: 'USER_SUSPENDED', target: 'U004', detail: 'Suspicious activity detected' },
  { id: 'AL-004', timestamp: '2025-01-15 12:18:30', actor: 'ops@citylink.com', action: 'SCHEDULE_UPDATED', target: 'SCH-001', detail: 'Departure time changed 06:00→06:30' },
  { id: 'AL-005', timestamp: '2025-01-15 11:55:12', actor: 'system', action: 'PAYMENT_WEBHOOK', target: 'PAY-881', detail: 'Stripe webhook verified, booking confirmed' },
  { id: 'AL-006', timestamp: '2025-01-15 10:30:00', actor: 'admin@ticketwave.com', action: 'PROMO_CREATED', target: 'WAVE10', detail: '10% discount promo code created' },
  { id: 'AL-007', timestamp: '2025-01-14 22:05:44', actor: 'system', action: 'IDEMPOTENCY_REPLAY', target: 'IK-8823', detail: 'Duplicate webhook ignored' },
  { id: 'AL-008', timestamp: '2025-01-14 19:12:33', actor: 'sarah@example.com', action: 'BOOKING_CANCELLED', target: 'BK-0992', detail: 'User-initiated cancellation' },
];

const actionBadge = {
  REFUND_APPROVED: { variant: 'success', label: 'Refund' },
  SEAT_HOLD_EXPIRED: { variant: 'secondary', label: 'Hold Expired' },
  USER_SUSPENDED: { variant: 'destructive', label: 'User Suspended' },
  SCHEDULE_UPDATED: { variant: 'secondary', label: 'Schedule' },
  PAYMENT_WEBHOOK: { variant: 'success', label: 'Payment' },
  PROMO_CREATED: { variant: 'secondary', label: 'Promo' },
  IDEMPOTENCY_REPLAY: { variant: 'warning', label: 'Idempotent' },
  BOOKING_CANCELLED: { variant: 'destructive', label: 'Cancelled' },
};

/* ============ Overview Tab ============ */
function OverviewTab() {
  return (
    <div className="space-y-8">
      {/* Stats Grid */}
      <motion.div variants={staggerContainer} initial="initial" animate="animate" className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {statCards.map((stat) => {
          const Icon = stat.icon;
          const isPositive = stat.change > 0;
          return (
            <motion.div key={stat.title} variants={staggerItem}>
              <Card className="relative overflow-hidden">
                <CardContent className="p-6">
                  <div className="flex items-start justify-between">
                    <div>
                      <p className="text-sm text-muted-foreground mb-1">{stat.title}</p>
                      <p className="text-2xl font-bold">{stat.value}</p>
                    </div>
                    <div className={`flex items-center justify-center h-10 w-10 rounded-xl bg-gradient-to-br ${stat.color} text-white shadow-lg`}>
                      <Icon className="h-5 w-5" />
                    </div>
                  </div>
                  <div className={`flex items-center gap-1 mt-3 text-sm ${isPositive ? 'text-emerald-600 dark:text-emerald-400' : 'text-red-600 dark:text-red-400'}`}>
                    {isPositive ? <ArrowUpRight className="h-4 w-4" /> : <ArrowDownRight className="h-4 w-4" />}
                    <span className="font-medium">{Math.abs(stat.change)}%</span>
                    <span className="text-muted-foreground">vs last month</span>
                  </div>
                </CardContent>
              </Card>
            </motion.div>
          );
        })}
      </motion.div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <motion.div {...fadeInUp} transition={{ delay: 0.2 }} className="lg:col-span-2">
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle className="text-lg">Revenue Overview</CardTitle>
                  <CardDescription>Monthly revenue for the current year</CardDescription>
                </div>
                <Tabs defaultValue="12m">
                  <TabsList className="h-8">
                    <TabsTrigger value="7d" className="text-xs px-2">7D</TabsTrigger>
                    <TabsTrigger value="1m" className="text-xs px-2">1M</TabsTrigger>
                    <TabsTrigger value="12m" className="text-xs px-2">12M</TabsTrigger>
                  </TabsList>
                </Tabs>
              </div>
            </CardHeader>
            <CardContent>
              <div className="h-64 flex items-end gap-2 px-4">
                {[40, 55, 35, 70, 85, 60, 90, 75, 95, 80, 65, 88].map((h, i) => (
                  <motion.div
                    key={i}
                    initial={{ height: 0 }}
                    animate={{ height: `${h}%` }}
                    transition={{ delay: 0.1 * i, duration: 0.5 }}
                    className="flex-1 rounded-t-lg gradient-primary opacity-80 hover:opacity-100 transition-opacity cursor-pointer relative group"
                  >
                    <div className="absolute -top-8 left-1/2 -translate-x-1/2 hidden group-hover:block bg-foreground text-background text-xs px-2 py-1 rounded-lg whitespace-nowrap">
                      ${Math.round(h * 140)}k
                    </div>
                  </motion.div>
                ))}
              </div>
              <div className="flex justify-between mt-2 px-4 text-xs text-muted-foreground">
                {['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].map((m) => (
                  <span key={m}>{m}</span>
                ))}
              </div>
            </CardContent>
          </Card>
        </motion.div>

        <motion.div {...fadeInUp} transition={{ delay: 0.3 }}>
          <Card className="h-full">
            <CardHeader>
              <CardTitle className="text-lg">Bookings by Category</CardTitle>
              <CardDescription>Distribution this month</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {[
                { name: 'Concerts', pct: 42, color: 'bg-indigo-500' },
                { name: 'Sports', pct: 28, color: 'bg-emerald-500' },
                { name: 'Theatre', pct: 18, color: 'bg-rose-500' },
                { name: 'Festivals', pct: 8, color: 'bg-amber-500' },
                { name: 'Shows', pct: 4, color: 'bg-cyan-500' },
              ].map((cat) => (
                <div key={cat.name}>
                  <div className="flex items-center justify-between text-sm mb-1">
                    <div className="flex items-center gap-2">
                      <div className={`h-2.5 w-2.5 rounded-full ${cat.color}`} />
                      <span>{cat.name}</span>
                    </div>
                    <span className="font-medium">{cat.pct}%</span>
                  </div>
                  <div className="h-2 bg-secondary rounded-full overflow-hidden">
                    <motion.div initial={{ width: 0 }} animate={{ width: `${cat.pct}%` }} transition={{ delay: 0.5, duration: 0.5 }} className={`h-full rounded-full ${cat.color}`} />
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </motion.div>
      </div>

      {/* Recent Bookings */}
      <motion.div {...fadeInUp} transition={{ delay: 0.4 }}>
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="text-lg">Recent Bookings</CardTitle>
                <CardDescription>Latest booking activity</CardDescription>
              </div>
              <Badge variant="secondary" className="text-xs"><Activity className="h-3 w-3 mr-1" /> Live</Badge>
            </div>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b text-muted-foreground">
                    <th className="text-left py-3 px-2 font-medium">Booking ID</th>
                    <th className="text-left py-3 px-2 font-medium">Event</th>
                    <th className="text-left py-3 px-2 font-medium hidden md:table-cell">Seats</th>
                    <th className="text-left py-3 px-2 font-medium">Amount</th>
                    <th className="text-left py-3 px-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {mockBookings.map((booking) => (
                    <tr key={booking.id} className="border-b last:border-0 hover:bg-muted/50 transition-colors">
                      <td className="py-3 px-2 font-mono text-xs">{booking.id}</td>
                      <td className="py-3 px-2 font-medium max-w-[200px] truncate">{booking.eventTitle}</td>
                      <td className="py-3 px-2 hidden md:table-cell">{booking.seats.join(', ')}</td>
                      <td className="py-3 px-2 font-medium">{formatCurrency(booking.total)}</td>
                      <td className="py-3 px-2">
                        <Badge variant={booking.status === 'CONFIRMED' ? 'success' : booking.status === 'PENDING' ? 'warning' : 'destructive'} className="text-xs">
                          {booking.status}
                        </Badge>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </motion.div>
    </div>
  );
}

/* ============ User Management Tab ============ */
function UsersTab() {
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState('all');

  const filtered = mockUsers.filter((u) => {
    if (roleFilter !== 'all' && u.role !== roleFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      return u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q) || u.id.toLowerCase().includes(q);
    }
    return true;
  });

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search users..." value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
        </div>
        <div className="flex items-center gap-2">
          <select
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            className="h-10 rounded-xl border border-input bg-background px-3 text-sm"
          >
            <option value="all">All Roles</option>
            <option value="User">Users</option>
            <option value="Operator">Operators</option>
          </select>
          <Button className="gap-2"><Plus className="h-4 w-4" /> Add User</Button>
        </div>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">User</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Role</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground hidden md:table-cell">Bookings</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground hidden lg:table-cell">Joined</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Status</th>
                <th className="text-right p-4 text-xs font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.id} className="border-b last:border-0 hover:bg-muted/30 transition-colors">
                  <td className="p-4">
                    <div className="flex items-center gap-3">
                      <div className="h-8 w-8 rounded-full gradient-primary flex items-center justify-center text-white text-xs font-medium">
                        {u.name.split(' ').map((n) => n[0]).join('').slice(0, 2)}
                      </div>
                      <div>
                        <p className="font-medium">{u.name}</p>
                        <p className="text-xs text-muted-foreground">{u.email}</p>
                      </div>
                    </div>
                  </td>
                  <td className="p-4">
                    <Badge variant={u.role === 'Operator' ? 'secondary' : 'outline'} className="text-xs">
                      {u.role}
                    </Badge>
                  </td>
                  <td className="p-4 hidden md:table-cell">{u.bookings}</td>
                  <td className="p-4 hidden lg:table-cell text-muted-foreground">{u.joined}</td>
                  <td className="p-4">
                    <Badge
                      variant={u.status === 'Active' ? 'success' : u.status === 'Suspended' ? 'destructive' : 'warning'}
                      className="text-xs"
                    >
                      {u.status}
                    </Badge>
                  </td>
                  <td className="p-4 text-right">
                    <div className="flex items-center justify-end gap-1">
                      <button className="p-1.5 rounded-lg hover:bg-muted" title="Edit">
                        <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
                      </button>
                      {u.status === 'Active' ? (
                        <button className="p-1.5 rounded-lg hover:bg-muted" title="Suspend">
                          <Ban className="h-3.5 w-3.5 text-amber-500" />
                        </button>
                      ) : u.status === 'Suspended' ? (
                        <button className="p-1.5 rounded-lg hover:bg-muted" title="Activate">
                          <UserCheck className="h-3.5 w-3.5 text-emerald-500" />
                        </button>
                      ) : (
                        <button className="p-1.5 rounded-lg hover:bg-muted" title="Approve">
                          <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />
                        </button>
                      )}
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

/* ============ Audit Log Tab ============ */
function AuditLogTab() {
  const [search, setSearch] = useState('');
  const filtered = mockAuditLogs.filter((l) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return l.action.toLowerCase().includes(q) || l.actor.toLowerCase().includes(q) || l.target.toLowerCase().includes(q) || l.detail.toLowerCase().includes(q);
  });

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input placeholder="Search audit logs..." value={search} onChange={(e) => setSearch(e.target.value)} className="pl-9" />
        </div>
        <Button variant="outline" className="gap-2">
          <Download className="h-4 w-4" /> Export
        </Button>
      </div>

      <Card>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b">
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Timestamp</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Actor</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground">Action</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground hidden md:table-cell">Target</th>
                <th className="text-left p-4 text-xs font-medium text-muted-foreground hidden lg:table-cell">Detail</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((log) => {
                const badge = actionBadge[log.action] || { variant: 'secondary', label: log.action };
                return (
                  <tr key={log.id} className="border-b last:border-0 hover:bg-muted/30 transition-colors">
                    <td className="p-4 text-xs font-mono text-muted-foreground whitespace-nowrap">{log.timestamp}</td>
                    <td className="p-4 text-sm">{log.actor}</td>
                    <td className="p-4">
                      <Badge variant={badge.variant} className="text-xs">{badge.label}</Badge>
                    </td>
                    <td className="p-4 text-xs font-mono hidden md:table-cell">{log.target}</td>
                    <td className="p-4 text-xs text-muted-foreground hidden lg:table-cell max-w-[300px] truncate">{log.detail}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </Card>
    </motion.div>
  );
}

/* ============ Refund Policy Tab ============ */
function RefundPolicyTab() {
  const [policies, setPolicies] = useState([
    { id: 1, name: 'Standard Cancellation', window: '24 hours', refundPct: 100, description: 'Full refund if cancelled more than 24 hours before event' },
    { id: 2, name: 'Late Cancellation', window: '2-24 hours', refundPct: 50, description: '50% refund if cancelled between 2-24 hours before event' },
    { id: 3, name: 'Last Minute', window: '<2 hours', refundPct: 0, description: 'No refund for cancellations within 2 hours of event' },
    { id: 4, name: 'Event Cancelled', window: 'N/A', refundPct: 100, description: 'Full refund + compensation if event is cancelled by organizer' },
  ]);

  return (
    <motion.div {...fadeInUp} className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h3 className="font-semibold">Refund Policies</h3>
          <p className="text-sm text-muted-foreground">Configure refund rules applied during booking cancellation</p>
        </div>
        <Button className="gap-2"><Plus className="h-4 w-4" /> Add Policy</Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {policies.map((p) => (
          <Card key={p.id} className="hover:shadow-md transition-shadow">
            <CardContent className="p-5 space-y-4">
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <div className={cn(
                    'p-2 rounded-xl',
                    p.refundPct === 100 ? 'bg-emerald-500/10' :
                    p.refundPct > 0 ? 'bg-amber-500/10' : 'bg-destructive/10'
                  )}>
                    <RefreshCw className={cn(
                      'h-4 w-4',
                      p.refundPct === 100 ? 'text-emerald-500' :
                      p.refundPct > 0 ? 'text-amber-500' : 'text-destructive'
                    )} />
                  </div>
                  <div>
                    <h4 className="font-medium">{p.name}</h4>
                    <p className="text-xs text-muted-foreground">Window: {p.window}</p>
                  </div>
                </div>
                <button className="p-1.5 rounded-lg hover:bg-muted">
                  <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
                </button>
              </div>
              <p className="text-sm text-muted-foreground">{p.description}</p>
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">Refund Amount</span>
                <span className={cn(
                  'text-xl font-bold',
                  p.refundPct === 100 ? 'text-emerald-500' :
                  p.refundPct > 0 ? 'text-amber-500' : 'text-destructive'
                )}>
                  {p.refundPct}%
                </span>
              </div>
              <div className="w-full bg-muted rounded-full h-2">
                <div
                  className={cn(
                    'h-2 rounded-full transition-all',
                    p.refundPct === 100 ? 'bg-emerald-500' :
                    p.refundPct > 0 ? 'bg-amber-500' : 'bg-destructive'
                  )}
                  style={{ width: `${p.refundPct}%` }}
                />
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </motion.div>
  );
}

/* ============ Main Admin Dashboard ============ */
export default function AdminDashboard() {
  return (
    <div className="max-w-7xl mx-auto px-4 md:px-6 py-8 space-y-8">
      {/* Header */}
      <motion.div {...fadeInUp} className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold mb-1 flex items-center gap-2">
            <Shield className="h-7 w-7 text-primary" /> Admin Dashboard
          </h1>
          <p className="text-muted-foreground">Overview and management of the TicketWave platform</p>
        </div>
        <Badge variant="secondary" className="text-xs gap-1">
          <Activity className="h-3 w-3" /> Live
        </Badge>
      </motion.div>

      {/* Tab Navigation */}
      <Tabs defaultValue="overview">
        <TabsList className="mb-6">
          <TabsTrigger value="overview" className="gap-1.5">
            <BarChart3 className="h-3.5 w-3.5" /> Overview
          </TabsTrigger>
          <TabsTrigger value="users" className="gap-1.5">
            <Users className="h-3.5 w-3.5" /> Users
          </TabsTrigger>
          <TabsTrigger value="audit" className="gap-1.5">
            <FileText className="h-3.5 w-3.5" /> Audit Log
          </TabsTrigger>
          <TabsTrigger value="refund" className="gap-1.5">
            <RefreshCw className="h-3.5 w-3.5" /> Refund Policies
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview"><OverviewTab /></TabsContent>
        <TabsContent value="users"><UsersTab /></TabsContent>
        <TabsContent value="audit"><AuditLogTab /></TabsContent>
        <TabsContent value="refund"><RefundPolicyTab /></TabsContent>
      </Tabs>
    </div>
  );
}
