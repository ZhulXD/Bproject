package com.bypassnext.release

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Tests for MainActivity using Espresso.
 * These tests mock the ShellExecutor to simulate different system states (root, DNS, etc.)
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    private lateinit var mockExecutor: TestMockShellExecutor
    private lateinit var originalExecutor: ShellExecutor

    // Helper mock executor for instrumentation tests
    class TestMockShellExecutor : ShellExecutor {
        val commandResponses = mutableMapOf<String, String>()
        val executedCommands = mutableListOf<String>()

        override suspend fun execute(command: String): String {
            executedCommands.add(command)
            return commandResponses[command]
                ?: commandResponses.entries.find { command.contains(it.key) }?.value
                ?: ""
        }
    }

    @Before
    fun setup() {
        originalExecutor = RootUtil.shellExecutor
        mockExecutor = TestMockShellExecutor()
        RootUtil.shellExecutor = mockExecutor
    }

    @After
    fun tearDown() {
        RootUtil.shellExecutor = originalExecutor
    }

    @Test
    fun testInitialUI_RootDenied() {
        // Mock root denied
        mockExecutor.commandResponses["id"] = "Error: su not found"

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnToggle)).check(matches(isDisplayed()))
            onView(withId(R.id.btnToggle)).check(matches(withText("NO ROOT")))
            onView(withId(R.id.btnToggle)).check(matches(isNotEnabled()))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Root access DENIED"))))
        }
    }

    @Test
    fun testInitialUI_RootGranted_PrivacyInactive() {
        // Mock root granted and privacy disabled
        mockExecutor.commandResponses["id"] = "uid=0(root)"
        mockExecutor.commandResponses["settings get global private_dns_mode"] = "off"
        mockExecutor.commandResponses["settings get global private_dns_specifier"] = ""

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnToggle)).check(matches(isDisplayed()))
            onView(withId(R.id.btnToggle)).check(matches(withText("INACTIVE")))
            onView(withId(R.id.btnToggle)).check(matches(isEnabled()))
            onView(withId(R.id.tvDnsStatus)).check(matches(withText("System Default")))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Root access GRANTED"))))
        }
    }

    @Test
    fun testInitialUI_RootGranted_PrivacyActive() {
        // Mock root granted and privacy enabled
        mockExecutor.commandResponses["id"] = "uid=0(root)"
        mockExecutor.commandResponses["settings get global private_dns_mode"] = "hostname"
        mockExecutor.commandResponses["settings get global private_dns_specifier"] = "a4f5f2.dns.nextdns.io"

        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.btnToggle)).check(matches(isDisplayed()))
            onView(withId(R.id.btnToggle)).check(matches(withText("ACTIVE")))
            onView(withId(R.id.tvDnsStatus)).check(matches(withText("a4f5f2.dns.nextdns.io")))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Privacy Mode detected: ACTIVE"))))
        }
    }

    @Test
    fun testTogglePrivacy_Activate() {
        // Mock root granted and privacy disabled initially
        mockExecutor.commandResponses["id"] = "uid=0(root)"
        mockExecutor.commandResponses["settings get global private_dns_mode"] = "off"
        mockExecutor.commandResponses["settings get global private_dns_specifier"] = ""

        // Mock success response for enable script (it ends with "Certificates filtered.")
        mockExecutor.commandResponses["settings put global private_dns_mode hostname"] = "Privacy Mode Activated: DNS set to NextDNS, Certificates filtered."

        ActivityScenario.launch(MainActivity::class.java).use {
            // Click to enable
            onView(withId(R.id.btnToggle)).perform(click())

            // Check UI updated to ACTIVE
            onView(withId(R.id.btnToggle)).check(matches(withText("ACTIVE")))
            onView(withId(R.id.tvDnsStatus)).check(matches(withText("a4f5f2.dns.nextdns.io")))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Activating Privacy Mode"))))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Certificates filtered"))))
        }
    }

    @Test
    fun testTogglePrivacy_Deactivate() {
        // Mock root granted and privacy enabled initially
        mockExecutor.commandResponses["id"] = "uid=0(root)"
        mockExecutor.commandResponses["settings get global private_dns_mode"] = "hostname"
        mockExecutor.commandResponses["settings get global private_dns_specifier"] = "a4f5f2.dns.nextdns.io"

        // Mock success response for disable script
        mockExecutor.commandResponses["settings put global private_dns_mode off"] = "Privacy Mode Deactivated: DNS reset, System Certificates restored."

        ActivityScenario.launch(MainActivity::class.java).use {
            // Click to deactivate
            onView(withId(R.id.btnToggle)).perform(click())

            // Check UI updated to INACTIVE
            onView(withId(R.id.btnToggle)).check(matches(withText("INACTIVE")))
            onView(withId(R.id.tvDnsStatus)).check(matches(withText("System Default")))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Deactivating Privacy Mode"))))
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("System Certificates restored"))))
        }
    }

    @Test
    fun testTogglePrivacy_EmptyID() {
        // Mock root granted
        mockExecutor.commandResponses["id"] = "uid=0(root)"
        // Mock initial privacy check (if any)
        mockExecutor.commandResponses["settings get global private_dns_mode"] = "off"
        mockExecutor.commandResponses["settings get global private_dns_specifier"] = ""

        ActivityScenario.launch(MainActivity::class.java).use {
            // Clear the ID field
            onView(withId(R.id.etNextDnsId)).perform(replaceText(""), closeSoftKeyboard())

            // Click toggle
            onView(withId(R.id.btnToggle)).perform(click())

            // Verify error log
            onView(withId(R.id.tvLog)).check(matches(withText(containsString("Error: NextDNS ID is required"))))

            // Verify button is still INACTIVE (meaning toggle didn't happen)
            onView(withId(R.id.btnToggle)).check(matches(withText("INACTIVE")))

            // Verify no toggle commands were executed
            // The enable script contains "settings put global private_dns_mode hostname"
            val toggleCommandExecuted = mockExecutor.executedCommands.any {
                it.contains("settings put global private_dns_mode hostname")
            }
            org.junit.Assert.assertFalse("Toggle command should not have been executed", toggleCommandExecuted)
        }
    }
}
