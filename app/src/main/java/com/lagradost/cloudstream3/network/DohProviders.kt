package com.lagradost.cloudstream3.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress

/**
 * Based on https://github.com/tachiyomiorg/tachiyomi/blob/master/app/src/main/java/eu/kanade/tachiyomi/network/DohProviders.kt
 */

fun OkHttpClient.Builder.addGenericDns(url: String, ips: List<String>) = dns(
    DnsOverHttps
        .Builder()
        .client(build())
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(
            ips.map { InetAddress.getByName(it) }
        )
        .build()
)

fun OkHttpClient.Builder.addGoogleDns() = (
        addGenericDns(
            "https://dns.google/dns-query",
            listOf(
                "8.8.4.4",
                "8.8.8.8"
            )
        ))

fun OkHttpClient.Builder.addCloudFlareDns() = (
        addGenericDns(
            "https://cloudflare-dns.com/dns-query",
            // https://www.cloudflare.com/ips/
            listOf(
                "1.1.1.1",
                "1.0.0.1",
                "2606:4700:4700::1111",
                "2606:4700:4700::1001"
            )
        ))

// Commented out as it doesn't work
//fun OkHttpClient.Builder.addOpenDns() = (
//        addGenericDns(
//            "https://doh.opendns.com/dns-query",
//            // https://support.opendns.com/hc/en-us/articles/360038086532-Using-DNS-over-HTTPS-DoH-with-OpenDNS
//            listOf(
//                "208.67.222.222",
//                "208.67.220.220",
//                "2620:119:35::35",
//                "2620:119:53::53",
//            )
//        ))


fun OkHttpClient.Builder.addAdGuardDns() = (
        addGenericDns(
            "https://dns.adguard.com/dns-query",
            // https://github.com/AdguardTeam/AdGuardDNS
            listOf(
                // "Non-filtering"
                "94.140.14.140",
                "94.140.14.141",
            )
        ))