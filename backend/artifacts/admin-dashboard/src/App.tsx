import { Switch, Route, useLocation, Router as WouterRouter, Redirect } from 'wouter';
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Toaster } from "@/components/ui/toaster";
import { TooltipProvider } from "@/components/ui/tooltip";
import { AuthProvider, useAuth } from "@/contexts/AuthContext";
import NotFound from "@/pages/not-found";
import { Layout } from "@/components/layout";

import Landing from "@/pages/landing";
import SignIn from "@/pages/sign-in";
import Dashboard from "@/pages/dashboard";
import Auctions from "@/pages/auctions";
import AuctionDetail from "@/pages/auctions/detail";
import Cars from "@/pages/cars";
import Users from "@/pages/users";
import Payments from "@/pages/payments";
import Analytics from "@/pages/analytics";

const queryClient = new QueryClient();

const basePath = import.meta.env.BASE_URL.replace(/\/$/, "");

function HomeRedirect() {
  const { isSignedIn, isLoaded } = useAuth();
  if (!isLoaded) return null;
  return isSignedIn ? <Redirect to="/dashboard" /> : <Landing />;
}

function ProtectedRoute({ component: Component }: { component: React.ComponentType }) {
  const { isSignedIn, isLoaded } = useAuth();

  if (!isLoaded) {
    return (
      <div className="min-h-[100dvh] bg-background flex items-center justify-center">
        <div className="w-8 h-8 rounded-full border-4 border-primary border-t-transparent animate-spin" />
      </div>
    );
  }

  if (!isSignedIn) return <Redirect to="/sign-in" />;

  return (
    <Layout>
      <Component />
    </Layout>
  );
}

function AppRoutes() {
  return (
    <Switch>
      <Route path="/" component={HomeRedirect} />
      <Route path="/sign-in" component={SignIn} />

      <Route path="/dashboard" component={() => <ProtectedRoute component={Dashboard} />} />
      <Route path="/auctions" component={() => <ProtectedRoute component={Auctions} />} />
      <Route path="/auctions/:id" component={() => <ProtectedRoute component={AuctionDetail} />} />
      <Route path="/cars" component={() => <ProtectedRoute component={Cars} />} />
      <Route path="/users" component={() => <ProtectedRoute component={Users} />} />
      <Route path="/payments" component={() => <ProtectedRoute component={Payments} />} />
      <Route path="/analytics" component={() => <ProtectedRoute component={Analytics} />} />

      <Route component={NotFound} />
    </Switch>
  );
}

function App() {
  return (
    <WouterRouter base={basePath}>
      <AuthProvider>
        <QueryClientProvider client={queryClient}>
          <TooltipProvider>
            <AppRoutes />
            <Toaster />
          </TooltipProvider>
        </QueryClientProvider>
      </AuthProvider>
    </WouterRouter>
  );
}

export default App;
