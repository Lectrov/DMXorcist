package net.seb.dmxorcist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.seb.dmxorcist.BuildConfig
import net.seb.dmxorcist.FixtureProfile
import net.seb.dmxorcist.TestColor
import net.seb.dmxorcist.TestMode
import net.seb.dmxorcist.TesterViewModel
import net.seb.dmxorcist.dmx.DriverMode
import net.seb.dmxorcist.dmx.OutputTarget

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TesterScreen(vm: TesterViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DMXorcist", style = MaterialTheme.typography.headlineSmall)

        // ------------------------------------------------------- interface
        Section("Interface") {
            Text(status.deviceName ?: "no device", fontWeight = FontWeight.Medium)
            if (status.vendorId != 0) {
                Text(
                    "VID 0x%04X  PID 0x%04X".format(status.vendorId, status.productId),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            status.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Art-Net failure: reported, but the wired output keeps running.
            status.artNetError?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Dropdown(
                label = "Output",
                options = OutputTarget.entries.map { it.label },
                selectedIndex = OutputTarget.entries.indexOf(state.target),
                onSelect = { i -> vm.update { it.copy(target = OutputTarget.entries[i]) } },
            )

            if (state.target != OutputTarget.ARTNET) {
                Dropdown(
                    label = "Dongle type",
                    options = DriverMode.entries.map { it.label },
                    selectedIndex = DriverMode.entries.indexOf(state.driverMode),
                    onSelect = { i -> vm.update { it.copy(driverMode = DriverMode.entries[i]) } },
                )
                Text(
                    state.driverMode.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.target != OutputTarget.USB) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "Art-Net universe",
                        value = state.artNetUniverse,
                        range = 0..255,
                        modifier = Modifier.weight(1f),
                        onChange = { v -> vm.update { it.copy(artNetUniverse = v) } },
                    )
                    OutlinedTextField(
                        value = state.artNetTarget,
                        onValueChange = { v -> vm.update { it.copy(artNetTarget = v) } },
                        label = { Text("Destination") },
                        singleLine = true,
                        modifier = Modifier.weight(1.4f),
                    )
                }
                Text(
                    "Universe ${state.artNetUniverse} in the protocol is usually displayed as " +
                        "${state.artNetUniverse + 1} on a console. This is the most common source of mismatch.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.connected) {
                    Button(onClick = { vm.disconnect() }) { Text("Disconnect") }
                    Text(
                        "${status.framesPerSecond} frames/s",
                        modifier = Modifier.align(Alignment.CenterVertically),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Button(onClick = { vm.connect() }) { Text("Connect") }
                    OutlinedButton(onClick = { vm.refresh() }) { Text("Scan") }
                }
            }
        }

        // ----------------------------------------------------------- patch
        Section("Patch") {
            Dropdown(
                label = "Fixture type",
                options = FixtureProfile.PRESETS.map { it.label },
                selectedIndex = FixtureProfile.PRESETS.indexOfFirst {
                    it.label == state.patch.profile.label
                },
                onSelect = { i ->
                    vm.update { s -> s.copy(patch = s.patch.copy(profile = FixtureProfile.PRESETS[i])) }
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "Start address",
                    value = state.patch.startAddress,
                    range = 1..512,
                    modifier = Modifier.weight(1f),
                    onChange = { v -> vm.update { s -> s.copy(patch = s.patch.copy(startAddress = v)) } },
                )
                NumberField(
                    label = "Fixture count",
                    value = state.patch.count,
                    range = 1..512,
                    modifier = Modifier.weight(1f),
                    onChange = { v -> vm.update { s -> s.copy(patch = s.patch.copy(count = v)) } },
                )
            }
            Text(state.patch.summary, style = MaterialTheme.typography.bodyMedium)
            if (state.patch.overflows) {
                Text(
                    "The patch runs past channel 512; the last fixtures will not be addressed.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---------------------------------------------------------- colour
        Section("Colour") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TestColor.PRESETS.forEach { (label, preset) ->
                    OutlinedButton(onClick = {
                        vm.update { s -> s.copy(color = preset.copy(dimmer = s.color.dimmer)) }
                    }) { Text(label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(
                            Color(
                                state.color.red / 255f,
                                state.color.green / 255f,
                                state.color.blue / 255f,
                            ),
                            RoundedCornerShape(8.dp),
                        )
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "R ${state.color.red}   G ${state.color.green}   B ${state.color.blue}   W ${state.color.white}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            LevelSlider("Red", state.color.red) { v ->
                vm.update { s -> s.copy(color = s.color.copy(red = v)) }
            }
            LevelSlider("Green", state.color.green) { v ->
                vm.update { s -> s.copy(color = s.color.copy(green = v)) }
            }
            LevelSlider("Blue", state.color.blue) { v ->
                vm.update { s -> s.copy(color = s.color.copy(blue = v)) }
            }
            LevelSlider("White", state.color.white) { v ->
                vm.update { s -> s.copy(color = s.color.copy(white = v)) }
            }
            LevelSlider("Intensity", state.color.dimmer) { v ->
                vm.update { s -> s.copy(color = s.color.copy(dimmer = v)) }
            }
        }

        // ----------------------------------------------------------- tests
        Section("Test") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TestMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { vm.update { it.copy(mode = mode) } },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                state.mode.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            when (state.mode) {
                TestMode.CHASE, TestMode.RGB_CYCLE, TestMode.CHANNEL_WALK,
                TestMode.INVERSE_WALK, TestMode.BLOCK_SWEEP, TestMode.UNIVERSE_RAMP -> {
                    Text("Step: ${state.stepMs} ms")
                    Slider(
                        value = state.stepMs.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(stepMs = v.toInt()) } },
                        valueRange = 100f..3000f,
                    )
                }

                TestMode.UNIVERSE_SWEEP -> {
                    Text("Step: ${state.stepMs} ms per universe")
                    Slider(
                        value = state.stepMs.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(stepMs = v.toInt()) } },
                        valueRange = 300f..5000f,
                    )
                    NumberField(
                        label = "Sweep up to universe",
                        value = state.sweepMaxUniverse,
                        range = 0..255,
                        onChange = { v -> vm.update { it.copy(sweepMaxUniverse = v) } },
                    )
                    if (state.target == OutputTarget.USB) {
                        Text(
                            "This mode only does anything over Art-Net: an XLR line carries a single " +
                                "universe. Switch the output to Art-Net or Both.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                TestMode.STROBE -> {
                    Text("Rate: ${state.strobeHz} Hz")
                    Slider(
                        value = state.strobeHz.toFloat(),
                        onValueChange = { v -> vm.update { it.copy(strobeHz = v.toInt()) } },
                        valueRange = 1f..15f,
                    )
                }

                TestMode.MANUAL -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(
                            label = "Channel",
                            value = state.manualChannel,
                            range = 1..512,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(manualChannel = v) } },
                        )
                        NumberField(
                            label = "Value",
                            value = state.manualValue,
                            range = 0..255,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(manualValue = v) } },
                        )
                    }
                }

                else -> Unit
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            LiveReadout(vm)
        }

        // ------------------------------------------------- universe view
        Section("Transmitted universe") {
            var visible by remember { mutableStateOf(false) }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { visible = !visible }) {
                    Text(if (visible) "Hide" else "Show all 512 channels")
                }
                if (visible) {
                    Text(
                        "Tap a cell to edit it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (visible) {
                UniverseGrid(vm) { channel ->
                    vm.update { it.copy(mode = TestMode.MANUAL, manualChannel = channel) }
                }
            }
        }

        // Build stamp: lets you confirm at a glance that the install actually
        // took, rather than relaunching the previous version.
        Text(
            "DMXorcist v${BuildConfig.VERSION_NAME}  -  build ${BuildConfig.BUILD_TIME}",
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Readout for the running mode.
 *
 * It collects the flow itself rather than the parent screen: some modes rewrite
 * this text on every frame, which was recomposing the whole screen thirty times
 * a second while the DMX loop runs.
 */
@Composable
private fun LiveReadout(vm: TesterViewModel) {
    val live by vm.live.collectAsStateWithLifecycle()
    Text(
        live.ifEmpty { "-" },
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * All 512 channels as 32 rows of 16, brightness proportional to level.
 *
 * Drawn in a single Canvas rather than 512 composables: the frame refreshes ten
 * times a second and the recomposition cost would compete directly with the
 * transmit loop. The flow is collected here, so nothing recomposes while the
 * grid is collapsed.
 */
@Composable
private fun UniverseGrid(vm: TesterViewModel, onPick: (Int) -> Unit) {
    val frame by vm.snapshot.collectAsStateWithLifecycle()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(COLUMNS.toFloat() / ROWS)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val col = (offset.x / (size.width / COLUMNS)).toInt().coerceIn(0, COLUMNS - 1)
                    val row = (offset.y / (size.height / ROWS)).toInt().coerceIn(0, ROWS - 1)
                    onPick(row * COLUMNS + col + 1)
                }
            }
    ) {
        val cellW = size.width / COLUMNS
        val cellH = size.height / ROWS
        for (row in 0 until ROWS) {
            for (col in 0 until COLUMNS) {
                val channel = row * COLUMNS + col + 1
                val level = if (channel < frame.size) frame[channel].toInt() and 0xFF else 0
                val shade = 0.10f + 0.90f * (level / 255f)
                drawRect(
                    color = Color(shade, shade, shade),
                    topLeft = Offset(col * cellW + 1f, row * cellH + 1f),
                    size = Size(cellW - 2f, cellH - 2f),
                )
            }
        }
        // Tick marks every 4 rows, so every 64 channels.
        for (row in 0 until ROWS step 4) {
            drawRect(
                color = labelColor.copy(alpha = 0.5f),
                topLeft = Offset(0f, row * cellH),
                size = Size(2f, cellH),
            )
        }
    }
    Text(
        "16 channels per row, from 1 top-left to 512 bottom-right. " +
            "A lighter tick every 64 channels.",
        style = MaterialTheme.typography.bodySmall,
        color = labelColor,
    )
}

private const val ROWS = 32
private const val COLUMNS = 16

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun LevelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.toString().padStart(3),
            modifier = Modifier.width(40.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.let { if (it in range) onChange(it) }
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = options.getOrElse(selectedIndex) { "" },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index)
                        expanded = false
                    },
                )
            }
        }
    }
}
