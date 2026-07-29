package com.flowhist.refocus.domain

object ScoreRules {
    fun score(penaltyApplied: Boolean, goalCompleted: Boolean): Int =
        when {
            penaltyApplied -> -1
            goalCompleted -> 1
            else -> 0
        }
}
