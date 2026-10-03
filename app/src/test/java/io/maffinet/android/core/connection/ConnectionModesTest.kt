package io.maffinet.android.core.connection

import org.junit.Assert.*
import org.junit.Test

class ConnectionModesTest {
    @Test fun eachChoiceRequestsOnlyItsOwnModes() {
        for (apps in listOf(false, true)) for (telegram in listOf(false, true)) {
            val selected = ConnectionModes(apps, telegram)
            assertEquals(apps || telegram, selected.any)
            assertEquals(selected, selected.requested(true))
        }
    }

    @Test fun stopClearsRequestsAndPreservesModeChoices() {
        val selected = ConnectionModes(false, true)
        assertEquals(ConnectionModes(false, false), selected.requested(false))
        assertEquals(ConnectionModes(false, true), selected)
        assertEquals(selected, selected.requested(true))
    }
}
