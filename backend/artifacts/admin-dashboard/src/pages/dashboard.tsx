import { useGetDashboardStats } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Users, CarFront, Gavel, DollarSign, Activity, ArrowUpRight } from "lucide-react";
import { Link } from "wouter";
import { Button } from "@/components/ui/button";

function formatCurrency(amount: number) {
  if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)} Cr`;
  if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)} L`;
  return `₹${amount.toLocaleString("en-IN")}`;
}

export default function Dashboard() {
  const { data: stats, isLoading } = useGetDashboardStats({ query: { queryKey: ["/api/dashboard/stats"] } });

  if (isLoading) {
    return (
      <div className="space-y-6 animate-pulse">
        <div className="h-8 w-48 bg-secondary rounded"></div>
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          {[...Array(4)].map((_, i) => (
            <Card key={i} className="border-border">
              <CardHeader className="flex flex-row items-center justify-between pb-2"><div className="h-4 w-24 bg-secondary rounded"></div></CardHeader>
              <CardContent><div className="h-8 w-16 bg-secondary rounded"></div></CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">System Overview</h1>
        <div className="flex items-center gap-2">
          <span className="text-sm font-mono text-muted-foreground">AUTO-REFRESH:</span>
          <span className="text-sm font-mono text-primary">ACTIVE</span>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
        <Card className="border-border bg-card/50 backdrop-blur">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground font-mono">TOTAL REVENUE</CardTitle>
            <DollarSign className="h-4 w-4 text-green-500" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{formatCurrency(stats?.totalRevenue || 0)}</div>
          </CardContent>
        </Card>
        
        <Card className="border-primary/50 bg-primary/5">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-primary font-mono flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-primary animate-pulse"></span>
              LIVE AUCTIONS
            </CardTitle>
            <Activity className="h-4 w-4 text-primary" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-primary">{stats?.liveAuctions || 0}</div>
            <p className="text-xs text-primary/70 mt-1 font-mono">ACTIVE SESSIONS</p>
          </CardContent>
        </Card>

        <Card className="border-border bg-card/50 backdrop-blur">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground font-mono">PENDING LISTINGS</CardTitle>
            <CarFront className="h-4 w-4 text-accent" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{stats?.pendingListings || 0}</div>
            {stats?.pendingListings ? (
              <Link href="/cars?status=pending">
                <span className="text-xs text-accent hover:underline cursor-pointer flex items-center mt-1 font-mono">
                  ACTION REQUIRED <ArrowUpRight className="h-3 w-3 ml-1" />
                </span>
              </Link>
            ) : null}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/50 backdrop-blur">
          <CardHeader className="flex flex-row items-center justify-between pb-2">
            <CardTitle className="text-sm font-medium text-muted-foreground font-mono">TOTAL USERS</CardTitle>
            <Users className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold text-foreground">{stats?.totalUsers || 0}</div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Auctions Table */}
        <Card className="border-border col-span-1 lg:col-span-2">
          <CardHeader className="flex flex-row items-center justify-between">
            <CardTitle className="font-mono uppercase">Recent Auctions</CardTitle>
            <Link href="/auctions">
              <Button variant="outline" size="sm" className="font-mono text-xs">VIEW ALL</Button>
            </Link>
          </CardHeader>
          <CardContent>
            <div className="overflow-x-auto">
              <table className="w-full text-sm text-left">
                <thead className="text-xs text-muted-foreground font-mono uppercase bg-secondary/50 border-b border-border">
                  <tr>
                    <th className="px-4 py-3">Vehicle</th>
                    <th className="px-4 py-3">Current Bid</th>
                    <th className="px-4 py-3">Bids</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Ends</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border">
                  {stats?.recentAuctions?.map((auction) => (
                    <tr key={auction.id} className="hover:bg-secondary/30 transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {auction.car?.brand} {auction.car?.model} ({auction.car?.year})
                      </td>
                      <td className="px-4 py-3 font-mono">{formatCurrency(auction.currentBid)}</td>
                      <td className="px-4 py-3 font-mono">{auction.bidCount}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium font-mono uppercase border ${
                          auction.status === 'live' ? 'bg-primary/10 text-primary border-primary/30' :
                          auction.status === 'upcoming' ? 'bg-accent/10 text-accent border-accent/30' :
                          'bg-muted text-muted-foreground border-border'
                        }`}>
                          {auction.status === 'live' && <span className="w-1.5 h-1.5 rounded-full bg-primary mr-1.5 animate-pulse" />}
                          {auction.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground font-mono">
                        {new Date(auction.endTime).toLocaleDateString()}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <Link href={`/auctions/${auction.id}`}>
                          <Button variant="ghost" size="sm" className="h-8 font-mono text-xs">
                            DETAILS <ArrowUpRight className="ml-1 h-3 w-3" />
                          </Button>
                        </Link>
                      </td>
                    </tr>
                  ))}
                  {!stats?.recentAuctions?.length && (
                    <tr>
                      <td colSpan={6} className="px-4 py-8 text-center text-muted-foreground font-mono">
                        NO RECENT AUCTIONS FOUND
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}