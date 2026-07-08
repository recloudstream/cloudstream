package com.lagradost.cloudstream3.ui.player

import android.util.Log
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Permissive trust manager that skips certificate validation.
 * Only used when the user explicitly opts in to ignoring SSL errors
 * for media playback from sources with self-signed or invalid certificates.
 */
class SSLTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("No client certificate chain provided")
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("No server certificate chain provided")
        }
        Log.w("SSLTrustManager", "Accepting unverified server certificate for: ${chain[0].subjectDN}")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return arrayOf()
    }
}
