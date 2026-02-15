package jp.saitama.orange.mycompass

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val useTrueNorth by settingsViewModel.useTrueNorth.collectAsState()
    val distanceUnit by settingsViewModel.distanceUnit.collectAsState()
    val strideLengthCm by settingsViewModel.strideLengthCm.collectAsState()
    val compassType by settingsViewModel.compassType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
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
                .padding(16.dp)
        ) {
            // Display Settings
            Text(
                text = stringResource(R.string.settings_display),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // North basis toggle: True North vs Magnetic North
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_true_north_label))
                            Text(
                                text = stringResource(R.string.settings_true_north_description),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useTrueNorth,
                            onCheckedChange = { settingsViewModel.setUseTrueNorth(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Compass Image Selection
                    Text(text = stringResource(R.string.settings_compass_type), fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(Modifier.selectableGroup()) {
                        listOf(
                            0 to stringResource(R.string.settings_compass_type01),
                            1 to stringResource(R.string.settings_compass_type02)
                        ).forEach { (index, label) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .selectable(
                                        selected = (compassType == index),
                                        onClick = { settingsViewModel.setCompassType(index) },
                                        role = Role.RadioButton
                                    )
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = (compassType == index),
                                    onClick = null // null because the row is selectable
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(text = stringResource(R.string.settings_distance_unit))
                    Spacer(modifier = Modifier.height(8.dp))

                    val unitTabs = listOf(
                        stringResource(R.string.settings_unit_meter),
                        stringResource(R.string.settings_unit_mile)
                    )
                    val selectedIndex = if (distanceUnit == "mile") 1 else 0

                    TabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        unitTabs.forEachIndexed { index, label ->
                            val isSelected = index == selectedIndex
                            Tab(
                                selected = isSelected,
                                onClick = {
                                    val unit = if (index == 0) "meter" else "mile"
                                    settingsViewModel.setDistanceUnit(unit)
                                },
                                text = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Stride length setting
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = stringResource(R.string.settings_stride_length))
                                Text(
                                    text = stringResource(R.string.settings_stride_length_description),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(R.string.settings_stride_length_value, strideLengthCm),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = strideLengthCm.toFloat(),
                            onValueChange = { settingsViewModel.setStrideLengthCm(it.toInt()) },
                            valueRange = 60f..80f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Data Management (reserved for future)
        }
    }
}
