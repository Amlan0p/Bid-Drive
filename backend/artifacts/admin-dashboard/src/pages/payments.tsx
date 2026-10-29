import { useState } from "react";
import { useListPayments } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Search, Filter } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

export default function Payments() {
  const [statusFilter, setStatusFilter] = useState<string>("all");

  const { data: response, isLoading } = useListPayments(
    statusFilter !== "all" ? { status: statusFilter as any } : {}
  );
  
  const formatCurrency = (amount: number) => {
    if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)} Cr`;
    if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)} L`;
    return `₹${amount.toLocaleString("en-IN")}`;
  };

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">Financial Ledger</h1>
      </div>

      <Card className="border-border">
        <CardHeader className="py-4 border-b border-border bg-secondary/30">
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Search transaction ID..." className="pl-9 font-mono text-sm bg-background" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[180px] font-mono text-sm">
                <div className="flex items-center gap-2"><Filter className="h-4 w-4" /> <SelectValue placeholder="Status" /></div>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">ALL STATUSES</SelectItem>
                <SelectItem value="completed">COMPLETED</SelectItem>
                <SelectItem value="pending">PENDING</SelectItem>
                <SelectItem value="failed">FAILED</SelectItem>
                <SelectItem value="refunded">REFUNDED</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="p-8 space-y-4">
              {[...Array(5)].map((_, i) => (
                <div key={i} className="h-12 bg-secondary animate-pulse rounded" />
              ))}
            </div>
          ) : (
            <Table>
              <TableHeader className="bg-secondary/50">
                <TableRow className="border-border">
                  <TableHead className="font-mono text-xs">TXN ID</TableHead>
                  <TableHead className="font-mono text-xs">Stripe Ref</TableHead>
                  <TableHead className="font-mono text-xs">Auction ID</TableHead>
                  <TableHead className="font-mono text-xs">Buyer ID</TableHead>
                  <TableHead className="font-mono text-xs text-right">Amount</TableHead>
                  <TableHead className="font-mono text-xs text-right">Status</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {response?.payments?.map((payment) => (
                  <TableRow key={payment.id} className="border-border hover:bg-secondary/30">
                    <TableCell className="font-mono text-muted-foreground">TXN-{payment.id}</TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground truncate max-w-[150px]">
                      {payment.stripePaymentIntentId || 'N/A'}
                    </TableCell>
                    <TableCell className="font-mono">AUC-{payment.auctionId}</TableCell>
                    <TableCell className="font-mono text-muted-foreground">USR-{payment.buyerId}</TableCell>
                    <TableCell className="text-right font-mono font-bold text-foreground">
                      {formatCurrency(payment.amount)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Badge variant="outline" className={`font-mono text-xs uppercase border ${
                        payment.status === 'completed' ? 'bg-green-500/10 text-green-500 border-green-500/30' :
                        payment.status === 'pending' ? 'bg-primary/10 text-primary border-primary/30' :
                        payment.status === 'failed' ? 'bg-destructive/10 text-destructive border-destructive/30' :
                        'bg-muted text-muted-foreground border-border'
                      }`}>
                        {payment.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))}
                {!response?.payments?.length && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground font-mono">
                      NO TRANSACTIONS FOUND
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </div>
  );
}