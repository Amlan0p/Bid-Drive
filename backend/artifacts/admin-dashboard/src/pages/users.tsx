import { useState } from "react";
import { useListUsers, useBanUser, useUnbanUser, useUpdateUser } from "@workspace/api-client-react";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Search, Filter, ShieldAlert, ShieldCheck } from "lucide-react";
import { useToast } from "@/hooks/use-toast";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useQueryClient } from "@tanstack/react-query";
import { getListUsersQueryKey } from "@workspace/api-client-react";

export default function Users() {
  const [roleFilter, setRoleFilter] = useState<string>("all");
  const { toast } = useToast();
  const queryClient = useQueryClient();

  const { data: response, isLoading } = useListUsers(
    roleFilter !== "all" ? { role: roleFilter as any } : {}
  );
  
  const banUser = useBanUser({
    mutation: {
      onSuccess: () => {
        toast({ title: "User banned" });
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      }
    }
  });

  const unbanUser = useUnbanUser({
    mutation: {
      onSuccess: () => {
        toast({ title: "User unbanned" });
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      }
    }
  });

  const updateUser = useUpdateUser({
    mutation: {
      onSuccess: () => {
        toast({ title: "Role updated" });
        queryClient.invalidateQueries({ queryKey: getListUsersQueryKey() });
      }
    }
  });

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight font-mono uppercase">User Registry</h1>
      </div>

      <Card className="border-border">
        <CardHeader className="py-4 border-b border-border bg-secondary/30">
          <div className="flex items-center gap-4">
            <div className="relative flex-1 max-w-sm">
              <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input placeholder="Search users by email..." className="pl-9 font-mono text-sm bg-background" />
            </div>
            <Select value={roleFilter} onValueChange={setRoleFilter}>
              <SelectTrigger className="w-[180px] font-mono text-sm">
                <div className="flex items-center gap-2"><Filter className="h-4 w-4" /> <SelectValue placeholder="Role" /></div>
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">ALL ROLES</SelectItem>
                <SelectItem value="customer">CUSTOMER</SelectItem>
                <SelectItem value="dealer">DEALER</SelectItem>
                <SelectItem value="admin">ADMIN</SelectItem>
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
                  <TableHead className="font-mono text-xs">ID / Email</TableHead>
                  <TableHead className="font-mono text-xs">Name</TableHead>
                  <TableHead className="font-mono text-xs">Role</TableHead>
                  <TableHead className="font-mono text-xs">Status</TableHead>
                  <TableHead className="font-mono text-xs">Joined</TableHead>
                  <TableHead className="font-mono text-xs text-right">Access Control</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {response?.users?.map((user) => (
                  <TableRow key={user.id} className="border-border hover:bg-secondary/30">
                    <TableCell>
                      <div className="font-mono text-foreground text-sm">{user.email}</div>
                      <div className="text-xs text-muted-foreground font-mono">UID: {user.id}</div>
                    </TableCell>
                    <TableCell className="font-medium text-foreground">{user.name}</TableCell>
                    <TableCell>
                      <Select 
                        value={user.role} 
                        onValueChange={(val: any) => updateUser.mutate({ id: user.id, data: { role: val } })}
                      >
                        <SelectTrigger className="w-[120px] h-8 font-mono text-xs uppercase">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="customer">CUSTOMER</SelectItem>
                          <SelectItem value="dealer">DEALER</SelectItem>
                          <SelectItem value="admin">ADMIN</SelectItem>
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell>
                      <Badge variant="outline" className={`font-mono text-xs uppercase border ${
                        user.status === 'active' ? 'bg-green-500/10 text-green-500 border-green-500/30' :
                        'bg-destructive/10 text-destructive border-destructive/30'
                      }`}>
                        {user.status}
                      </Badge>
                    </TableCell>
                    <TableCell className="font-mono text-xs text-muted-foreground">
                      {new Date(user.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right">
                      {user.status === 'active' ? (
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          className="h-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => banUser.mutate({ id: user.id })}
                          disabled={banUser.isPending}
                        >
                          <ShieldAlert className="h-4 w-4 mr-1" /> BAN
                        </Button>
                      ) : (
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          className="h-8 text-green-500 hover:bg-green-500/10 hover:text-green-500"
                          onClick={() => unbanUser.mutate({ id: user.id })}
                          disabled={unbanUser.isPending}
                        >
                          <ShieldCheck className="h-4 w-4 mr-1" /> UNBAN
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {!response?.users?.length && (
                  <TableRow>
                    <TableCell colSpan={6} className="text-center py-8 text-muted-foreground font-mono">
                      NO USERS FOUND
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