import { useState } from 'react';
import { motion } from 'framer-motion';
import {
  User, Mail, Phone, MapPin, Camera, Save,
  Shield, Bell, CreditCard, Key,
} from 'lucide-react';
import {
  Button, Input, Card, CardContent, CardHeader, CardTitle,
  CardDescription, Avatar, AvatarImage, AvatarFallback, Separator,
  Tabs, TabsList, TabsTrigger, TabsContent, Badge,
} from '@/components/ui';
import { useAuth } from '@/context/AuthContext';
import { fadeInUp } from '@/lib/animations';

export default function ProfilePage() {
  const { user } = useAuth();
  const [profile, setProfile] = useState({
    name: user?.name || '',
    email: user?.email || '',
    phone: '+1 (555) 000-0000',
    location: 'San Francisco, CA',
    bio: 'Event enthusiast and music lover.',
  });

  const update = (field, value) => setProfile((prev) => ({ ...prev, [field]: value }));

  return (
    <div className="max-w-4xl mx-auto px-4 md:px-6 py-8 space-y-8">
      <motion.div {...fadeInUp}>
        <h1 className="text-3xl font-bold mb-1">Profile</h1>
        <p className="text-muted-foreground">Manage your account settings and preferences</p>
      </motion.div>

      <Tabs defaultValue="profile" className="space-y-6">
        <TabsList>
          <TabsTrigger value="profile">Profile</TabsTrigger>
          <TabsTrigger value="security">Security</TabsTrigger>
          <TabsTrigger value="notifications">Notifications</TabsTrigger>
        </TabsList>

        {/* Profile Tab */}
        <TabsContent value="profile">
          <motion.div {...fadeInUp} transition={{ delay: 0.1 }} className="space-y-6">
            {/* Avatar section */}
            <Card>
              <CardContent className="p-6">
                <div className="flex items-center gap-6">
                  <div className="relative">
                    <Avatar className="h-20 w-20 text-2xl">
                      {user?.avatar && <AvatarImage src={user.avatar} alt={user?.name || 'User'} />}
                      <AvatarFallback>{user?.name?.charAt(0) || 'U'}</AvatarFallback>
                    </Avatar>
                    <button className="absolute -bottom-1 -right-1 flex items-center justify-center h-8 w-8 rounded-full gradient-primary text-white shadow-lg hover:shadow-xl transition-shadow">
                      <Camera className="h-4 w-4" />
                    </button>
                  </div>
                  <div>
                    <h3 className="text-xl font-semibold">{profile.name}</h3>
                    <p className="text-muted-foreground">{profile.email}</p>
                    <Badge variant="info" className="mt-2 text-xs">
                      <Shield className="h-3 w-3 mr-1" /> Verified Account
                    </Badge>
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Edit fields */}
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Personal Information</CardTitle>
                <CardDescription>Update your personal details</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Full Name</label>
                    <div className="relative">
                      <User className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input value={profile.name} onChange={(e) => update('name', e.target.value)} className="pl-9" />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Email</label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input value={profile.email} onChange={(e) => update('email', e.target.value)} className="pl-9" />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Phone</label>
                    <div className="relative">
                      <Phone className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input value={profile.phone} onChange={(e) => update('phone', e.target.value)} className="pl-9" />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="text-sm font-medium">Location</label>
                    <div className="relative">
                      <MapPin className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                      <Input value={profile.location} onChange={(e) => update('location', e.target.value)} className="pl-9" />
                    </div>
                  </div>
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Bio</label>
                  <textarea
                    value={profile.bio}
                    onChange={(e) => update('bio', e.target.value)}
                    className="flex min-h-[80px] w-full rounded-xl border border-input bg-background px-4 py-3 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 resize-none"
                    rows={3}
                  />
                </div>
                <div className="flex justify-end">
                  <Button className="gap-2">
                    <Save className="h-4 w-4" /> Save Changes
                  </Button>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </TabsContent>

        {/* Security Tab */}
        <TabsContent value="security">
          <motion.div {...fadeInUp} className="space-y-6">
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Change Password</CardTitle>
                <CardDescription>Update your password to keep your account secure</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4 max-w-md">
                <div className="space-y-2">
                  <label className="text-sm font-medium">Current Password</label>
                  <Input type="password" placeholder="Enter current password" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">New Password</label>
                  <Input type="password" placeholder="At least 8 characters" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium">Confirm New Password</label>
                  <Input type="password" placeholder="Repeat new password" />
                </div>
                <Button className="gap-2">
                  <Key className="h-4 w-4" /> Update Password
                </Button>
              </CardContent>
            </Card>
          </motion.div>
        </TabsContent>

        {/* Notifications Tab */}
        <TabsContent value="notifications">
          <motion.div {...fadeInUp}>
            <Card>
              <CardHeader>
                <CardTitle className="text-lg">Notification Preferences</CardTitle>
                <CardDescription>Choose how you receive updates</CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                {[
                  { label: 'Booking confirmations', description: 'Receive emails when bookings are confirmed', default: true },
                  { label: 'Event reminders', description: 'Get notified before your events', default: true },
                  { label: 'Price drops', description: 'Get alerted when prices drop for saved events', default: false },
                  { label: 'Newsletter', description: 'Weekly picks and new event announcements', default: false },
                  { label: 'Marketing emails', description: 'Special promotions and partner offers', default: false },
                ].map((item) => (
                  <div key={item.label} className="flex items-center justify-between py-2">
                    <div>
                      <p className="font-medium text-sm">{item.label}</p>
                      <p className="text-xs text-muted-foreground">{item.description}</p>
                    </div>
                    <label className="relative inline-flex items-center cursor-pointer">
                      <input type="checkbox" defaultChecked={item.default} className="sr-only peer" />
                      <div className="w-10 h-5 bg-muted rounded-full peer peer-checked:after:translate-x-5 peer-checked:bg-primary after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:rounded-full after:h-4 after:w-4 after:transition-all" />
                    </label>
                  </div>
                ))}
                <div className="flex justify-end">
                  <Button className="gap-2">
                    <Save className="h-4 w-4" /> Save Preferences
                  </Button>
                </div>
              </CardContent>
            </Card>
          </motion.div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
