import { useGetAuctionAnalytics, useGetRevenueAnalytics } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { 
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip as RechartsTooltip, ResponsiveContainer,
  BarChart, Bar, PieChart, Pie, Cell
} from "recharts";

const COLORS = ['hsl(38 92% 50%)', 'hsl(215 20% 65%)', 'hsl(217 32% 15%)'];
const REVENUE_COLOR = 'hsl(142 71% 45%)'; // Green

const CustomTooltip = ({ active, payload, label }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-card border border-border p-3 rounded-lg shadow-lg font-mono text-sm">
        <p className="text-muted-foreground mb-1">{label}</p>
        {payload.map((p: any, i: number) => (
          <p key={i} style={{ color: p.color }} className="font-bold">
            {p.name}: {p.name === 'Revenue' ? `₹${p.value.toLocaleString("en-IN")}` : p.value}
          </p>
        ))}
      </div>
    );
  }
  return null;
};

export default function Analytics() {
  const { data: auctionData, isLoading: auctionsLoading } = useGetAuctionAnalytics({ query: { queryKey: ["/api/analytics/auctions"] } });
  const { data: revenueData, isLoading: revenueLoading } = useGetRevenueAnalytics({ query: { queryKey: ["/api/analytics/revenue"] } });

  const formatCurrency = (amount: number) => {
    if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)} Cr`;
    if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)} L`;
    return `₹${amount.toLocaleString("en-IN")}`;
  };

  const statusData = auctionData ? [
    { name: 'Live', value: auctionData.statusBreakdown.live },
    { name: 'Ended', value: auctionData.statusBreakdown.ended },
    { name: 'Upcoming', value: auctionData.statusBreakdown.upcoming },
  ] : [];

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">System Analytics</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="border-border col-span-1 lg:col-span-2 bg-card/50 backdrop-blur">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Revenue Velocity</CardTitle>
          </CardHeader>
          <CardContent className="p-6">
            {revenueLoading ? (
              <div className="h-[300px] bg-secondary/50 animate-pulse rounded" />
            ) : (
              <div className="h-[300px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={revenueData?.revenueOverTime || []}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
                    <XAxis 
                      dataKey="date" 
                      stroke="hsl(var(--muted-foreground))" 
                      fontSize={12} 
                      tickLine={false} 
                      axisLine={false} 
                      tickFormatter={(val) => new Date(val).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                    />
                    <YAxis 
                      stroke="hsl(var(--muted-foreground))" 
                      fontSize={12} 
                      tickLine={false} 
                      axisLine={false}
                      tickFormatter={(val) => `$${(val / 1000).toFixed(0)}k`}
                    />
                    <RechartsTooltip content={<CustomTooltip />} />
                    <Bar dataKey="amount" name="Revenue" fill={REVENUE_COLOR} radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/50 backdrop-blur">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Bid Activity</CardTitle>
          </CardHeader>
          <CardContent className="p-6">
            {auctionsLoading ? (
              <div className="h-[250px] bg-secondary/50 animate-pulse rounded" />
            ) : (
              <div className="h-[250px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={auctionData?.bidsOverTime || []}>
                    <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
                    <XAxis 
                      dataKey="date" 
                      stroke="hsl(var(--muted-foreground))" 
                      fontSize={12} 
                      tickLine={false} 
                      axisLine={false}
                      tickFormatter={(val) => new Date(val).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                    />
                    <YAxis 
                      stroke="hsl(var(--muted-foreground))" 
                      fontSize={12} 
                      tickLine={false} 
                      axisLine={false}
                    />
                    <RechartsTooltip content={<CustomTooltip />} />
                    <Line type="monotone" dataKey="count" name="Bids" stroke="hsl(38 92% 50%)" strokeWidth={2} dot={{ r: 3, fill: "hsl(38 92% 50%)" }} activeDot={{ r: 5 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border bg-card/50 backdrop-blur">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Auction Status Distribution</CardTitle>
          </CardHeader>
          <CardContent className="p-6 flex items-center justify-center">
            {auctionsLoading ? (
              <div className="h-[250px] w-[250px] bg-secondary/50 animate-pulse rounded-full" />
            ) : (
              <div className="h-[250px] w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={statusData}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={80}
                      paddingAngle={5}
                      dataKey="value"
                      stroke="none"
                    >
                      {statusData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                      ))}
                    </Pie>
                    <RechartsTooltip content={<CustomTooltip />} />
                  </PieChart>
                </ResponsiveContainer>
                <div className="flex justify-center gap-6 mt-4">
                  {statusData.map((entry, index) => (
                    <div key={entry.name} className="flex items-center gap-2 font-mono text-xs">
                      <div className="w-3 h-3 rounded-full" style={{ backgroundColor: COLORS[index % COLORS.length] }} />
                      <span className="text-muted-foreground">{entry.name}:</span>
                      <span className="font-bold">{entry.value}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}