package com.mzgs.helper

import org.junit.Assert.*
import org.junit.Test

class AppOpenAdSuppressionTest {
    @Test fun explicitFlowsStayBlockedUntilCompletionAndThroughTheReturningResume() {
        val suppression = AppOpenAdSuppression()
        val owner = Any()
        val permission = suppression.beginFlow(owner)
        val consent = suppression.beginFlow(owner)
        assertTrue(suppression.isSuppressed)
        assertNull(suppression.resumed(1, "Editor")) // Resume alone cannot finish a dialog.

        suppression.suppress(1, "Editor") // Existing picker/ad guards cannot release active flows.
        val earlyClear = requireNotNull(suppression.resumed(1, "Editor"))
        suppression.armAdReturn(1, "Editor")
        suppression.cancelAdReturn(1, "Editor")
        suppression.clear(earlyClear)
        assertTrue(suppression.isSuppressed)

        assertTrue(suppression.endFlow(permission, 1, "Editor"))
        suppression.clear(requireNotNull(suppression.resumed(1, "Editor")))
        assertFalse(suppression.endFlow(permission, 1, "Editor")) // Completion is idempotent.
        assertTrue(suppression.isSuppressed) // Consent is still open.

        assertTrue(suppression.endFlow(consent, 1, "Editor"))
        assertNull(suppression.resumed(2, "Editor"))
        assertNull(suppression.resumed(1, "OtherActivity"))
        val returned = requireNotNull(suppression.resumed(1, "Editor"))
        assertTrue(suppression.isSuppressed) // Foreground callbacks still skip the ad.
        suppression.clear(returned)
        assertFalse(suppression.isSuppressed)
        assertNull(suppression.resumed(1, "Editor")) // Next normal Home return is eligible.

        val failedLaunch = suppression.beginFlow(owner)
        suppression.endFlow(failedLaunch, 1, "Editor")
        suppression.clear(requireNotNull(suppression.resumed(1, "Editor")))
        assertFalse(suppression.isSuppressed) // No lifecycle transition is required to recover.

        val destroyedFlow = suppression.beginFlow(owner)
        val otherOwner = Any()
        val otherFlow = suppression.beginFlow(otherOwner)
        suppression.cancelFlows(owner)
        assertTrue(suppression.isSuppressed) // Another Activity's flow is still open.
        assertFalse(suppression.endFlow(destroyedFlow, 1, "Editor"))
        suppression.cancelFlows(otherOwner)
        assertFalse(suppression.isSuppressed)
        assertFalse(suppression.endFlow(otherFlow, 2, "OtherActivity"))
    }

    @Test fun pickerAndAdReturnsStaySuppressedThroughResumeButNotTheNextHomeReturn() {
        val suppression = AppOpenAdSuppression()
        assertFalse(suppression.isSuppressed)
        suppression.suppress(1, "Editor")
        assertNull(suppression.resumed(1, "AdActivity"))
        assertNull(suppression.resumed(2, "Editor"))
        assertTrue(suppression.isSuppressed)
        val returnGeneration = requireNotNull(suppression.resumed(1, "Editor"))
        assertTrue(suppression.isSuppressed) // Foreground callbacks run before the posted clear.
        suppression.clear(returnGeneration)
        assertFalse(suppression.isSuppressed)
        assertNull(suppression.resumed(1, "Editor")) // Subsequent Home return allows app open.

        suppression.suppress(1, "Editor") // Dismissal after host resume also clears.
        val dismissalGeneration = requireNotNull(suppression.resumed(1, "Editor"))
        suppression.suppress(1, "Editor") // A new flow starts before the previous clear runs.
        suppression.clear(dismissalGeneration)
        assertTrue(suppression.isSuppressed)
        suppression.clear(requireNotNull(suppression.resumed(1, "Editor")))
        assertFalse(suppression.isSuppressed)

        suppression.armAdReturn(1, "Editor")
        assertFalse(suppression.isSuppressed) // Preserve existing behavior while the ad shows.
        assertNull(suppression.resumed(1, "AdActivity"))
        val earlyResume = requireNotNull(suppression.resumed(1, "Editor"))
        assertTrue(suppression.isSuppressed) // Host can resume before the SDK reports dismissal.
        suppression.clear(earlyResume)
        suppression.cancelAdReturn(1, "Editor") // SDK reports dismissal after that resume.
        assertNull(suppression.resumed(1, "Editor"))

        suppression.armAdReturn(1, "Editor")
        suppression.cancelAdReturn(1, "Editor") // Failed display must leave app-open fallback available.
        assertNull(suppression.resumed(1, "Editor"))
        assertFalse(suppression.isSuppressed)

        suppression.armAdReturn(1, "Editor")
        requireNotNull(suppression.resumed(1, "Editor"))
        suppression.cancelAdReturn(1, "Editor") // Failure after resume still permits fallback.
        assertFalse(suppression.isSuppressed)
    }
}
