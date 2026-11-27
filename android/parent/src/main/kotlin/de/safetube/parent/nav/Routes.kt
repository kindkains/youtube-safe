package de.safetube.parent.nav

object Routes {
    const val LOGIN = "login"
    const val OVERVIEW = "overview"
    const val REQUESTS = "requests"
    const val REQUEST = "request/{requestId}"
    const val RULES = "rules"
    const val RULE_ADD = "rules/add"
    const val HISTORY = "history/{childId}"
    const val STATS = "stats/{childId}"
    const val REWARD = "reward/{childId}"
    const val CHILD = "child/{childId}"
    const val PARENTS = "parents"

    /** Bereiche mit unterer Leiste. */
    val TOP_LEVEL = setOf(OVERVIEW, REQUESTS, RULES, PARENTS)

    fun request(requestId: Int) = "request/$requestId"
    fun history(childId: Int) = "history/$childId"
    fun stats(childId: Int) = "stats/$childId"
    fun reward(childId: Int) = "reward/$childId"
    fun child(childId: Int) = "child/$childId"
}
