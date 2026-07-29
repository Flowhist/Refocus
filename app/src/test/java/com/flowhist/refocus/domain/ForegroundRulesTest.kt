package com.flowhist.refocus.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRulesTest {
    private val target = "example.target"
    private val home = "example.launcher"

    @Test
    fun homeSnapshotDoesNotCountAsForeground() {
        assertFalse(
            ForegroundRules.isTargetForeground(
                targetPackage = target,
                visiblePackages = setOf(target, home),
                pictureInPicturePackages = emptySet(),
                eventPackage = home,
                homePackage = home,
            ),
        )
    }

    @Test
    fun pictureInPictureStillCountsOnHome() {
        assertTrue(
            ForegroundRules.isTargetForeground(
                targetPackage = target,
                visiblePackages = setOf(target, home),
                pictureInPicturePackages = setOf(target),
                eventPackage = home,
                homePackage = home,
            ),
        )
    }

    @Test
    fun systemUiTemporarilyHidesOverlayWithoutEndingVisibleSession() {
        assertTrue(
            ForegroundRules.isTargetForeground(
                targetPackage = target,
                visiblePackages = setOf(target),
                pictureInPicturePackages = emptySet(),
                eventPackage = ForegroundRules.SYSTEM_UI_PACKAGE,
                homePackage = home,
            ),
        )
        assertTrue(ForegroundRules.isOverlayBlocked(ForegroundRules.SYSTEM_UI_PACKAGE))
    }

    @Test
    fun missingTargetEndsSession() {
        assertFalse(
            ForegroundRules.isTargetForeground(
                targetPackage = target,
                visiblePackages = setOf(home),
                pictureInPicturePackages = emptySet(),
                eventPackage = ForegroundRules.SYSTEM_UI_PACKAGE,
                homePackage = home,
            ),
        )
    }
}
