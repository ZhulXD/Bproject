package com.bypassnext.release

import org.junit.Assert.assertTrue
import org.junit.Test

class RootUtilTest {

    @Test
    fun testGetDisablePrivacyScript() {
        val script = RootUtil.getDisablePrivacyScript()

        // Verify key commands are present in the script
        assertTrue("Script should reset private_dns_mode", script.contains("settings put global private_dns_mode off"))
        assertTrue("Script should delete private_dns_specifier", script.contains("settings delete global private_dns_specifier"))
        assertTrue("Script should unmount certificates", script.contains("umount"))
        assertTrue("Script should clean up temp directory", script.contains("rm -rf /data/local/tmp/filtered_certs"))
        assertTrue("Script should include a success message", script.contains("Privacy Mode Deactivated"))
    }
}
