package net.seb.dmxtester

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import net.seb.dmxtester.dmx.ArtNetOutput
import net.seb.dmxtester.dmx.DmxEngine
import net.seb.dmxtester.dmx.DriverMode
import net.seb.dmxtester.dmx.OutputTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TesterState(
    val patch: Patch = Patch(),
    val color: TestColor = TestColor(),
    val mode: TestMode = TestMode.BLACKOUT,
    val stepMs: Int = 700,
    val strobeHz: Int = 8,
    val manualChannel: Int = 1,
    val manualValue: Int = 255,
    val driverMode: DriverMode = DriverMode.OPEN_DMX,
    val target: OutputTarget = OutputTarget.USB,
    /** Numbered the way the protocol carries it, so starting at 0. */
    val artNetUniverse: Int = 0,
    val artNetTarget: String = ArtNetOutput.BROADCAST,
    /** Last universe included in the sweep. */
    val sweepMaxUniverse: Int = 7,
)

class TesterViewModel(app: Application) : AndroidViewModel(app) {

    val engine = DmxEngine(app)

    private val _state = MutableStateFlow(TesterState())
    val state: StateFlow<TesterState> = _state.asStateFlow()

    /**
     * Readout for the running mode. Deliberately kept out of [state]: it is
     * rewritten thirty times a second from the render thread, and mixing it into
     * the UI state was overwriting the user's own settings.
     */
    private val _live = MutableStateFlow("")
    val live: StateFlow<String> = _live.asStateFlow()

    val status = engine.status
    val snapshot = engine.snapshot

    init {
        engine.registerReceiver()
        engine.renderer = ::render
        engine.refreshDetection()
    }

    fun update(transform: (TesterState) -> TesterState) {
        _state.update(transform)
    }

    fun connect() {
        val s = _state.value
        engine.artNetTarget = s.artNetTarget
        engine.connect(s.driverMode, s.target)
    }

    fun disconnect() = engine.disconnect()
    fun refresh() = engine.refreshDetection()

    override fun onCleared() {
        engine.renderer = null
        engine.unregisterReceiver()
        engine.shutdown()
        super.onCleared()
    }

    // --------------------------------------------------------------- render

    /** Fills the frame and returns the Art-Net universe to send it on. */
    private fun render(frame: ByteArray, elapsedMs: Long): Int {
        val s = _state.value
        var universe = s.artNetUniverse
        java.util.Arrays.fill(frame, 1, frame.size, 0)

        when (s.mode) {
            TestMode.BLACKOUT -> setLive("Blackout")

            TestMode.COLOR -> {
                paintAll(frame, s, s.color)
                setLive("Every fixture - ${describe(s.color)}")
            }

            TestMode.ALL_ON -> {
                java.util.Arrays.fill(frame, 1, frame.size, 255.toByte())
                setLive("512 channels at 255")
            }

            TestMode.UNIVERSE_SWEEP -> {
                val span = s.sweepMaxUniverse + 1
                universe = ((elapsedMs / s.stepMs) % span).toInt()
                java.util.Arrays.fill(frame, 1, frame.size, 255.toByte())
                setLive("Universe $universe (shown as ${universe + 1} on most consoles)")
            }

            TestMode.UNIVERSE_RAMP -> {
                val level = triangle(elapsedMs, s.stepMs * 4L)
                java.util.Arrays.fill(frame, 1, frame.size, level.toByte())
                setLive("512 channels at $level")
            }

            TestMode.BLOCK_SWEEP -> {
                val footprint = s.patch.profile.footprint
                val blocks = 512 / footprint
                val index = ((elapsedMs / s.stepMs) % blocks).toInt()
                val base = 1 + index * footprint
                paintAt(frame, base, s.patch.profile, s.color)
                setLive("Block ${index + 1}/$blocks - channels $base to ${base + footprint - 1}")
            }

            TestMode.CHASE -> {
                if (s.patch.count > 0) {
                    val index = ((elapsedMs / s.stepMs) % s.patch.count).toInt()
                    paintAt(frame, s.patch.addressOf(index), s.patch.profile, s.color)
                    setLive("Fixture ${index + 1}/${s.patch.count} - address ${s.patch.addressOf(index)}")
                }
            }

            TestMode.RGB_CYCLE -> {
                val phase = ((elapsedMs / s.stepMs) % 3).toInt()
                val color = when (phase) {
                    0 -> TestColor(255, 0, 0, 0, s.color.dimmer)
                    1 -> TestColor(0, 255, 0, 0, s.color.dimmer)
                    else -> TestColor(0, 0, 255, 0, s.color.dimmer)
                }
                paintAll(frame, s, color)
                setLive("Phase ${listOf("RED", "GREEN", "BLUE")[phase]}")
            }

            TestMode.STROBE -> {
                val periodMs = (1000.0 / s.strobeHz.coerceAtLeast(1)).toLong()
                val on = (elapsedMs % periodMs) < periodMs / 2
                if (on) paintAll(frame, s, s.color)
                setLive("Strobe ${s.strobeHz} Hz")
            }

            TestMode.CHANNEL_WALK -> {
                val channel = 1 + ((elapsedMs / s.stepMs) % 512).toInt()
                frame[channel] = 255.toByte()
                setLive("Channel $channel at 255")
            }

            TestMode.INVERSE_WALK -> {
                val channel = 1 + ((elapsedMs / s.stepMs) % 512).toInt()
                java.util.Arrays.fill(frame, 1, frame.size, 255.toByte())
                frame[channel] = 0
                setLive("Channel $channel at 0 (all others at 255)")
            }

            TestMode.MANUAL -> {
                val channel = s.manualChannel.coerceIn(1, 512)
                frame[channel] = s.manualValue.coerceIn(0, 255).toByte()
                setLive("Channel $channel = ${s.manualValue}")
            }
        }
        return universe
    }

    /** Rises then falls from 0 to 255 over [periodMs]. */
    private fun triangle(elapsedMs: Long, periodMs: Long): Int {
        val half = periodMs / 2
        val phase = elapsedMs % periodMs
        return if (phase < half) {
            (phase * 255 / half).toInt()
        } else {
            (255 - (phase - half) * 255 / half).toInt()
        }.coerceIn(0, 255)
    }

    private fun paintAll(frame: ByteArray, s: TesterState, color: TestColor) {
        for (i in 0 until s.patch.count) {
            paintAt(frame, s.patch.addressOf(i), s.patch.profile, color)
        }
    }

    private fun paintAt(frame: ByteArray, base: Int, p: FixtureProfile, color: TestColor) {
        // With no dedicated dimmer channel, intensity is folded into the colour
        // components themselves.
        val scale = if (p.dimmer == null) color.dimmer / 255.0 else 1.0

        p.dimmer?.let { put(frame, base + it, color.dimmer) }
        p.red?.let { put(frame, base + it, (color.red * scale).toInt()) }
        p.green?.let { put(frame, base + it, (color.green * scale).toInt()) }
        p.blue?.let { put(frame, base + it, (color.blue * scale).toInt()) }
        p.white?.let { put(frame, base + it, (color.white * scale).toInt()) }
    }

    private fun put(frame: ByteArray, channel: Int, value: Int) {
        if (channel in 1..512) frame[channel] = value.coerceIn(0, 255).toByte()
    }

    private fun describe(c: TestColor) = "R${c.red} G${c.green} B${c.blue} W${c.white}"

    private fun setLive(text: String) {
        _live.value = text
    }
}
