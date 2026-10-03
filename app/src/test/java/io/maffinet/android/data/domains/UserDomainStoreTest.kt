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
}
