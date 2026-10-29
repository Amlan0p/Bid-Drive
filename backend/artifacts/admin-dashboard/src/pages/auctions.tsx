import { useState } from "react";
import { Link } from "wouter";
import { useListAuctions, useCreateAuction } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Search, Plus, Filter, ArrowUpRight } from "lucide-react";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { useToast } from "@/hooks/use-toast";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useQueryClient } from "@tanstack/react-query";
import { getListAuctionsQueryKey } from "@workspace/api-client-react";

export default function Auctions() {
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: response, isLoading } = useListAuctions(
    statusFilter !== "all" ? { status: statusFilter as any } : {}
  );
  
  const createAuction = useCreateAuction({
    mutation: {
      onSuccess: () => {
        toast({ title: "Auction created successfully" });
        queryClient.invalidateQueries({ queryKey: getListAuctionsQueryKey() });
      },
      onError: (err: any) => {
        toast({ title: "Error creating auction", description: err.message, variant: "destructive" });
      }
    }
  });

  const [carId, setCarId] = useState("");
  const [startingPrice, setStartingPrice] = useState("");
  const [reservePrice, setReservePrice] = useState("");
  const [startTime, setStartTime] = useState("");
  const [endTime, setEndTime] = useState("");

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    createAuction.mutate({
      data: {
        carId: Number(carId),
        startingPrice: Number(startingPrice),
        reservePrice: Number(reservePrice),
        startTime: new Date(startTime).toISOString(),
        endTime: new Date(endTime).toISOString()
      }
    });
  };

  const formatCurrency = (amount: number) => {
    if (amount >= 10000000) return `₹${(amount / 10000000).toFixed(2)} Cr`;
    if (amount >= 100000) return `₹${(amount / 100000).toFixed(1)} L`;
    return `₹${amount.toLocaleString("en-IN")}`;
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">Auctions</h1>
        
        <Dialog>
          <DialogTrigger asChild>
            <Button className="font-mono">
              <Plus className="mr-2 h-4 w-4" /> NEW AUCTION
            </Button>
          </DialogTrigger>
          <DialogContent className="border-border bg-card">
            <DialogHeader>
              <DialogTitle className="font-mono uppercase">Create Auction</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleCreate} className="space-y-4 pt-4">
              <div className="space-y-2">
                <label className="text-sm font-mono text-muted-foreground">Car ID</label>
                <Input value={carId} onChange={(e) => setCarId(e.target.value)} required type="number" className="font-mono" />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-mono text-muted-foreground">Starting Price</label>
                  <Input value={startingPrice} onChange={(e) => setStartingPrice(e.target.value)} required type="number" className="font-mono" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-mono text-muted-foreground">Reserve Price</label>
                  <Input value={reservePrice} onChange={(e) => setReservePrice(e.target.value)} required type="number" className="font-mono" />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <label className="text-sm font-mono text-muted-foreground">Start Time</label>
                  <Input value={startTime} onChange={(e) => setStartTime(e.target.value)} required type="datetime-local" className="font-mono" />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-mono text-muted-foreground">End Time</label>
                  <Input value={endTime} onChange={(e) => setEndTime(e.target.value)} required type="datetime-local" className="font-mono" />
                </div>
              </div>
              <Button type="submit" className="w-full font-mono" disabled={createAuction.isPending}>
                {createAuction.isPending ? "CREATING..." : "INITIALIZE AUCTION"}
              </Button>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <Card className="border-border">
        <CardHeader className="py-4 border-b border-border bg-secondary/30">
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Search auctions..." className="pl-9 font-mono text-sm bg-background" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[180px] font-mono text-sm">
                <div className="flex items-center gap-2"><Filter className="h-4 w-4" /> <SelectValue placeholder="Status" /></div>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">ALL STATUSES</SelectItem>
                <SelectItem value="upcoming">UPCOMING</SelectItem>
                <SelectItem value="live">LIVE</SelectItem>
                <SelectItem value="ended">ENDED</SelectItem>
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
                  <TableHead className="font-mono text-xs">ID</TableHead>
                  <TableHead className="font-mono text-xs">Vehicle</TableHead>
                  <TableHead className="font-mono text-xs">Current Bid</TableHead>
                  <TableHead className="font-mono text-xs">Bids</TableHead>
                  <TableHead className="font-mono text-xs">Status</TableHead>
                  <TableHead className="font-mono text-xs text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {response?.auctions?.map((auction) => (
                  <TableRow key={auction.id} className="border-border hover:bg-secondary/30">
                    <TableCell className="font-mono text-muted-foreground">#{auction.id}</TableCell>
                    <TableCell className="font-medium text-foreground">
                      {auction.car?.brand} {auction.car?.model} ({auction.car?.year})
                    </TableCell>
                    <TableCell className="font-mono">{formatCurrency(auction.currentBid || auction.startingPrice)}</TableCell>
                    <TableCell className="font-mono">{auction.bidCount}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`font-mono text-xs uppercase border ${
                        auction.status === 'live' ? 'bg-primary/10 text-primary border-primary/30' :
                        auction.status === 'upcoming' ? 'bg-accent/10 text-accent border-accent/30' :
                        'bg-muted text-muted-foreground border-border'
                      }`}>
                        {auction.status === 'live' && <span className="w-1.5 h-1.5 rounded-full bg-primary mr-1.5 animate-pulse" />}
                        {auction.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <Link href={`/auctions/${auction.id}`}>
                        <Button variant="ghost" size="sm" className="font-mono text-xs h-8">
                          DETAILS <ArrowUpRight className="ml-1 h-3 w-3" />
                        </Button>
                      </Link>
                    </TableCell>
                  </TableRow>
                ))}
                {!response?.auctions?.length && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground font-mono">
                      NO AUCTIONS FOUND
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