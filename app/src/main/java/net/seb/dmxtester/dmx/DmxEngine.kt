package net.seb.dmxtester.dmx

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where frames go. Both outputs can run at the same time. */
enum class OutputTarget(val label: String) {
    USB("USB-DMX dongle"),
    ARTNET("Art-Net (Wi-Fi)"),
    BOTH("Both"),
}

data class EngineStatus(
    val deviceName: String? = null,
    val vendorId: Int = 0,
    val productId: Int = 0,
    val connected: Boolean = false,
    val mode: DriverMode = DriverMode.OPEN_DMX,
    val target: OutputTarget = OutputTarget.USB,
    val framesPerSecond: Int = 0,
    val error: String? = null,
    val artNetError: String? = null,
)

private const val ACTION_USB_PERMISSION = "net.seb.dmxtester.USB_PERMISSION"
private const val TARGET_PERIOD_MS = 30L      // ~33 frames/s, the practical DMX512 ceiling
private const val SNAPSHOT_PERIOD_MS = 100L   // universe view refresh rate

/**
 * Owns the outputs and the refresh loop. DMX is not event driven: the universe
 * has to be retransmitted continuously, otherwise fixtures time out after a few
 * seconds.
 */
class DmxEngine(context: Context) {

    private val appContext = context.applicationContext
    private val usbManager = appContext.getSystemService(Context.USB_SERVICE) as UsbManager
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var port: UsbSerialPort? = null
    private var artNet: ArtNetOutput? = null
    private var loop: Job? = null

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** Copy of the last transmitted frame, for the 512-channel view. */
    private val _snapshot = MutableStateFlow(ByteArray(DMX_FRAME_SIZE))
    val snapshot: StateFlow<ByteArray> = _snapshot.asStateFlow()

    /**
     * Fills the frame (513 bytes, slot 0 is the start code) and returns the
     * Art-Net universe to use for it. The universe sweep mode relies on this to
     * change universe at every step.
     */
    var renderer: ((frame: ByteArray, elapsedMs: Long) -> Int)? = null

