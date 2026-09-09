package net.seb.dmxorcist.dmx

import com.hoho.android.usbserial.driver.UsbSerialPort

/** Slot 0 is the start code (0x00), slots 1..512 carry the levels. */
const val DMX_SLOTS = 512
const val DMX_FRAME_SIZE = DMX_SLOTS + 1

private const val WRITE_TIMEOUT_MS = 200

enum class DriverMode(val label: String, val hint: String) {
    OPEN_DMX(
        "Open DMX (raw FTDI)",
        "Unbuffered dongles: Enttec Open DMX USB and nearly every cheap FT232RL stick. " +
            "The phone generates the BREAK and the frame itself."
    ),
    ENTTEC_PRO(
        "Enttec DMX USB Pro",
        "Buffered dongles: a microcontroller inside the stick handles the timing. " +
            "Far more stable, but only works with genuine Pro hardware or a truly compatible clone."
    )
}

interface DmxDriver {
    val mode: DriverMode
    fun configure()
    fun writeFrame(frame: ByteArray)
}

/**
 * Dumb FT232-based dongle: we talk 250 kbaud 8N2 directly and build the BREAK
 * ourselves before every frame.
 */
class OpenDmxDriver(private val port: UsbSerialPort) : DmxDriver {

    override val mode = DriverMode.OPEN_DMX
    private var nativeBreakWorks = true

    override fun configure() {
        setDmxBaud()
    }

    private fun setDmxBaud() =
        port.setParameters(250_000, 8, UsbSerialPort.STOPBITS_2, UsbSerialPort.PARITY_NONE)

    override fun writeFrame(frame: ByteArray) {
        sendBreak()
        port.write(frame, WRITE_TIMEOUT_MS)
    }

    /**
     * Two strategies. The FT232 native BREAK command costs a USB round trip
     * (~500 us), which yields a long but perfectly legal BREAK: DMX512-A allows
     * up to 1 s. If the driver does not expose it, fall back to the classic
     * trick of dropping the baud rate and sending 0x00. At 56000 baud the start
     * bit plus eight zero data bits hold the line low for nine bit times, about
     * 160 us of BREAK, and the stop bit provides the mark after break.
     */
    private fun sendBreak() {
        if (nativeBreakWorks) {
            try {
                port.setBreak(true)
                port.setBreak(false)
                return
            } catch (e: UnsupportedOperationException) {
                nativeBreakWorks = false
            } catch (e: NoSuchMethodError) {
                nativeBreakWorks = false
            }
        }
        port.setParameters(56_000, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.write(byteArrayOf(0), WRITE_TIMEOUT_MS)
        setDmxBaud()
    }
}

/**
 * Enttec Pro dongle: the frame is wrapped in a message and the stick's own
 * microcontroller takes care of the BREAK and the timing.
 */
class EnttecProDriver(private val port: UsbSerialPort) : DmxDriver {

    override val mode = DriverMode.ENTTEC_PRO

    override fun configure() =
        port.setParameters(115_200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

    override fun writeFrame(frame: ByteArray) {
        val packet = ByteArray(frame.size + 5)
        packet[0] = START_OF_MESSAGE
        packet[1] = LABEL_OUTPUT_ONLY
        packet[2] = (frame.size and 0xFF).toByte()
        packet[3] = ((frame.size shr 8) and 0xFF).toByte()
        frame.copyInto(packet, destinationOffset = 4)
        packet[packet.size - 1] = END_OF_MESSAGE
        port.write(packet, WRITE_TIMEOUT_MS)
    }

    private companion object {
        const val START_OF_MESSAGE = 0x7E.toByte()
        const val LABEL_OUTPUT_ONLY = 0x06.toByte()
        const val END_OF_MESSAGE = 0xE7.toByte()
    }
}
