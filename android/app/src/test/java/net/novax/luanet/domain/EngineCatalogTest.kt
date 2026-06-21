package net.novax.luanet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCatalogTest {
    @Test fun containsOneReleasePerMinor() {
        assertEquals(17, EngineCatalog.releases.size)
        assertEquals("5.0.1", EngineCatalog.releases.first().version)
        assertEquals("5.16.1", EngineCatalog.latest.version)
    }

    @Test fun rejectsDowngrades() {
        assertTrue(EngineCatalog.canUpgrade("5.8.0", "5.16.1"))
        assertTrue(EngineCatalog.canUpgrade("5.8.0", "5.8.0"))
        assertFalse(EngineCatalog.canUpgrade("5.16.1", "5.8.0"))
    }
}

