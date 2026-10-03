package io.maffinet.android.core.domains

/** Domain selection is independent of application routing and the desync strategy. */
data class DomainList(
    val id: String,
    val name: String,
    val domains: List<String>,
    val isActive: Boolean = true,
    val isBuiltIn: Boolean = false,
    val isModified: Boolean = false,
    val isDeleted: Boolean = false,
)

data class DomainParseError(val line: Int, val input: String, val message: String)

data class DomainParseResult(val domains: List<String>, val errors: List<DomainParseError>) {
    val isValid: Boolean get() = errors.isEmpty()
}
