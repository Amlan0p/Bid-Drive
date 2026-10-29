import { Link, useLocation } from "wouter";
import { useAuth } from "@/contexts/AuthContext";
import {
  LayoutDashboard,
  Gavel,
  CarFront,
  Users,
  CreditCard,
  LineChart,
  LogOut,
  Menu,
  Bell
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { useState, useEffect } from "react";
import { useLocation as useWouterLocation } from "wouter";

export function Layout({ children }: { children: React.ReactNode }) {
  const [location] = useWouterLocation();
  const { user, logout } = useAuth();
  const basePath = import.meta.env.BASE_URL.replace(/\/$/, "");

  useEffect(() => {
    document.documentElement.classList.add("dark");
  }, []);

  const navItems = [
    { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
    { href: "/auctions", label: "Live Auctions", icon: Gavel },
    { href: "/cars", label: "Car Listings", icon: CarFront },
    { href: "/users", label: "Users", icon: Users },
    { href: "/payments", label: "Payments", icon: CreditCard },
    { href: "/analytics", label: "Analytics", icon: LineChart },
  ];

  const handleSignOut = () => {
    logout();
  };

  const SidebarContent = () => (
    <div className="flex h-full flex-col bg-card border-r border-border">
      <div className="flex h-16 items-center px-6 border-b border-border">
        <Link href="/dashboard" className="flex items-center gap-2">
          <Gavel className="h-6 w-6 text-primary" />
          <span className="text-xl font-bold tracking-tight text-foreground uppercase">BidDrive <span className="text-primary text-sm font-mono tracking-widest align-top">SYS</span></span>
        </Link>
      </div>
      <div className="flex-1 overflow-y-auto py-4">
        <nav className="space-y-1 px-3">
          {navItems.map((item) => {
            const isActive = location === item.href || location.startsWith(`${item.href}/`);
            return (
              <Link key={item.href} href={item.href}>
                <Button
                  variant="ghost"
                  className={`w-full justify-start gap-3 rounded-md px-3 py-2 text-sm font-medium transition-all ${
                    isActive
                      ? "bg-secondary text-primary border-l-2 border-primary rounded-l-none"
                      : "text-muted-foreground hover:bg-secondary hover:text-foreground"
                  }`}
                >
                  <item.icon className={`h-4 w-4 ${isActive ? "text-primary" : ""}`} />
                  {item.label}
                </Button>
              </Link>
            );
          })}
        </nav>
      </div>
      <div className="border-t border-border p-4">
        <div className="flex items-center gap-3 mb-4">
          <Avatar className="h-9 w-9 border border-border">
            <AvatarImage src={user?.avatarUrl ?? undefined} />
            <AvatarFallback className="bg-secondary text-secondary-foreground font-mono">
              {user?.name?.charAt(0)?.toUpperCase() || "A"}
            </AvatarFallback>
          </Avatar>
          <div className="flex flex-col flex-1 min-w-0">
            <span className="text-sm font-medium text-foreground truncate">{user?.name || user?.email}</span>
            <span className="text-xs text-muted-foreground font-mono">ADMIN</span>
          </div>
        </div>
        <Button
          variant="outline"
          className="w-full justify-start gap-2 border-border hover:bg-destructive/10 hover:text-destructive hover:border-destructive/30 transition-colors"
          onClick={handleSignOut}
        >
          <LogOut className="h-4 w-4" />
          Terminate Session
        </Button>
      </div>
    </div>
  );

  return (
    <div className="flex min-h-[100dvh] w-full bg-background selection:bg-primary/30 selection:text-primary">
      <aside className="hidden w-64 flex-col md:flex fixed inset-y-0 z-50">
        <SidebarContent />
      </aside>

      <div className="flex flex-1 flex-col md:pl-64">
        <header className="sticky top-0 z-40 flex h-16 items-center justify-between border-b border-border bg-card/80 backdrop-blur-md px-4 md:px-6">
          <div className="flex items-center gap-4 md:hidden">
            <Sheet>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="md:hidden">
                  <Menu className="h-5 w-5" />
                  <span className="sr-only">Toggle navigation menu</span>
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-64 p-0 border-r border-border">
                <SidebarContent />
              </SheetContent>
            </Sheet>
            <Link href="/dashboard" className="flex items-center gap-2">
              <Gavel className="h-5 w-5 text-primary" />
              <span className="text-lg font-bold tracking-tight text-foreground uppercase">BidDrive</span>
            </Link>
          </div>

          <div className="hidden md:flex items-center text-sm font-mono text-muted-foreground">
            SYSTEM STATUS: <span className="text-green-500 ml-2 flex items-center gap-1"><span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span> ONLINE</span>
          </div>

          <div className="flex items-center gap-4 ml-auto">
            <Button variant="ghost" size="icon" className="relative text-muted-foreground hover:text-foreground">
              <Bell className="h-5 w-5" />
              <span className="absolute top-1.5 right-1.5 h-2 w-2 rounded-full bg-primary ring-2 ring-card"></span>
            </Button>
          </div>
        </header>

        <main className="flex-1 overflow-x-hidden p-4 md:p-6 lg:p-8">
          <div className="mx-auto max-w-7xl w-full">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
