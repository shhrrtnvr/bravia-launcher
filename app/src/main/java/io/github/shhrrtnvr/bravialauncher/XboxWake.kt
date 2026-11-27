package io.github.shhrrtnvr.bravialauncher

import android.content.Context
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Wakes the Xbox over the home network, the way the Xbox phone app does: the console's
 * "remote features" listen on UDP 5050 for a power-on packet carrying its Live ID.
 * Needs, on the Xbox: Settings > Devices & connections > Remote features on, and the
 * "Sleep" power mode (not "Shut down / energy saving").
 *
 * The Live ID isn't something anyone should have to type: whenever the Xbox is awake it
 * answers a discovery broadcast with its certificate, whose common name is the Live ID.
 * We remember that (and its address) and use it the next time the Xbox is asleep.
 *
 * Blocking calls: run them off the main thread.
 */
object XboxWake {
    private const val TAG = "XboxWake"
    private const val PORT = 5050
    private const val PREFS = "xbox"
    private const val KEY_ID = "live_id"
    private const val KEY_IP = "ip"

    private const val DISCOVERY_REQUEST = 0xDD00
    private const val DISCOVERY_RESPONSE = 0xDD01
    private const val POWER_ON = 0xDD02
    private const val CLIENT_ANDROID = 8

    fun knownLiveId(context: Context): String? = Storage.prefs(context, PREFS).getString(KEY_ID, null)

    /** Send the power-on packet (a few times, as the official app does). False if we don't know the Xbox yet. */
    fun wake(context: Context): Boolean {
        val prefs = Storage.prefs(context, PREFS)
        val id = prefs.getString(KEY_ID, null) ?: return false
        val targets = listOfNotNull(prefs.getString(KEY_IP, null), "255.255.255.255")
        val payload = sgString(id)
        val packet = header(POWER_ON, payload.size) + payload
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                repeat(5) {
                    for (t in targets) socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(t), PORT))
                    Thread.sleep(100)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "power-on failed", e)
        }
        return true
    }

    /** Ask the network for an Xbox; remember its Live ID and address. Returns true if one answered. */
    fun discover(context: Context, timeoutMs: Int = 1500): Boolean {
        val payload = ByteBuffer.allocate(10).putInt(0).putShort(CLIENT_ANDROID.toShort())
            .putShort(0).putShort(2).array()
        val packet = header(DISCOVERY_REQUEST, payload.size) + payload
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName("255.255.255.255"), PORT))
                Storage.prefs(context, PREFS).getString(KEY_IP, null)?.let {
                    socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(it), PORT))
                }
                val buf = ByteArray(4096)
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val reply = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(reply)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    val id = parseLiveId(reply.data, reply.length) ?: continue
                    Storage.prefs(context, PREFS).edit()
                        .putString(KEY_ID, id)
                        .putString(KEY_IP, reply.address.hostAddress)
                        .commit()
                    Log.i(TAG, "found Xbox at ${reply.address.hostAddress}")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "discovery failed", e)
        }
        return false
    }

    /** Discovery response: header, flags u32, type u16, name, uuid, last error u32, certificate (u16-prefixed DER). */
    private fun parseLiveId(data: ByteArray, length: Int): String? = try {
        val b = ByteBuffer.wrap(data, 0, length)
        if ((b.short.toInt() and 0xFFFF) != DISCOVERY_RESPONSE) null else {
            b.short; b.short // payload length, version
            b.int; b.short // flags, console type
            skipSgString(b); skipSgString(b) // name, uuid
            b.int // last error
            val cert = ByteArray(b.short.toInt() and 0xFFFF).also { b.get(it) }
            val x509 = CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
            Regex("CN=([^,]+)").find(x509.subjectX500Principal.name)?.groupValues?.get(1)
        }
    } catch (e: Exception) {
        null
    }

    private fun header(type: Int, payloadLength: Int): ByteArray =
        ByteBuffer.allocate(6).putShort(type.toShort()).putShort(payloadLength.toShort()).putShort(0).array()

    /** SmartGlass string: u16 length, UTF-8 bytes, NUL. */
    private fun sgString(s: String): ByteArray {
        val bytes = s.toByteArray(Charsets.UTF_8)
        return ByteArrayOutputStream().apply {
            write(bytes.size shr 8); write(bytes.size and 0xFF); write(bytes); write(0)
        }.toByteArray()
    }

    private fun skipSgString(b: ByteBuffer) {
        val n = b.short.toInt() and 0xFFFF
        b.position(b.position() + n + 1)
    }
}
