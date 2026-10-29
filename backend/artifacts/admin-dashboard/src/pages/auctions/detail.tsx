import { useParams } from "wouter";
import { useGetAuction, useListAuctionBids, useUpdateAuction } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useToast } from "@/hooks/use-toast";
import { useQueryClient } from "@tanstack/react-query";
import { getGetAuctionQueryKey } from "@workspace/api-client-react";

export default function AuctionDetail() {
  const { id } = useParams<{ id: string }>();
  const auctionId = Number(id);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: auction, isLoading } = useGetAuction(auctionId, { 
    query: { enabled: !!auctionId, queryKey: getGetAuctionQueryKey(auctionId) } 
  });
  
  const { data: bids } = useListAuctionBids(auctionId, {
    query: { enabled: !!auctionId }
  });

  const updateAuction = useUpdateAuction({
    mutation: {
      onSuccess: () => {
        toast({ title: "Status updated" });
        queryClient.invalidateQueries({ queryKey: getGetAuctionQueryKey(auctionId) });
      }
    }
  });

  if (isLoading) return <div className="p-8 animate-pulse flex justify-center"><div className="w-8 h-8 rounded-full border-4 border-primary border-t-transparent animate-spin" /></div>;
  if (!auction) return <div className="p-8 font-mono text-center text-muted-foreground">AUCTION NOT FOUND</div>;

  const formatCurrency = (amount: number) => {
    if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)} Cr`;
    if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)} L`;
    return `₹${amount.toLocaleString("en-IN")}`;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold tracking-tight uppercase">
            {auction.car?.brand} {auction.car?.model} <span className="text-muted-foreground">({auction.car?.year})</span>
          </h1>
          <p className="text-muted-foreground font-mono text-sm mt-1">AUCTION ID: #{auction.id}</p>
        </div>
        
        <div className="flex items-center gap-3">
          <Badge variant="outline" className={`font-mono text-sm px-3 py-1 uppercase border ${
            auction.status === 'live' ? 'bg-primary/10 text-primary border-primary/30' :
            auction.status === 'upcoming' ? 'bg-accent/10 text-accent border-accent/30' :
            'bg-muted text-muted-foreground border-border'
          }`}>
            {auction.status === 'live' && <span className="w-2 h-2 rounded-full bg-primary mr-2 animate-pulse" />}
            {auction.status}
          </Badge>

          <Select 
            value={auction.status} 
            onValueChange={(val: any) => updateAuction.mutate({ id: auctionId, data: { status: val } })}
            disabled={updateAuction.isPending}
          >
            <SelectTrigger className="w-[140px] font-mono text-xs h-8">
              <SelectValue placeholder="Change Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="upcoming">SET UPCOMING</SelectItem>
              <SelectItem value="live">SET LIVE</SelectItem>
              <SelectItem value="ended">SET ENDED</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <Card className="border-border md:col-span-2">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Vehicle Telemetry</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="grid grid-cols-2 sm:grid-cols-4 divide-x divide-y divide-border border-b border-border">
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">BRAND</div>
                <div className="font-medium">{auction.car?.brand}</div>
              </div>
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">MODEL</div>
                <div className="font-medium">{auction.car?.model}</div>
              </div>
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">YEAR</div>
                <div className="font-medium">{auction.car?.year}</div>
              </div>
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">MILEAGE</div>
                <div className="font-mono">{auction.car?.mileage.toLocaleString()} km</div>
              </div>
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">FUEL</div>
                <div className="font-medium uppercase">{auction.car?.fuelType}</div>
              </div>
              <div className="p-4">
                <div className="text-xs text-muted-foreground font-mono mb-1">TRANS</div>
                <div className="font-medium uppercase">{auction.car?.transmission}</div>
              </div>
              <div className="p-4 col-span-2 sm:col-span-2">
                <div className="text-xs text-muted-foreground font-mono mb-1">LOCATION</div>
                <div className="font-medium truncate">{auction.car?.location}</div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-border">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Financials</CardTitle>
          </CardHeader>
          <CardContent className="p-6 space-y-6">
            <div>
              <div className="text-xs text-muted-foreground font-mono mb-1">CURRENT BID</div>
              <div className="text-3xl font-bold text-primary font-mono">{formatCurrency(auction.currentBid || auction.startingPrice)}</div>
            </div>
            <div className="grid grid-cols-2 gap-4 pt-4 border-t border-border">
              <div>
                <div className="text-xs text-muted-foreground font-mono mb-1">STARTING</div>
                <div className="font-mono text-sm">{formatCurrency(auction.startingPrice)}</div>
              </div>
              <div>
                <div className="text-xs text-muted-foreground font-mono mb-1">RESERVE</div>
                <div className="font-mono text-sm">{formatCurrency(auction.reservePrice)}</div>
              </div>
            </div>
            {auction.status === 'ended' && auction.winningBid && (
              <div className="pt-4 border-t border-border">
                <div className="text-xs text-accent font-mono mb-1">WINNING BID</div>
                <div className="text-xl font-bold text-accent font-mono">{formatCurrency(auction.winningBid)}</div>
                <div className="text-xs text-muted-foreground font-mono mt-1">WINNER ID: {auction.winnerId}</div>
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border-border md:col-span-3">
          <CardHeader className="border-b border-border bg-secondary/20">
            <CardTitle className="font-mono text-sm uppercase">Bid History Log ({auction.bidCount})</CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow className="border-border bg-secondary/10 hover:bg-secondary/10">
                  <TableHead className="font-mono text-xs w-[180px]">Timestamp</TableHead>
                  <TableHead className="font-mono text-xs">Bidder</TableHead>
                  <TableHead className="font-mono text-xs text-right">Amount</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {bids?.map((bid) => (
                  <TableRow key={bid.id} className="border-border">
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {new Date(bid.createdAt).toLocaleString()}
                    </TableCell>
                    <TableCell className="font-medium">
                      {bid.bidderName || `User #${bid.bidderId}`}
                    </TableCell>
                    <TableCell className="text-right font-mono text-primary font-bold">
                      {formatCurrency(bid.amount)}
                    </TableCell>
                  </TableRow>
                ))}
                {!bids?.length && (
                  <TableRow>
                    <TableCell colSpan={3} className="text-center py-8 font-mono text-muted-foreground">
                      NO BIDS RECORDED
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}