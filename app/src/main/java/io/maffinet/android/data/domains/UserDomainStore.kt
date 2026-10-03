package io.maffinet.android.data.domains

import io.maffinet.android.core.domains.DomainParser
import io.maffinet.android.core.domains.DomainParseResult
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface UserDomainStore {
    fun read(): List<String>
    fun write(domains: List<String>)
}

/** Pure editor/import logic, shared by the Android adapter and storage tests. */
class UserDomainRepository(private val store: UserDomainStore) {
    fun userDomains(): List<String> = store.read()

    @Synchronized fun saveUserDomains(text: String): DomainParseResult {
        val parsed = DomainParser.parse(text)
        if (parsed.isValid) store.write(parsed.domains)
        return parsed
    }

    @Synchronized fun importUserDomains(text: String): DomainParseResult {
        val parsed = DomainParser.parse(text)
        if (!parsed.isValid) return parsed
        val merged = (userDomains() + parsed.domains).distinct()
        store.write(merged)
        return DomainParseResult(merged, emptyList())
    }

    fun exportUserDomains(): String = userDomains().joinToString("\n", postfix = "\n")
}

/** A versioned, human-readable local file. Replacements never expose partial domain lists. */
class FileUserDomainStore(private val file: File) : UserDomainStore {
    @Synchronized override fun read(): List<String> {
        if (!file.exists()) return emptyList()
        val text = file.readText(Charsets.UTF_8)
        val versionLine = text.lineSequence().firstOrNull().orEmpty()
        require(versionLine == HEADER) { "Unsupported user domain storage version" }
        val parsed = DomainParser.parse(text)
        require(parsed.isValid) { "User domain file contains invalid domains" }
        return parsed.domains
    }

    @Synchronized override fun write(domains: List<String>) {
        val parsed = DomainParser.parse(domains.joinToString("\n"))
        require(parsed.isValid) { "Cannot save invalid domains" }
        val directory = file.absoluteFile.parentFile
        check(directory.exists() || directory.mkdirs()) { "Cannot create domain storage directory" }
        val temporary = File.createTempFile("user-domains-", ".tmp", directory)
        try {
            temporary.outputStream().use { output ->
                output.write((HEADER + "\n" + parsed.domains.joinToString("\n", postfix = if (parsed.domains.isEmpty()) "" else "\n")).toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    companion object { const val HEADER = "# Maffinet user domains; schema=1" }
}
