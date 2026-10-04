package io.maffinet.android.data.domains

import io.maffinet.android.core.domains.DomainParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UserDomainStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun savingReopeningReplacingAndClearingDomainsPersists() {
        val file = File(temporary.root, "domains/user.txt")
        FileUserDomainStore(file).write(listOf("EXAMPLE.COM", "example.net", "example.com"))
        assertEquals(listOf("example.com", "example.net"), FileUserDomainStore(file).read())
        FileUserDomainStore(file).write(listOf("edited.org"))
        assertEquals(listOf("edited.org"), FileUserDomainStore(file).read())
        FileUserDomainStore(file).write(emptyList())
        assertEquals(emptyList<String>(), FileUserDomainStore(file).read())
        assertEquals(listOf("user.txt"), file.parentFile.list()?.toList())
    }

    @Test fun invalidWritePreservesSavedDomains() {
        val store = FileUserDomainStore(File(temporary.root, "user.txt"))
        store.write(listOf("example.com"))
        assertThrows(IllegalArgumentException::class.java) { store.write(listOf("https://invalid.net")) }
        assertEquals(listOf("example.com"), store.read())
    }

    @Test fun exportedTextCanBeImportedAndFutureStorageVersionIsRejected() {
        val file = File(temporary.root, "user.txt")
        val store = FileUserDomainStore(file)
        store.write(listOf("example.com", "example.net"))
        assertEquals(store.read(), DomainParser.parse(file.readText()).domains)
        file.writeText("# Maffinet user domains; schema=2\nexample.org\n")
        assertThrows(IllegalArgumentException::class.java) { store.read() }
    }

    @Test fun userEditorAndImportValidateBeforeSavingAndRoundTrip() {
        val repository = UserDomainRepository(FileUserDomainStore(File(temporary.root, "user.txt")))
        assertTrue(repository.saveUserDomains("example.com\nexample.net").isValid)
        assertFalse(repository.saveUserDomains("bad://url").isValid)
        assertEquals(listOf("example.com", "example.net"), repository.userDomains())
        assertTrue(repository.importUserDomains("EXAMPLE.COM\nadditional.org").isValid)
        assertEquals(listOf("example.com", "example.net", "additional.org"), repository.userDomains())
        val copy = UserDomainRepository(FileUserDomainStore(File(temporary.root, "copy.txt")))
        assertTrue(copy.importUserDomains(repository.exportUserDomains()).isValid)
        assertEquals(repository.userDomains(), copy.userDomains())
        assertTrue(repository.saveUserDomains("edited.org").isValid)
        assertEquals(listOf("edited.org"), repository.userDomains())
    }

    @Test fun hostsImportMergesDomainsButAnyInvalidAliasPreservesTheEntireFile() {
        val file = File(temporary.root, "user.txt")
        val repository = UserDomainRepository(FileUserDomainStore(file))
        repository.saveUserDomains("saved.org")
        assertTrue(repository.importUserDomains("\uFEFF176.99.11.77 first.net FIRST.NET second.org # aliases\n" +
            "2001:4860:4860::8888 ipv6.org\n0.0.0.0 tracker.net\n127.0.0.1 blocked.net localhost").isValid)
        assertEquals(listOf("saved.org", "first.net", "second.org", "ipv6.org"), repository.userDomains())
        val previous = file.readBytes()
        val invalid = repository.importUserDomains("176.99.11.77 new.org\n2001:4860:4860::8888 valid.net https://bad.net")
        assertFalse(invalid.isValid)
        assertEquals(2, invalid.errors.single().line)
        assertArrayEquals(previous, file.readBytes())
        assertFalse(repository.importUserDomains("<html>not a hosts file</html>").isValid)
        assertArrayEquals(previous, file.readBytes())
        assertTrue(repository.importUserDomains("# no domains\n0.0.0.0 blocked.net").isValid)
        assertArrayEquals(previous, file.readBytes())
    }
}
