@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package ging.us.katfod

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.longdo.mjpegviewer.MjpegView
import ging.us.katfod.ui.theme.FeederControllerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeederControllerTheme {
                MainNavigation()
            }
        }
    }
}

// main navigation page for everything, rest based on top of it
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // init sharedpreferences
    val sharedPreferences = remember { context.getSharedPreferences("FeederPrefs", Context.MODE_PRIVATE)}

    // scope, snackbar, haptics
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // MJPEG URL, web server camera address is stored here
    var webServerIP by remember { mutableStateOf(sharedPreferences.getString("ip_address", "192.168.100.100") ?: "192.168.100.100")  }
    var webServerPort by remember { mutableStateOf(sharedPreferences.getString("port", "80") ?: "80")  }

    // vibration feedback toggle
    var vibrationEnabled by remember {
        mutableStateOf(sharedPreferences.getBoolean("vibration_enabled", true))
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            ShortNavigationBar {
                ShortNavigationBarItem(
                    selected = currentRoute == "feeder",
                    onClick = { navController.navigate("feeder") },
                    label = { Text("Feeder") },
                    icon = {
                        Icon(Icons.Default.Pets, contentDescription = null)
                    }
                )
                ShortNavigationBarItem(
                    selected = currentRoute == "settings",
                    onClick = { navController.navigate("settings") },
                    label = { Text("Settings") },
                    icon = {
                        Icon(Icons.Default.Settings, contentDescription = null)
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "feeder",
            modifier = Modifier.padding(innerPadding),
            //enterTransition = { fadeIn() },
            //exitTransition = { fadeOut() }
        ) {
            composable("feeder") {
                CatFeederScreen(
                    webServerIP = webServerIP,
                    webServerPort = webServerPort,
                    vibrationEnabled = vibrationEnabled,
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    haptics = haptics
                )
            }
            composable("settings") {
                SettingsScreen(
                    // the set ip
                    currentUrl = webServerIP,
                    // cleanse the ip after change
                    onUrlChange = { input ->
                        val cleaned = input
                            .replace("http://", "")
                            .replace("https://", "")
                            .trim()

                        webServerIP = cleaned
                        sharedPreferences.edit { putString("ip_address", cleaned) }
                        println("New URL: $webServerIP")
                    },
                    currentPort = webServerPort,
                    onPortChange = { input ->
                        webServerPort = input
                        sharedPreferences.edit { putString("port", input) }
                        println("New Port: $webServerPort")
                    },
                    vibrationEnabled = vibrationEnabled,
                    onVibrationChange = { enabled ->
                        vibrationEnabled = enabled
                        sharedPreferences.edit {
                            putBoolean("vibration_enabled", enabled)
                            apply()
                        }
                    },
                    scope = scope,
                    snackbarHostState = snackbarHostState,
                    haptics = haptics
                )
            }
        }
    }
}

//fun VibrateFeedback(){
//    if (vibrationEnabled)
//}
@Composable
fun CatFeederScreen(
    webServerIP: String,
    webServerPort: String,
    vibrationEnabled: Boolean,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.weight(0.5f))
            Text(
                "Feeder",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLargeEmphasized,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(64.dp))
            //Text(
            // "FPS: " + camFPS,
            // modifier = Modifier.fillMaxWidth(),
            // style = MaterialTheme.typography.bodyLargeEmphasized,
            //  textAlign = TextAlign.Start
            //       )

            // Integrated android-mjpeg-view
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 4.dp,
            ) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                    factory = { context ->
                        MjpegView(context).apply {
                            mode = MjpegView.MODE_FIT_WIDTH
                            isAdjustHeight = false
                            supportPinchZoomAndPan = true
                            // setUrl(videoUrl)
                            // startStream()
                        }
                    },
                    update = { view ->
                        // Restart stream if URL changed
                        val latestUrl = "http://$webServerIP:$webServerPort/video"
                        if (view.tag != latestUrl) {
                            println("changing MJPEG into $latestUrl")
                            if (view.tag != null) {
                                try {
                                    view.stopStream()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            view.setUrl(latestUrl)
                            view.startStream()
                            view.tag = latestUrl
                        }
                    },
                    onRelease = { view ->
                        try {
                            view.stopStream()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            ElevatedButton(
                onClick = {
                    triggerVibration(haptics, vibrationEnabled)
                    println("dispensing food...")

                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val loadingJob = launch {
                            snackbarHostState.showSnackbar("Dispensing...")
                        }
                        val code = performHttpRequest("http://$webServerIP:$webServerPort/dispense")

                        loadingJob.cancel()

                        when (code) {
                            200 -> snackbarHostState.showSnackbar("Food dispensed!")
                            429 -> snackbarHostState.showSnackbar("Too many dispenses. Calm down!")
                            404 -> snackbarHostState.showSnackbar("Wrong endpoint? Can't dispense.")
                            500 -> snackbarHostState.showSnackbar("Feeder error. Check Feeder hardware logs.")
                            null -> snackbarHostState.showSnackbar("Can't reach Feeder hardware... Check if hardware is active.")
                            else -> snackbarHostState.showSnackbar("Unexpected response: $code")
                        }
                    }
                },
                modifier = Modifier.size(280.dp),
                shape = CircleShape,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 8.dp,
                    pressedElevation = 2.dp
                ),


                ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Dispense!", style = MaterialTheme.typography.headlineMediumEmphasized)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SettingsScreen(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    currentPort: String,
    onPortChange: (String) -> Unit,
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    haptics: androidx.compose.ui.hapticfeedback.HapticFeedback
) {

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(64.dp))
            Text(
                "Settings",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineLargeEmphasized,
                textAlign = TextAlign.Start
            )
            Text(
                text = "Configure Feeder settings.",
                style = MaterialTheme.typography.bodyMediumEmphasized,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Local IP to microcontroller") },
                    modifier = Modifier.weight(0.7f),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = currentPort,
                    onValueChange = onPortChange,
                    label = { Text("Port for IP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.3f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Vibration", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = vibrationEnabled,
                    onCheckedChange = {
                        triggerVibration(haptics, it)
                        onVibrationChange(it)
                                      },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ElevatedButton(
                    onClick = {
                        triggerVibration(haptics, vibrationEnabled)
                        println("opening dispenser door...")
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val loadingJob = launch {
                                snackbarHostState.showSnackbar("Opening door...")
                            }

                            val code = withContext(Dispatchers.IO) {performHttpRequest("http://$currentUrl:$currentPort/open_servo")}

                            loadingJob.cancel()

                            when (code) {
                                200 -> snackbarHostState.showSnackbar("Door opened!")
                                null -> snackbarHostState.showSnackbar("Feeder hardware unreachable. Check if hardware is active.")
                                else -> snackbarHostState.showSnackbar("Error: $code")
                            }
                        }
                    },
                    modifier = Modifier.weight(0.5f),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 2.dp
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Open Door", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                ElevatedButton(
                    onClick = {
                        triggerVibration(haptics, vibrationEnabled)
                        println("closing dispenser door...")
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val loadingJob = launch {
                                snackbarHostState.showSnackbar("Closing door...")
                            }

                            val code = withContext(Dispatchers.IO) {performHttpRequest("http://$currentUrl:$currentPort/close_servo")}

                            loadingJob.cancel()

                            when (code) {
                                200 -> snackbarHostState.showSnackbar("Door opened!")
                                null -> snackbarHostState.showSnackbar("Feeder hardware unreachable. Check if hardware is active.")
                                else -> snackbarHostState.showSnackbar("Error: $code")
                            }
                        }
                    },
                    modifier = Modifier.weight(0.5f),
                    shape = CircleShape,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    elevation = ButtonDefaults.elevatedButtonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 2.dp
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Close Door", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                Image(
                    painter = painterResource(id = R.drawable.gingus512),
                    contentDescription = "Car",
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "by gingus Scringus, for PP. (c) 2026",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(start = 8.dp)
                )
                }
            }
        }
    }
}

