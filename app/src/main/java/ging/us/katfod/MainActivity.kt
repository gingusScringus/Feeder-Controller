@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package ging.us.katfod

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ElevatedButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.longdo.mjpegviewer.MjpegView

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

import ging.us.katfod.ui.theme.FeederControllerTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext


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

    // MJPEG URL, web server camera address is stored here
    var webServerIP by remember { mutableStateOf("192.168.100.100") }
    var webServerPort by remember { mutableStateOf("80") }

    // vibration feedback toggle
    //var vibrationEnabled by remember { mutableStateOf(true) }

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
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() }
        ) {
            composable("feeder") { 
                CatFeederScreen(webServerIP = webServerIP, webServerPort = webServerPort)
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
                        println("New URL: $webServerIP")
                    },
                    currentPort = webServerPort,
                    onPortChange = { webServerPort = it }
                ) 
            }
        }
    }
}

//fun VibrateFeedback(){
//    if (vibrationEnabled)
//}
@Composable
fun CatFeederScreen(webServerIP: String, webServerPort: String) {
    val currentIP by rememberUpdatedState(webServerIP)
    val currentPort by rememberUpdatedState(webServerPort)

    val haptics = LocalHapticFeedback.current
    // val camFPS =
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    // val videoUrl = "http://$webServerIP:$webServerPort/video"
    // val dispenseUrl = "http://$webServerIP:$webServerPort/dispense"

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
                            view.stopStream()
                            view.setUrl(latestUrl)
                            view.startStream()
                            view.tag = latestUrl
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            ElevatedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    println("dispensing food...")

                    val targetIP = currentIP
                    val targetPort = currentPort
                    val requestUrl = "http://$webServerIP:$webServerPort/dispense"

                    scope.launch {
                        // optimistic UI feedback
                        snackbarHostState.showSnackbar("sending dispense command…")

                        val success = withContext(Dispatchers.IO) {
                            try {
                                println("attempting to connect to $requestUrl")
                                val conn = URL(requestUrl).openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.connectTimeout = 2000
                                // conn.readTimeout = 2000
                                conn.connect()

                                val responseCode = conn.responseCode
                                println("response code got $responseCode")

                                conn.inputStream.use { it.readBytes() }
                                conn.disconnect()
                                responseCode == 200
                            } catch (e: Exception) {
                                println("error connecting! ${e.message}")
                                false
                            }
                        }

                        if (success) {
                            snackbarHostState.showSnackbar("Food dispensed!")
                        } else {
                            snackbarHostState.showSnackbar("Feeder can't be reached...")
                        }
                    }
                },
                modifier = Modifier
                    .width(280.dp)
                    .height(280.dp),
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
    onPortChange: (String) -> Unit
) {

    var vibrationEnabled by remember { mutableStateOf(true) }


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
        Spacer(modifier = Modifier.height(32.dp))

        // ExposedDropdownMenuBox(){}

        Spacer(modifier = Modifier.height(16.dp))

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
                onCheckedChange = { vibrationEnabled = it },
            )
        }
    }
}

@Preview(showBackground = true)
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
        CatFeederScreen(webServerIP = "192.168.100.69", webServerPort = "80")
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    FeederControllerTheme {
        SettingsScreen(currentUrl = "192.168.100.69", onUrlChange = {}, currentPort = "80", onPortChange = {})
    }
}