    var artNetTarget: String = ArtNetOutput.BROADCAST

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action != ACTION_USB_PERMISSION) return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                val s = _status.value
                connect(s.mode, s.target)
            } else {
                _status.value = _status.value.copy(error = "USB permission denied")
            }
        }
    }

    fun registerReceiver() {
        ContextCompat.registerReceiver(
            appContext,
            permissionReceiver,
            IntentFilter(ACTION_USB_PERMISSION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun unregisterReceiver() {
        runCatching { appContext.unregisterReceiver(permissionReceiver) }
    }

    fun findDevice(): UsbDevice? =
        UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).firstOrNull()?.device

    fun refreshDetection() {
        val device = findDevice()
        _status.value = _status.value.copy(
            deviceName = device?.let { it.productName ?: it.deviceName },
            vendorId = device?.vendorId ?: 0,
            productId = device?.productId ?: 0,
            error = if (device == null) "No dongle detected" else null,
        )
    }

    fun connect(mode: DriverMode, target: OutputTarget) {
        disconnect()

        val wantsUsb = target != OutputTarget.ARTNET
        val wantsArtNet = target != OutputTarget.USB
        var usbDriver: DmxDriver? = null

        if (wantsUsb) {
            usbDriver = openUsb(mode, target) ?: return
        }

        if (wantsArtNet) {
            // Nothing is opened here: the socket and the address resolution
            // happen inside the loop, on the IO thread.
            artNet = ArtNetOutput().apply { open(artNetTarget) }
        }

        val device = if (wantsUsb) findDevice() else null
        _status.value = EngineStatus(
            deviceName = device?.let { it.productName ?: it.deviceName },
            vendorId = device?.vendorId ?: 0,
            productId = device?.productId ?: 0,
            connected = true,
            mode = mode,
            target = target,
        )
        startLoop(usbDriver)
    }

    /** Returns null if opening failed, or if the permission prompt is pending. */
    private fun openUsb(mode: DriverMode, target: OutputTarget): DmxDriver? {
        val serialDriver = UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .firstOrNull()
        if (serialDriver == null) {
            _status.value = _status.value.copy(connected = false, error = "No dongle detected")
            return null
        }

        val device = serialDriver.device
        if (!usbManager.hasPermission(device)) {
            requestPermission(device, mode, target)
            return null
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            _status.value = _status.value.copy(connected = false, error = "Cannot open USB device")
            return null
        }

        return try {
            val serialPort = serialDriver.ports.first()
            serialPort.open(connection)
            serialPort.setDTR(true)
            serialPort.setRTS(true)
            val dmxDriver = when (mode) {
                DriverMode.OPEN_DMX -> OpenDmxDriver(serialPort)
                DriverMode.ENTTEC_PRO -> EnttecProDriver(serialPort)
            }
            dmxDriver.configure()
            port = serialPort
            dmxDriver
        } catch (e: Exception) {
            runCatching { connection.close() }
            _status.value = _status.value.copy(
                connected = false,
                error = e.message ?: e.javaClass.simpleName,
            )
            null
        }
    }

    private fun requestPermission(device: UsbDevice, mode: DriverMode, target: OutputTarget) {
        _status.value = _status.value.copy(mode = mode, target = target)
        // setPackage(): without it, Android 14+ rejects a mutable implicit PendingIntent.
        val intent = Intent(ACTION_USB_PERMISSION).setPackage(appContext.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        usbManager.requestPermission(
            device,
            PendingIntent.getBroadcast(appContext, 0, intent, flags),
        )
    }

    private fun startLoop(usbDriver: DmxDriver?) {
        val frame = ByteArray(DMX_FRAME_SIZE)
        val startedAt = SystemClock.elapsedRealtime()
        loop = scope.launch {
            var frames = 0
            var lastCount = startedAt
            var lastSnapshot = startedAt
            var artNetFailures = 0
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                frame[0] = 0                       // DMX start code
                val universe = renderer?.invoke(frame, now - startedAt) ?: 0

                // USB is the critical output: if it fails, stop everything.
                try {
                    usbDriver?.writeFrame(frame)
                } catch (e: Exception) {
                    _status.value = _status.value.copy(
                        connected = false,
                        error = "USB link lost: ${e.message ?: e.javaClass.simpleName}",
                    )
                    return@launch
                }

                // Art-Net is not critical: with no network it fails on every frame,
                // and that must never take the wired output down with it.
                artNet?.let { output ->
                    try {
                        output.send(universe, frame)
                        if (artNetFailures > 0) {
                            artNetFailures = 0
                            _status.value = _status.value.copy(artNetError = null)
                        }
                    } catch (e: Exception) {
                        artNetFailures++
                        if (artNetFailures == 1 || artNetFailures % 100 == 0) {
                            _status.value = _status.value.copy(
                                artNetError = "Art-Net silent (${e.message ?: e.javaClass.simpleName}) " +
                                    "- check the phone is on the node's network",
                            )
                        }
                    }
                }

                frames++
                if (now - lastCount >= 1000) {
                    _status.value = _status.value.copy(framesPerSecond = frames)
                    frames = 0
                    lastCount = now
                }
                if (now - lastSnapshot >= SNAPSHOT_PERIOD_MS) {
                    _snapshot.value = frame.copyOf()
                    lastSnapshot = now
                }

                val spent = SystemClock.elapsedRealtime() - now
                if (spent < TARGET_PERIOD_MS) delay(TARGET_PERIOD_MS - spent)
            }
        }
    }

    fun disconnect() {
        loop?.cancel()
        loop = null
        runCatching { port?.close() }
        port = null
        artNet?.close()
        artNet = null
        _status.value = _status.value.copy(connected = false, framesPerSecond = 0)
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
    }
}
