package com.lagradost.cloudstream3.actions.temp.fcast

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.ResolveListener
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import android.util.Log
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe

class FcastManager {
    private var nsdManager: NsdManager? = null

    // Used for receiver
    private val registrationListenerTcp = DefaultRegistrationListener()
    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER}-${Build.MODEL}"
    }

    /**
     * Start the fcast service
     * @param registerReceiver If true will register the app as a compatible fcast receiver for discovery in other app
     */
    fun init(context: Context, registerReceiver: Boolean) = ioSafe {
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceType = "_fcast._tcp"

        if (registerReceiver) {
            val serviceName = "$APP_PREFIX-${getDeviceName()}"

            val serviceInfo = NsdServiceInfo().apply {
                this.serviceName = serviceName
                this.serviceType = serviceType
                this.port = TCP_PORT
            }

            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListenerTcp
            )
        }

        nsdManager?.discoverServices(
            serviceType,
            NsdManager.PROTOCOL_DNS_SD,
            DefaultDiscoveryListener()
        )
    }

    fun stop() {
        nsdManager?.unregisterService(registrationListenerTcp)
    }

    inner class DefaultDiscoveryListener : NsdManager.DiscoveryListener {
        val tag = "DiscoveryListener"
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d(tag, "Discovery failed: $serviceType, error code: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.d(tag, "Stop discovery failed: $serviceType, error code: $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d(tag, "Discovery started: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d(tag, "Discovery stopped: $serviceType")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
            // Safe here as, java.lang.NoClassDefFoundError: Failed resolution of: Landroid/net/nsd/NsdManager$ServiceInfoCallback
            safe {
                if (serviceInfo == null) return@safe

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && SdkExtensions.getExtensionVersion(
                        Build.VERSION_CODES.TIRAMISU
                    ) >= 7
                ) {
                    nsdManager?.registerServiceInfoCallback(
                        serviceInfo,
                        Runnable::run,
                        object : NsdManager.ServiceInfoCallback {
                            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                                Log.e(tag, "Service registration failed: $errorCode")
                            }

                            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                                Log.d(
                                    tag,
                                    "Service updated: ${serviceInfo.serviceName}," +
                                            "Net: ${serviceInfo.hostAddresses.firstOrNull()?.hostAddress}"
                                )
                                synchronized(_currentDevices) {
                                    _currentDevices.removeIf { it.rawName == serviceInfo.serviceName }
                                    _currentDevices.add(PublicDeviceInfo(serviceInfo))
                                }
                            }

                            override fun onServiceLost() {
                                Log.d(tag, "Service lost: ${serviceInfo.serviceName},")
                                synchronized(_currentDevices) {
                                    _currentDevices.removeIf { it.rawName == serviceInfo.serviceName }
                                }
                            }

                            override fun onServiceInfoCallbackUnregistered() {}
                        })
                } else {
                    @Suppress("DEPRECATION")
                    nsdManager?.resolveService(serviceInfo, object : ResolveListener {
                        override fun onResolveFailed(
                            serviceInfo: NsdServiceInfo?,
                            errorCode: Int
                        ) {
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                            if (serviceInfo == null) return

                            synchronized(_currentDevices) {
                                _currentDevices.add(PublicDeviceInfo(serviceInfo))
                            }

                            Log.d(
                                tag,
                                "Service found: ${serviceInfo.serviceName}, Net: ${serviceInfo.host.hostAddress}"
                            )
                        }
                    })
                }
            }
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
            if (serviceInfo == null) return

            // May remove duplicates, but net and port is null here, preventing device specific identification
            synchronized(_currentDevices) {
                _currentDevices.removeAll {
                    it.rawName == serviceInfo.serviceName
                }
            }

            Log.d(tag, "Service lost: ${serviceInfo.serviceName}")
        }
    }

    companion object {
        const val APP_PREFIX = "CloudStream"
        private val _currentDevices: MutableList<PublicDeviceInfo> = mutableListOf()
        val currentDevices: List<PublicDeviceInfo> = _currentDevices

        class DefaultRegistrationListener : NsdManager.RegistrationListener {
            val tag = "DiscoveryService"
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Service registered: ${serviceInfo.serviceName}")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service registration failed: errorCode=$errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(tag, "Service unregistered: ${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(tag, "Service unregistration failed: errorCode=$errorCode")
            }
        }

        const val TCP_PORT = 46899
    }
}

class PublicDeviceInfo(serviceInfo: NsdServiceInfo) {
    val rawName: String = serviceInfo.serviceName
    val host: String? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        SdkExtensions.getExtensionVersion(
            Build.VERSION_CODES.TIRAMISU
        ) >= 7
    ) {
        serviceInfo.hostAddresses.firstOrNull()?.hostAddress
    } else {
        @Suppress("DEPRECATION")
        serviceInfo.host.hostAddress
    }
    val name = rawName.replace("-", " ") + host?.let { " $it" }
}