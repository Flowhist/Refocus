package com.flowhist.refocus.domain

object ForegroundRules {
    const val SYSTEM_UI_PACKAGE = "com.android.systemui"

    fun isTargetForeground(
        targetPackage: String,
        visiblePackages: Set<String>,
        pictureInPicturePackages: Set<String>,
        eventPackage: String?,
        homePackage: String?,
    ): Boolean {
        if (targetPackage in pictureInPicturePackages) return true
        if (targetPackage !in visiblePackages) return false
        return eventPackage == null || homePackage == null || eventPackage != homePackage
    }

    fun isExitSurface(eventPackage: String?, homePackage: String?): Boolean =
        eventPackage == SYSTEM_UI_PACKAGE ||
            (homePackage != null && eventPackage == homePackage)

    fun isOverlayBlocked(eventPackage: String?): Boolean =
        eventPackage == SYSTEM_UI_PACKAGE
}
