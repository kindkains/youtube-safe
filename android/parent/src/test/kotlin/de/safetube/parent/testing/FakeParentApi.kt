package de.safetube.parent.testing

import de.safetube.core.api.Child
import de.safetube.core.api.ChildUpdate
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.Decision
import de.safetube.core.api.DecisionAction
import de.safetube.core.api.Device
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.LoginResponse
import de.safetube.core.api.MeUpdate
import de.safetube.core.api.PairingCode
import de.safetube.core.api.Parent
import de.safetube.core.api.ParentApi
import de.safetube.core.api.ParentCreate
import de.safetube.core.api.PushInfo
import de.safetube.core.api.RequestFilter
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.RewardStart
import de.safetube.core.api.Rule
import de.safetube.core.api.RuleCreate
import de.safetube.core.api.RuleUpdate
import de.safetube.core.api.Stats
import java.time.Instant

/** Server im Speicher. [calls] protokolliert jeden Aufruf; [failure] wird beim nächsten Aufruf geworfen. */
class FakeParentApi : ParentApi {
    val calls = mutableListOf<String>()
    var failure: Exception? = null

    var loginResult = LoginResponse("token-1", Samples.parent())
    var pushInfo = PushInfo("https://push.example.org", "yts-test")
    var me: Parent = Samples.parent()
    val parentsList = mutableListOf(Samples.parent())
    val childrenList = mutableListOf<Child>()
    val devicesByChild = mutableMapOf<Int, MutableList<Device>>()
    val rulesList = mutableListOf<Rule>()
    val requestsList = mutableListOf<ContentRequest>()
    val historyByChild = mutableMapOf<Int, List<HistoryEntry>>()
    val statsByChild = mutableMapOf<Int, Stats>()
    var pairing = PairingCode("AB12CD34", Samples.NOW.plusSeconds(600), """{"server": "https://x", "code": "AB12CD34"}""")

    var lastDecision: Pair<Int, Decision>? = null
    var lastRequestFilter: RequestFilter? = null
    var lastReward: RewardStart? = null
    var lastRuleCreate: RuleCreate? = null
    var lastMeUpdate: MeUpdate? = null

    private var nextId = 1000

    private fun record(call: String) {
        calls += call
        failure?.let {
            failure = null
            throw it
        }
    }

    override suspend fun login(email: String, password: String): LoginResponse {
        record("login $email")
        return loginResult
    }

    override suspend fun logout() = record("logout")

    override suspend fun me(): Parent {
        record("me")
        return me
    }

    override suspend fun updateMe(update: MeUpdate): Parent {
        record("updateMe")
        lastMeUpdate = update
        me = me.copy(
            name = update.name ?: me.name,
            mailNewRequest = update.mailNewRequest ?: me.mailNewRequest,
            mailAlerts = update.mailAlerts ?: me.mailAlerts,
        )
        return me
    }

    override suspend fun pushInfo(): PushInfo {
        record("pushInfo")
        return pushInfo
    }

    override suspend fun parents(): List<Parent> {
        record("parents")
        return parentsList.toList()
    }

    override suspend fun createParent(data: ParentCreate): Parent {
        record("createParent ${data.email}")
        val parent = Parent(nextId++, data.name, data.email, true, true)
        parentsList += parent
        return parent
    }

    override suspend fun children(): List<Child> {
        record("children")
        return childrenList.toList()
    }

    override suspend fun createChild(name: String): Child {
        record("createChild $name")
        val child = Samples.child(nextId++, name)
        childrenList += child
        return child
    }

    override suspend fun updateChild(childId: Int, update: ChildUpdate): Child {
        record("updateChild $childId")
        return changeChild(childId) {
            it.copy(
                name = update.name ?: it.name,
                displayMode = update.displayMode ?: it.displayMode,
                shortsEnabled = update.shortsEnabled ?: it.shortsEnabled,
            )
        }
    }

    override suspend fun deleteChild(childId: Int) {
        record("deleteChild $childId")
        childrenList.removeAll { it.id == childId }
        devicesByChild.remove(childId)
    }

    override suspend fun devices(childId: Int): List<Device> {
        record("devices $childId")
        return devicesByChild[childId].orEmpty().toList()
    }

