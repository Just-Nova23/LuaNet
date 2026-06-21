package net.novax.luanet.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntitlementPolicyTest {
    @Test fun freePolicyMatchesProductLimits() {
        assertEquals(1, EntitlementPolicy.free.activeServers)
        assertEquals(4 * 60 * 60 * 1000L, EntitlementPolicy.free.publicSessionMillis)
    }

    @Test fun premiumHasFiveServersAndNoTimeouts() {
        assertEquals(5, EntitlementPolicy.premium.activeServers)
        assertNull(EntitlementPolicy.premium.publicSessionMillis)
    }
}
