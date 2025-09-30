package jp.saitama.orange.mycompass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import jp.saitama.orange.mycompass.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CompassApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompassApp(
    viewModel: CompassViewModel = viewModel()
) {
    val azimuth by viewModel.azimuth.collectAsState()
    val sensorAvailable by viewModel.sensorAvailable.collectAsState()

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose {
            viewModel.stopListening()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compass App") },
                actions = {
                    IconButton(onClick = { /* TODO: Open map */ }) {
                        Icon(
                            imageVector = Icons.Default.Map,
                            contentDescription = "Open Map"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (!sensorAvailable) {
                Text(
                    text = "Sensor not available on this device",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(32.dp))
                CompassMeter(azimuth = azimuth)
            }
        }
    }
}