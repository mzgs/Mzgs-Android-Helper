package com.mzgs.helper

internal class AppOpenAdSuppression {
    var isSuppressed = false
        private set
    private var taskId = -1
    private var activityName = ""
    private var generation = 0
    private var adReturn: Pair<Int, String>? = null

    fun armAdReturn(taskId: Int, activityName: String) {
        adReturn = taskId to activityName
    }

    fun cancelAdReturn(taskId: Int, activityName: String) {
        if (adReturn == taskId to activityName) {
            adReturn = null
            if (this.taskId == taskId && this.activityName == activityName) isSuppressed = false
        }
    }

    fun suppress(taskId: Int, activityName: String) {
        this.taskId = taskId
        this.activityName = activityName
        isSuppressed = true
        generation++
    }

    fun resumed(taskId: Int, activityName: String): Int? {
        if (adReturn == taskId to activityName) {
            suppress(taskId, activityName)
        }
        return generation.takeIf { isSuppressed && this.taskId == taskId && this.activityName == activityName }
    }

    fun clear(generation: Int) {
        if (this.generation == generation) isSuppressed = false
    }
}