// helper functions
fun triggerVibration(haptics: HapticFeedback, enabled: Boolean, type: HapticFeedbackType = HapticFeedbackType.LongPress) {
    if (enabled) {
        haptics.performHapticFeedback(type)
    }
}

suspend fun performHttpRequest(url: String): Int? {
    return withContext(Dispatchers.IO) {
        try {
            println("attempting to connect to $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.connect()
            val responseCode = conn.responseCode
            conn.disconnect()
            return@withContext responseCode
        } catch (e: Exception) {
            println("error connecting! ${e.message}")
            null
        }
    }
}

// @Preview(showBackground = true)
@Composable
fun CatfedPreview() {
    FeederControllerTheme {
        MainNavigation()
    }
}

// @Preview(showBackground = true)
@Composable
fun FeederPreview() {
    FeederControllerTheme {
        CatFeederScreen(
            webServerIP = "192.168.100.69",
            webServerPort = "80",
            vibrationEnabled = true,
            scope = rememberCoroutineScope(),
            snackbarHostState = remember { SnackbarHostState() },
            haptics = LocalHapticFeedback.current,
            )
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    FeederControllerTheme {
        SettingsScreen(
            currentUrl = "192.168.100.69",
            onUrlChange = {},
            currentPort = "80",
            onPortChange = {},
            vibrationEnabled = true,
            onVibrationChange = {},
            scope = rememberCoroutineScope(),
            snackbarHostState = remember { SnackbarHostState() },
            haptics = LocalHapticFeedback.current
        )
    }
}
