import { useState } from "react";
import { useListCars, useApproveCar, useRejectCar } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Search, Filter, CheckCircle, XCircle } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useQueryClient } from "@tanstack/react-query";
import { getListCarsQueryKey } from "@workspace/api-client-react";

export default function Cars() {
  const [statusFilter, setStatusFilter] = useState<string>("all");
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: response, isLoading } = useListCars(
    statusFilter !== "all" ? { status: statusFilter as any } : {}
  );
  
  const approveCar = useApproveCar({
    mutation: {
      onSuccess: () => {
        toast({ title: "Car approved" });
        queryClient.invalidateQueries({ queryKey: getListCarsQueryKey() });
      }
    }
  });

  const rejectCar = useRejectCar({
    mutation: {
      onSuccess: () => {
        toast({ title: "Car rejected" });
        queryClient.invalidateQueries({ queryKey: getListCarsQueryKey() });
      }
    }
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">Vehicle Listings</h1>
      </div>

      <Card className="border-border">
        <CardHeader className="py-4 border-b border-border bg-secondary/30">
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Search cars..." className="pl-9 font-mono text-sm bg-background" />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-[180px] font-mono text-sm">
                <div className="flex items-center gap-2"><Filter className="h-4 w-4" /> <SelectValue placeholder="Status" /></div>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">ALL STATUSES</SelectItem>
                <SelectItem value="pending">PENDING</SelectItem>
                <SelectItem value="approved">APPROVED</SelectItem>
                <SelectItem value="rejected">REJECTED</SelectItem>
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
                  <TableHead className="font-mono text-xs">Vehicle Info</TableHead>
                  <TableHead className="font-mono text-xs">Spec</TableHead>
                  <TableHead className="font-mono text-xs">Location</TableHead>
                  <TableHead className="font-mono text-xs">Status</TableHead>
                  <TableHead className="font-mono text-xs text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {response?.cars?.map((car) => (
                  <TableRow key={car.id} className="border-border hover:bg-secondary/30">
                    <TableCell className="font-mono text-muted-foreground">#{car.id}</TableCell>
                    <TableCell>
                      <div className="font-medium text-foreground">{car.brand} {car.model}</div>
                      <div className="text-xs text-muted-foreground font-mono">{car.year} • {car.mileage.toLocaleString()} km</div>
                    </TableCell>
                    <TableCell className="font-mono text-xs uppercase">
                      {car.fuelType} • {car.transmission}
                    </TableCell>
                    <TableCell className="font-mono text-xs">{car.location}</TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`font-mono text-xs uppercase border ${
                        car.status === 'approved' ? 'bg-green-500/10 text-green-500 border-green-500/30' :
                        car.status === 'pending' ? 'bg-primary/10 text-primary border-primary/30' :
                        'bg-destructive/10 text-destructive border-destructive/30'
                      }`}>
                        {car.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      {car.status === 'pending' && (
                        <div className="flex items-center justify-end gap-2">
                          <Button 
                            variant="outline" 
                            size="sm" 
                            className="h-8 border-green-500/50 text-green-500 hover:bg-green-500/10 hover:text-green-500"
                            onClick={() => approveCar.mutate({ id: car.id })}
                            disabled={approveCar.isPending}
                          >
                            <CheckCircle className="h-4 w-4 mr-1" /> APPROVE
                          </Button>
                          <Button 
                            variant="outline" 
                            size="sm" 
                            className="h-8 border-destructive/50 text-destructive hover:bg-destructive/10 hover:text-destructive"
                            onClick={() => rejectCar.mutate({ id: car.id, data: { reason: "Admin rejected" } })}
                            disabled={rejectCar.isPending}
                          >
                            <XCircle className="h-4 w-4 mr-1" /> REJECT
                          </Button>
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {!response?.cars?.length && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground font-mono">
                      NO VEHICLES FOUND
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