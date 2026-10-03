package com.shielddp.vpn

import android.content.Context
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import com.wireguard.config.Interface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

object Vpn {
    val connected = MutableStateFlow(false)
    var startedAt = 0L
        private set

    private var backend: GoBackend? = null

    private val tunnel = object : Tunnel {
        override fun getName() = "shielddp"
        override fun onStateChange(newState: Tunnel.State) {
            connected.value = newState == Tunnel.State.UP
        }
    }

    private fun backend(ctx: Context): GoBackend =
        backend ?: GoBackend(ctx.applicationContext).also { backend = it }

    /** Connects to [server]. Apps in [bypass] skip the VPN (split tunneling). */
    suspend fun connect(ctx: Context, server: Server, bypass: Set<String>) = withContext(Dispatchers.IO) {
        val parsed = Config.parse(ByteArrayInputStream(server.config.toByteArray()))
        val src = parsed.getInterface()
        val ib = Interface.Builder()
            .addAddresses(src.addresses)
            .addDnsServers(src.dnsServers)
            .setKeyPair(src.keyPair)
            .excludeApplications(bypass)
        src.mtu.ifPresent { ib.setMtu(it) }
        val cfg = Config.Builder().setInterface(ib.build()).addPeers(parsed.peers).build()
        backend(ctx).setState(tunnel, Tunnel.State.UP, cfg)
        startedAt = System.currentTimeMillis()
        connected.value = true
    }

    suspend fun disconnect(ctx: Context) = withContext(Dispatchers.IO) {
        backend(ctx).setState(tunnel, Tunnel.State.DOWN, null)
        connected.value = false
    }

    /** Returns (bytes received, bytes sent) for the current session. */
    suspend fun stats(ctx: Context): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            val s = backend(ctx).getStatistics(tunnel)
            s.totalRx() to s.totalTx()
        } catch (e: Exception) {
            null
        }
    }
}
