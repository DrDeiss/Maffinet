package io.maffinet.android.core.access

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import io.maffinet.android.core.dns.DnsCatalog
import io.maffinet.android.core.dns.DnsConfiguration

/** Builder assignments are evidence of configuration, not of an application's resolver. */
object DnsConfigurationMonitor {
    @Volatile private var assigned: List<String>? = null

    fun vpnEstablished(addresses: List<String>) { assigned = addresses.toList() }
    fun vpnStopped() { assigned = null }

    fun capture(preferences: SharedPreferences, properties: LinkProperties?, network: Network?): DnsConfiguration {
        val selected = DnsCatalog.resolve(preferences.getString("custom_dns_preset", DnsCatalog.SYSTEM_ID))
        return DnsConfiguration(
            savedSelection = selected.id,
            savedLabel = selected.label,
            savedAddresses = selected.ipv4,
            vpnAssignedAddresses = assigned,
            physicalAddresses = properties?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty(),
            privateDnsActive = if (Build.VERSION.SDK_INT >= 28) properties?.isPrivateDnsActive else null,
            privateDnsHostname = if (Build.VERSION.SDK_INT >= 28) properties?.privateDnsServerName else null,
            physicalNetworkIdentity = network?.let {
                listOf(it.networkHandle.toString(), properties?.interfaceName.orEmpty(),
                    properties?.linkAddresses.toString(), properties?.routes.toString()).joinToString("|")
            },
        )
    }

    fun read(context: Context): DnsConfiguration {
        val preferences = context.getSharedPreferences(context.packageName + "_preferences", Context.MODE_PRIVATE)
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork
        // This process is excluded from Maffinet's VPN. Never label an unexpected
        // VPN LinkProperties snapshot as the physical network.
        val physical = network?.takeIf {
            connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == false
        }
        return capture(preferences, physical?.let(connectivity::getLinkProperties), physical)
    }
}
