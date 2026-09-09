package com.anan.dfg

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.anan.dfg.schedule.Notifier
import com.anan.dfg.schedule.ReminderScheduler
import com.anan.dfg.ui.screen.HomeScreen
import com.anan.dfg.ui.screen.ItemDetailScreen
import com.anan.dfg.ui.screen.ItemEditScreen
import com.anan.dfg.ui.screen.PhotoManagerScreen
import com.anan.dfg.ui.theme.DfgTheme

class MainActivity : ComponentActivity() {

    /** Item id carried in by a notification; a second tap arrives via onNewIntent. */
    private val pendingItemId = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingItemId.value = intent?.getLongExtra(Notifier.EXTRA_ITEM_ID, 0L) ?: 0L
        setContent {
            DfgTheme {
                DfgNavHost(
                    requestedItemId = pendingItemId.value,
                    onRequestConsumed = { pendingItemId.value = 0L },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingItemId.value = intent.getLongExtra(Notifier.EXTRA_ITEM_ID, 0L)
    }
}

private const val ROUTE_HOME = "home"
private const val ROUTE_DETAIL = "item"
private const val ROUTE_EDIT = "edit"
private const val ROUTE_PHOTOS = "photos"

@Composable
private fun DfgNavHost(requestedItemId: Long, onRequestConsumed: () -> Unit) {
    val navController = rememberNavController()
    val context = LocalContext.current

    var notificationsAllowed by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    var exactAlarmAllowed by remember {
        mutableStateOf(ReminderScheduler.canScheduleExact(context))
    }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsAllowed = granted }

    // Ask up front, otherwise reminders fail silently.
    LaunchedEffect(Unit) {
        if (!notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Re-check after returning from a system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAlarmAllowed = ReminderScheduler.canScheduleExact(context)
                notificationsAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Open the item a notification points at, then clear it so we don't navigate twice.
    LaunchedEffect(requestedItemId) {
        if (requestedItemId > 0) {
            navController.navigate("$ROUTE_DETAIL/$requestedItemId")
            onRequestConsumed()
        }
    }

    NavHost(navController = navController, startDestination = ROUTE_HOME) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onOpenItem = { navController.navigate("$ROUTE_DETAIL/$it") },
                onCreateItem = { navController.navigate("$ROUTE_EDIT/0") },
                exactAlarmAllowed = exactAlarmAllowed,
                onFixExactAlarm = { context.openExactAlarmSettings() },
                notificationsAllowed = notificationsAllowed,
                onFixNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openAppSettings()
                    }
                },
            )
        }
        composable(
            "$ROUTE_DETAIL/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("itemId") ?: 0L
            ItemDetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() },
                onEditItem = { navController.navigate("$ROUTE_EDIT/$id") },
                onManagePhotos = { navController.navigate("$ROUTE_PHOTOS/$id") },
            )
        }
        composable(
            "$ROUTE_EDIT/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("itemId") ?: 0L
            ItemEditScreen(
                itemId = id,
                onBack = { navController.popBackStack() },
                onDeleted = {
                    navController.popBackStack(ROUTE_HOME, inclusive = false)
                },
            )
        }
        composable(
            "$ROUTE_PHOTOS/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            PhotoManagerScreen(
                itemId = entry.arguments?.getLong("itemId") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun android.content.Context.openExactAlarmSettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))
    } else {
        appSettingsIntent()
    }
    runCatching { startActivity(intent) }.onFailure { startActivity(appSettingsIntent()) }
}

private fun android.content.Context.openAppSettings() {
    runCatching { startActivity(appSettingsIntent()) }
}

private fun android.content.Context.appSettingsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
