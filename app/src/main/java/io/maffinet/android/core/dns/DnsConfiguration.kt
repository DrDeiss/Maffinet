package io.maffinet.android.core.dns

import java.util.Collections
import java.util.Locale

/** Observed DNS assignments and saved preferences, never evidence of a query's resolver. */
class DnsConfiguration(
    val savedSelection: String,
    val savedLabel: String,
    savedAddresses: List<String>,
    vpnAssignedAddresses: List<String>?,
    physicalAddresses: List<String>,
    val privateDnsActive: Boolean?,
    val privateDnsHostname: String?,
    /** Physical network/link identity supplied by the controller, if available. */
    val physicalNetworkIdentity: String? = null,
) {
    val savedAddresses: List<String> = immutable(savedAddresses)
    /** null means no VPN; an empty list means the VPN inherits network DNS. */
    val vpnAssignedAddresses: List<String>? = vpnAssignedAddresses?.let(::immutable)
    val physicalAddresses: List<String> = immutable(physicalAddresses)

    /** Includes every field, preserving null, empty, address order and preference identity. */
    val fingerprint: List<String> = immutable(buildList {
        add(savedSelection)
        add(savedLabel)
        addAddresses(this@DnsConfiguration.savedAddresses)
        addAddresses(this@DnsConfiguration.vpnAssignedAddresses)
        addAddresses(this@DnsConfiguration.physicalAddresses)
        add(privateDnsActive?.toString() ?: "unknown")
        add(if (privateDnsHostname == null) "null" else "value")
        privateDnsHostname?.let(::add)
        add(if (physicalNetworkIdentity == null) "null" else "value")
        physicalNetworkIdentity?.let(::add)
    })

    val hasSelectionAssignmentMismatch: Boolean
        get() = vpnAssignedAddresses?.let { canonical(savedAddresses) != canonical(it) } ?: false

    fun summaryLines(): List<String> = buildList {
        add("Сохранённый выбор DNS: $savedLabel; адреса: ${addresses(savedAddresses, "DNS сети")}")
        add("DNS, назначенные VPN: ${vpnAssignedAddresses?.let { addresses(it, "DNS сети (наследование)") } ?: "VPN отсутствует"}")
        add("DNS физической сети: ${addresses(physicalAddresses, "неизвестны")}")
        add("Системный Private DNS: ${when (privateDnsActive) {
            null -> "состояние неизвестно"
            false -> "не активен"
            true -> "активен"
        }}${privateDnsHostname?.takeIf { it.isNotBlank() }?.let { "; имя: $it" } ?: ""}")
        add("Собственный DoH/DoT приложений: неизвестен")
        if (hasSelectionAssignmentMismatch) {
            add("Предупреждение: сохранённый выбор DNS отличается от назначенных VPN адресов.")
        }
        add("Это настройки и назначения DNS; фактический резолвер запросов не установлен.")
    }

    override fun equals(other: Any?): Boolean = other is DnsConfiguration && fingerprint == other.fingerprint
    override fun hashCode(): Int = fingerprint.hashCode()

    private companion object {
        fun immutable(values: List<String>): List<String> = Collections.unmodifiableList(ArrayList(values))
        fun canonical(values: List<String>): Set<String> = values.map { it.trim().lowercase(Locale.ROOT) }.toSet()
        fun addresses(values: List<String>, empty: String): String = values.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: empty
        fun MutableList<String>.addAddresses(values: List<String>?) {
            add(values?.size?.toString() ?: "null")
            values?.let(::addAll)
        }
    }
}
