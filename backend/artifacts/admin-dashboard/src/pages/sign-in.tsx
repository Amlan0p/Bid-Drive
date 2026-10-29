import { useState } from "react";
import { useLocation } from "wouter";
import { Gavel, Lock, Mail, Eye, EyeOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAuth } from "@/contexts/AuthContext";

export default function SignIn() {
  const { login } = useAuth();
  const [, setLocation] = useLocation();
  const [email, setEmail] = useState("");
  const [adminSecret, setAdminSecret] = useState("");
  const [showSecret, setShowSecret] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      await login(email.trim(), adminSecret);
      setLocation("/dashboard");
    } catch (err: any) {
      setError(err.message || "Invalid credentials. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[100dvh] flex items-center justify-center bg-background px-4 dark">
      <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_top,_var(--tw-gradient-stops))] from-primary/10 via-background to-background pointer-events-none" />

      <div className="relative w-full max-w-md">
        <div className="bg-card border border-border rounded-xl shadow-2xl overflow-hidden">
          <div className="px-8 pt-8 pb-6 border-b border-border">
            <div className="flex items-center gap-3 mb-1">
              <Gavel className="h-7 w-7 text-primary" />
              <span className="text-2xl font-extrabold tracking-tight text-foreground uppercase">
                BidDrive <span className="text-primary text-sm font-mono tracking-widest align-top">SYS</span>
              </span>
            </div>
            <p className="text-sm text-muted-foreground font-mono mt-2">ADMIN PORTAL — AUTHORIZED ACCESS ONLY</p>
          </div>

          <div className="px-8 py-4 bg-primary/5 border-b border-border">
            <p className="text-xs text-muted-foreground font-mono mb-2 uppercase tracking-wider">Demo Credentials</p>
            <div className="space-y-1 text-sm">
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Email</span>
                <span className="font-mono text-foreground select-all">admin@biddrive.in</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-muted-foreground">Secret</span>
                <span className="font-mono text-foreground select-all">BidDrive@Admin2024</span>
              </div>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="px-8 py-6 space-y-5">
            <div className="space-y-2">
              <Label htmlFor="email" className="text-foreground font-medium text-sm">
                Admin Email
              </Label>
              <div className="relative">
                <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
                <Input
                  id="email"
                  type="email"
                  placeholder="admin@example.com"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  className="pl-10 bg-input border-border text-foreground placeholder:text-muted-foreground focus-visible:ring-primary"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="adminSecret" className="text-foreground font-medium text-sm">
                Admin Secret
              </Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
                <Input
                  id="adminSecret"
                  type={showSecret ? "text" : "password"}
                  placeholder="Enter admin secret"
                  value={adminSecret}
                  onChange={(e) => setAdminSecret(e.target.value)}
                  required
                  className="pl-10 pr-10 bg-input border-border text-foreground placeholder:text-muted-foreground focus-visible:ring-primary"
                />
                <button
                  type="button"
                  onClick={() => setShowSecret(!showSecret)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                >
                  {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </div>

            {error && (
              <div className="rounded-md bg-destructive/10 border border-destructive/30 px-4 py-3 text-sm text-destructive">
                {error}
              </div>
            )}

            <Button
              type="submit"
              disabled={loading}
              className="w-full h-11 bg-primary text-primary-foreground hover:bg-primary/90 font-bold font-mono tracking-wider"
            >
              {loading ? (
                <span className="flex items-center gap-2">
                  <span className="h-4 w-4 rounded-full border-2 border-primary-foreground border-t-transparent animate-spin" />
                  AUTHENTICATING...
                </span>
              ) : (
                "ACCESS SYSTEM"
              )}
            </Button>
          </form>
        </div>
      </div>
    </div>
  );
}
