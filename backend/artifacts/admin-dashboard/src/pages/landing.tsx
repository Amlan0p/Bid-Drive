import { Link } from "wouter";
import { Gavel, ArrowRight, ShieldCheck, Zap, BarChart3 } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function Landing() {
  const basePath = import.meta.env.BASE_URL.replace(/\/$/, "");

  return (
    <div className="min-h-[100dvh] flex flex-col bg-background selection:bg-primary/30 selection:text-primary dark">
      {/* Header */}
      <header className="border-b border-border/40 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Gavel className="h-6 w-6 text-primary" />
            <span className="text-xl font-bold tracking-tight text-foreground uppercase">BidDrive <span className="text-primary text-sm font-mono tracking-widest align-top">SYS</span></span>
          </div>
          <Link href={`${basePath}/sign-in`}>
            <Button variant="outline" className="border-border hover:bg-secondary font-mono text-sm">
              INITIATE LOGIN
            </Button>
          </Link>
        </div>
      </header>

      {/* Hero */}
      <main className="flex-1 flex flex-col items-center justify-center px-4 relative overflow-hidden">
        {/* Background effects */}
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-background to-background -z-10" />
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[400px] bg-primary/5 blur-[120px] rounded-full pointer-events-none" />
        
        <div className="max-w-3xl text-center space-y-8 relative z-10">
          <div className="inline-flex items-center rounded-full border border-primary/30 bg-primary/10 px-3 py-1 text-sm font-mono text-primary backdrop-blur-sm">
            <span className="flex h-2 w-2 rounded-full bg-primary mr-2 animate-pulse"></span>
            SECURE ADMIN PORTAL
          </div>
          
          <h1 className="text-5xl md:text-7xl font-extrabold tracking-tight text-foreground uppercase">
            Mission Control <br/>
            <span className="text-transparent bg-clip-text bg-gradient-to-r from-primary to-amber-200">For Auctions</span>
          </h1>
          
          <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
            High-density operational dashboard for real-time auction monitoring, vehicle approvals, and revenue analytics.
          </p>
          
          <div className="flex flex-col sm:flex-row items-center justify-center gap-4 pt-8">
            <Link href={`${basePath}/sign-in`}>
              <Button size="lg" className="h-12 px-8 text-base font-bold bg-primary text-primary-foreground hover:bg-primary/90 group">
                ACCESS SYSTEM
                <ArrowRight className="ml-2 h-4 w-4 group-hover:translate-x-1 transition-transform" />
              </Button>
            </Link>
          </div>
        </div>

        {/* Features grid */}
        <div className="grid md:grid-cols-3 gap-6 max-w-5xl mx-auto w-full mt-24 relative z-10">
          <div className="p-6 rounded-xl border border-border bg-card/50 backdrop-blur-sm">
            <Zap className="h-10 w-10 text-primary mb-4" />
            <h3 className="text-lg font-bold text-foreground mb-2 font-mono uppercase">Real-time Telemetry</h3>
            <p className="text-muted-foreground text-sm">Monitor live bids, active users, and auction countdowns with sub-second latency.</p>
          </div>
          <div className="p-6 rounded-xl border border-border bg-card/50 backdrop-blur-sm">
            <ShieldCheck className="h-10 w-10 text-primary mb-4" />
            <h3 className="text-lg font-bold text-foreground mb-2 font-mono uppercase">Listing Authority</h3>
            <p className="text-muted-foreground text-sm">Review, approve, and reject vehicle listings with integrated reports and imagery.</p>
          </div>
          <div className="p-6 rounded-xl border border-border bg-card/50 backdrop-blur-sm">
            <BarChart3 className="h-10 w-10 text-primary mb-4" />
            <h3 className="text-lg font-bold text-foreground mb-2 font-mono uppercase">Revenue Analytics</h3>
            <p className="text-muted-foreground text-sm">Track platform velocity, payment intents, and overall financial performance.</p>
          </div>
        </div>
      </main>
    </div>
  );
}