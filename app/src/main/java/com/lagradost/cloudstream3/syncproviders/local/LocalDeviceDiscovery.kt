package com.lagradost.cloudstream3.syncproviders.local

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import java.io.Closeable
import java.net.InetAddress

internal class LocalDeviceDiscovery(context: Context, private val found: (String, InetAddress, Int) -> Unit) : Closeable {
    companion object { const val TYPE = "_cs3handoff._tcp." }
    private val manager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    @Volatile private var active = true
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private val multicast = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
        ?.createMulticastLock("cloudstream-manual-share")?.apply { setReferenceCounted(false); acquire() }

    fun advertise(port: Int) {
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { if (!active) runCatching { manager.unregisterService(this) } }
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = listener
        manager.registerService(NsdServiceInfo().apply {
            serviceName = "CloudStream-${Build.MODEL.take(24)}"; serviceType = TYPE; this.port = port
        }, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) { if (!active) runCatching { manager.stopServiceDiscovery(this) } }
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (!active || info.serviceType.trimEnd('.') != TYPE.trimEnd('.')) return
                manager.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, error: Int) {}
                    override fun onServiceResolved(service: NsdServiceInfo) {
                        val address = service.host ?: return
                        if (active && LocalPairingTransport.isLocal(address)) found(service.serviceName, address, service.port)
                    }
                })
            }
        }
        discovery = listener
        manager.discoverServices(TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun close() {
        active = false
        registration?.let { runCatching { manager.unregisterService(it) } }; registration = null
        discovery?.let { runCatching { manager.stopServiceDiscovery(it) } }; discovery = null
        multicast?.let { if (it.isHeld) it.release() }
    }
}
