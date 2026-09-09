package net.seb.dmxtester.dmx

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Art-Net output over UDP.
 *
 * The classic trap: the protocol's universe field starts at 0, while consoles
 * and nodes almost always display universes starting at 1. A desk's universe
 * "1" is universe 0 on the wire.
 */
class ArtNetOutput {

    private var socket: DatagramSocket? = null
    private var address: InetAddress? = null
    private var target: String = BROADCAST
    private var sequence = 1

    /**
     * Only records the target. The socket, and above all the name resolution,
     * are deferred to the first [send]: open() is called from the user tapping
     * Connect, so on the main thread, where any DNS lookup throws
     * NetworkOnMainThreadException. A literal IP would have worked; a hostname
     * would have crashed the app.
     */
    fun open(targetAddress: String) {
        close()
        target = targetAddress.trim().ifEmpty { BROADCAST }
    }

    /** [dmx] is the full 513-byte frame, start code included. */
    fun send(universe: Int, dmx: ByteArray) {
        val sock = socket ?: DatagramSocket().apply { broadcast = true }.also { socket = it }
        val dest = address ?: InetAddress.getByName(target).also { address = it }

        val packet = ByteArray(HEADER_SIZE + DMX_SLOTS)
        ID.copyInto(packet, 0)
        packet[7] = 0
        packet[8] = 0x00                                        // OpDmx = 0x5000,
        packet[9] = 0x50                                        // little-endian on the wire
        packet[10] = 0
        packet[11] = 14                                         // protocol version
        packet[12] = sequence.toByte()
        packet[13] = 0                                          // physical
        packet[14] = (universe and 0xFF).toByte()               // SubUni
        packet[15] = ((universe shr 8) and 0x7F).toByte()       // Net
        packet[16] = (DMX_SLOTS shr 8).toByte()                 // length, big-endian
        packet[17] = (DMX_SLOTS and 0xFF).toByte()
        dmx.copyInto(packet, HEADER_SIZE, 1, DMX_SLOTS + 1)

        sequence = if (sequence >= 255) 1 else sequence + 1
        sock.send(DatagramPacket(packet, packet.size, dest, PORT))
    }

    fun close() {
        runCatching { socket?.close() }
        socket = null
        address = null
    }

    companion object {
        const val PORT = 6454
        const val BROADCAST = "255.255.255.255"
        private const val HEADER_SIZE = 18
        private val ID = "Art-Net".toByteArray(Charsets.US_ASCII)
    }
}
