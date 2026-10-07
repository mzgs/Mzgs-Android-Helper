package com.mzgs.helper

internal class AppOpenAdSuppression {
    private var suppressUntilResume = false
    private val activeFlows = mutableMapOf<Any, Any>()
    val isSuppressed: Boolean
        get() = suppressUntilResume || activeFlows.isNotEmpty()
    private var taskId = -1
    private var activityName = ""
    private var generation = 0
    private var adReturn: Pair<Int, String>? = null

    fun beginFlow(owner: Any): Any = Any().also { activeFlows[it] = owner }

    fun cancelFlows(owner: Any) {
        activeFlows.entries.removeAll { it.value === owner }
    }

    fun endFlow(token: Any, taskId: Int, activityName: String): Boolean {
        if (activeFlows.remove(token) == null) return false
        suppress(taskId, activityName)
        return true
    }

    fun armAdReturn(taskId: Int, activityName: String) {
        adReturn = taskId to activityName
    }

    fun cancelAdReturn(taskId: Int, activityName: String) {
        if (adReturn == taskId to activityName) {
            adReturn = null
            if (this.taskId == taskId && this.activityName == activityName) suppressUntilResume = false
        }
    }

    fun suppress(taskId: Int, activityName: String) {
        this.taskId = taskId
        this.activityName = activityName
        suppressUntilResume = true
        generation++
    }

    fun resumed(taskId: Int, activityName: String): Int? {
        if (adReturn == taskId to activityName) {
            suppress(taskId, activityName)
        }
        return generation.takeIf { suppressUntilResume && this.taskId == taskId && this.activityName == activityName }
    }

    fun clear(generation: Int) {
        if (this.generation == generation) suppressUntilResume = false
    }
}
