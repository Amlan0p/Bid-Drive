package app.biddrive.android.ui.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.biddrive.android.BidDriveApp
import app.biddrive.android.R
import app.biddrive.android.data.SessionState
import app.biddrive.android.ui.LocalSnackbar
import app.biddrive.android.ui.auth.SignInScreen
import app.biddrive.android.ui.auth.SignUpScreen
import app.biddrive.android.ui.auth.WelcomeScreen
import app.biddrive.android.ui.bid.BidConfirmationScreen
import app.biddrive.android.ui.details.AuctionDetailsScreen
import app.biddrive.android.ui.explore.ExploreScreen
import app.biddrive.android.ui.home.HomeScreen
import app.biddrive.android.ui.mybids.MyBidsScreen
import app.biddrive.android.ui.profile.ProfileScreen
import app.biddrive.android.ui.search.SearchFiltersScreen
import app.biddrive.android.ui.theme.Bd
import app.biddrive.android.ui.watchlist.WatchlistScreen

object Route {
    const val Welcome = "welcome"
    const val SignIn = "signin"
    const val SignUp = "signup"
    const val Home = "home"
    const val Explore = "explore"
    const val MyBids = "mybids"
    const val Watchlist = "watchlist"
    const val Profile = "profile"
    const val Search = "search"
    const val Details = "details/{auctionId}"
    const val Confirm = "confirm/{auctionId}/{amount}"
    fun details(id: Int) = "details/$id"
    fun confirm(id: Int, amount: Long) = "confirm/$id/$amount"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, "Home", Icons.Filled.Home),
    Tab(Route.Explore, "Explore", Icons.Filled.Explore),
    Tab(Route.MyBids, "My Bids", Icons.Filled.Gavel),
    Tab(Route.Watchlist, "Watchlist", Icons.Filled.Bookmark),
    Tab(Route.Profile, "Profile", Icons.Filled.Person),
)
private val authRoutes = setOf(Route.Welcome, Route.SignIn, Route.SignUp)

@Composable
fun BidDriveNavHost() {
    val app = LocalContext.current.applicationContext as BidDriveApp
    val container = app.container
    val session by container.session.state.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }

    // Wait for the persisted session to be read before choosing the first screen.
    val welcomeSeen by container.settings.welcomeSeen.collectAsState()
    if (session is SessionState.Loading || welcomeSeen == null) {
        Box(Modifier.fillMaxSize().background(Bd.Background), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.biddrive_logo), "BidDrive", Modifier.size(96.dp))
        }
        return
    }
    val start = remember { if (session is SessionState.SignedIn) Route.Home else if (welcomeSeen == true) Route.SignIn else Route.Welcome }

    // Auth gate: signing out (or an expired token) always returns to Sign In; signing in leaves the auth screens.
    val signedIn = session is SessionState.SignedIn
    LaunchedEffect(signedIn) {
        val current = nav.currentBackStackEntry?.destination?.route
        if (signedIn) {
            container.watchlist.load()
            if (current in authRoutes) nav.navigate(Route.Home) { popUpTo(nav.graph.id) { inclusive = true } }
        } else {
            container.watchlist.clear()
            if (current !in authRoutes) nav.navigate(Route.SignIn) { popUpTo(nav.graph.id) { inclusive = true } }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Scaffold(
            containerColor = Bd.Background,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { if (route in tabs.map { it.route }) BottomBar(nav, route) },
        ) { padding ->
            AppNavGraph(nav, start, padding, onWelcomeSeen = { container.settings.markWelcomeSeen() })
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = Bd.SurfaceLow, contentColor = Bd.OnSurface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, tab.label) },
                label = { Text(tab.label, style = Bd.LabelCaps) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Bd.OnAmber, selectedTextColor = Bd.Amber, indicatorColor = Bd.Amber,
                    unselectedIconColor = Bd.Outline, unselectedTextColor = Bd.Outline,
                ),
            )
        }
    }
}

@Composable
private fun AppNavGraph(nav: NavHostController, start: String, padding: PaddingValues, onWelcomeSeen: () -> Unit) {
    val openDetails: (Int) -> Unit = { nav.navigate(Route.details(it)) }
    NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
        composable(Route.Welcome) {
            WelcomeScreen(
                onCreateAccount = { onWelcomeSeen(); nav.navigate(Route.SignUp) },
                onSignIn = { onWelcomeSeen(); nav.navigate(Route.SignIn) },
            )
        }
        composable(Route.SignIn) {
            SignInScreen(
                onSignUp = { nav.navigate(Route.SignUp) },
                onBackToWelcome = {
                    nav.navigate(Route.Welcome) {
                        popUpTo(Route.SignIn) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.SignUp) {
            SignUpScreen(
                onBackToSignIn = { nav.navigate(Route.SignIn) { popUpTo(Route.SignUp) { inclusive = true } } },
                onBackToWelcome = {
                    nav.navigate(Route.Welcome) {
                        popUpTo(Route.SignUp) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.Home) {
            HomeScreen(onOpenAuction = openDetails, onOpenSearch = { nav.navigate(Route.Search) }, onSeeAll = { nav.navigate(Route.Explore) })
        }
        composable(Route.Explore) { ExploreScreen(onOpenAuction = openDetails, onOpenFilters = { nav.navigate(Route.Search) }) }
        composable(Route.MyBids) { MyBidsScreen(onOpenAuction = openDetails, onExplore = { nav.navigate(Route.Explore) }) }
        composable(Route.Watchlist) { WatchlistScreen(onOpenAuction = openDetails, onExplore = { nav.navigate(Route.Explore) }) }
        composable(Route.Profile) { ProfileScreen() }
        composable(Route.Search) {
            SearchFiltersScreen(
                onBack = { nav.popBackStack() },
                onApplied = {
                    nav.navigate(Route.Explore) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = false }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Route.Details, arguments = listOf(navArgument("auctionId") { type = NavType.IntType })) { entry: NavBackStackEntry ->
            AuctionDetailsScreen(
                onBack = { nav.popBackStack() },
                onPlaceBid = { id, amount -> nav.navigate(Route.confirm(id, amount)) },
            )
        }
        composable(
            Route.Confirm,
            arguments = listOf(navArgument("auctionId") { type = NavType.IntType }, navArgument("amount") { type = NavType.LongType }),
        ) {
            BidConfirmationScreen(
                onBack = { nav.popBackStack() },
                onViewMyBids = { nav.navigate(Route.MyBids) { popUpTo(Route.Home) } },
            )
        }
    }
}