    override suspend fun unpairDevice(deviceId: Int) {
        record("unpair $deviceId")
        devicesByChild.values.forEach { list ->
            list.replaceAll { if (it.id == deviceId) it.copy(unpairedAt = Samples.NOW) else it }
        }
    }

    override suspend fun pairingCode(childId: Int): PairingCode {
        record("pairingCode $childId")
        return pairing
    }

    override suspend fun rules(childId: Int?, globalOnly: Boolean): List<Rule> {
        record("rules $childId $globalOnly")
        return when {
            globalOnly -> rulesList.filter { it.childId == null }
            childId != null -> rulesList.filter { it.childId == childId || it.childId == null }
            else -> rulesList.toList()
        }
    }

    override suspend fun createRule(rule: RuleCreate): Rule {
        record("createRule ${rule.childId} ${rule.targetType} ${rule.targetId} ${rule.effect}")
        lastRuleCreate = rule
        rulesList.removeAll { it.childId == rule.childId && it.targetType == rule.targetType && it.targetId == rule.targetId }
        val created = Samples.rule(nextId++, rule.childId, rule.targetType, rule.targetId, rule.effect, rule.title, rule.channelName, rule.note)
        rulesList += created
        return created
    }

    override suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule {
        record("updateRule $ruleId")
        val index = rulesList.indexOfFirst { it.id == ruleId }
        val old = rulesList[index]
        val changed = old.copy(effect = update.effect ?: old.effect, note = update.note ?: old.note)
        rulesList[index] = changed
        return changed
    }

    override suspend fun deleteRule(ruleId: Int) {
        record("deleteRule $ruleId")
        rulesList.removeAll { it.id == ruleId }
    }

    override suspend fun requests(filter: RequestFilter): List<ContentRequest> {
        record("requests")
        lastRequestFilter = filter
        return requestsList
            .filter { filter.childId == null || it.childId == filter.childId }
            .filter { filter.status == null || it.status == filter.status }
            .filter { filter.since == null || !it.createdAt.isBefore(filter.since) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun request(requestId: Int): ContentRequest {
        record("request $requestId")
        return requestsList.first { it.id == requestId }
    }

    override suspend fun decide(requestId: Int, decision: Decision): ContentRequest {
        record("decide $requestId ${decision.action} ${decision.scope}")
        lastDecision = requestId to decision
        val index = requestsList.indexOfFirst { it.id == requestId }
        val approved = decision.action == DecisionAction.APPROVE || decision.action == DecisionAction.APPROVE_VIDEO_ONLY
        val changed = requestsList[index].copy(
            status = if (approved) RequestStatus.APPROVED else RequestStatus.REJECTED,
            reason = decision.reason,
            decidedByName = me.name,
            decidedAt = Samples.NOW,
        )
        requestsList[index] = changed
        return changed
    }

    override suspend fun startReward(childId: Int, reward: RewardStart): Child {
        record("startReward $childId")
        lastReward = reward
        val until = reward.until ?: Samples.NOW.plusSeconds(60L * (reward.minutes ?: 0))
        return changeChild(childId) {
            it.copy(rewardActive = true, rewardUntil = until, rewardIncludesBlocked = reward.includeBlocked)
        }
    }

    override suspend fun stopReward(childId: Int): Child {
        record("stopReward $childId")
        return changeChild(childId) { it.copy(rewardActive = false, rewardUntil = null, rewardIncludesBlocked = false) }
    }

    override suspend fun history(childId: Int, limit: Int, before: Instant?): List<HistoryEntry> {
        record("history $childId ${before ?: "-"}")
        return historyByChild[childId].orEmpty()
            .filter { before == null || it.startedAt.isBefore(before) }
            .take(limit)
    }

    override suspend fun stats(childId: Int, days: Int): Stats {
        record("stats $childId $days")
        return statsByChild[childId] ?: Samples.stats()
    }

    private fun changeChild(childId: Int, change: (Child) -> Child): Child {
        val index = childrenList.indexOfFirst { it.id == childId }
        val changed = change(childrenList[index])
        childrenList[index] = changed
        return changed
    }
}
