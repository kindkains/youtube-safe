package de.safetube.core.api

import java.time.Instant

/** Alle Aufrufe der Eltern-App an den Server (`/api/v1/parent/…`). */
interface ParentApi {
    suspend fun login(email: String, password: String): LoginResponse
    suspend fun logout()
    suspend fun me(): Parent
    suspend fun updateMe(update: MeUpdate): Parent
    suspend fun pushInfo(): PushInfo
    suspend fun parents(): List<Parent>
    suspend fun createParent(data: ParentCreate): Parent

    suspend fun children(): List<Child>
    suspend fun createChild(name: String): Child
    suspend fun updateChild(childId: Int, update: ChildUpdate): Child
    /** Löscht das Kind samt Geräten, eigenen Regeln, Anfragen und Schauzeit (Festlegung 16). */
    suspend fun deleteChild(childId: Int)
    suspend fun devices(childId: Int): List<Device>
    suspend fun unpairDevice(deviceId: Int)
    suspend fun pairingCode(childId: Int): PairingCode

    /** [childId] gesetzt: Regeln des Kindes plus die für alle; [globalOnly]: nur die für alle. */
    suspend fun rules(childId: Int? = null, globalOnly: Boolean = false): List<Rule>
    suspend fun createRule(rule: RuleCreate): Rule
    suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule
    suspend fun deleteRule(ruleId: Int)

    suspend fun requests(filter: RequestFilter = RequestFilter()): List<ContentRequest>
    suspend fun request(requestId: Int): ContentRequest
    suspend fun decide(requestId: Int, decision: Decision): ContentRequest

    suspend fun startReward(childId: Int, reward: RewardStart): Child
    suspend fun stopReward(childId: Int): Child

    suspend fun history(childId: Int, limit: Int = 50, before: Instant? = null): List<HistoryEntry>
    suspend fun stats(childId: Int, days: Int = 7): Stats
}

class HttpParentApi(private val http: HttpTransport) : ParentApi {

    override suspend fun login(email: String, password: String): LoginResponse =
        http.post("/parent/login", LoginRequest(email, password))

    override suspend fun logout() = http.send("POST", "/parent/logout")

    override suspend fun me(): Parent = http.get("/parent/me")

    override suspend fun updateMe(update: MeUpdate): Parent = http.patch("/parent/me", update)

    override suspend fun pushInfo(): PushInfo = http.get("/parent/push")

    override suspend fun parents(): List<Parent> = http.get("/parent/parents")

    override suspend fun createParent(data: ParentCreate): Parent = http.post("/parent/parents", data)

    override suspend fun children(): List<Child> = http.get("/parent/children")

    override suspend fun createChild(name: String): Child = http.post("/parent/children", ChildCreate(name))

    override suspend fun updateChild(childId: Int, update: ChildUpdate): Child =
        http.patch("/parent/children/$childId", update)

    override suspend fun deleteChild(childId: Int) = http.send("DELETE", "/parent/children/$childId")

    override suspend fun devices(childId: Int): List<Device> = http.get("/parent/children/$childId/devices")

    override suspend fun unpairDevice(deviceId: Int) = http.send("DELETE", "/parent/devices/$deviceId")

    override suspend fun pairingCode(childId: Int): PairingCode =
        http.postEmpty("/parent/children/$childId/pairing-code")

    override suspend fun rules(childId: Int?, globalOnly: Boolean): List<Rule> =
        http.get(
            "/parent/rules",
            mapOf(
                "child_id" to childId?.toString(),
                "global_only" to if (globalOnly) "true" else null,
            ),
        )

    override suspend fun createRule(rule: RuleCreate): Rule = http.post("/parent/rules", rule)

    override suspend fun updateRule(ruleId: Int, update: RuleUpdate): Rule = http.patch("/parent/rules/$ruleId", update)

    override suspend fun deleteRule(ruleId: Int) = http.send("DELETE", "/parent/rules/$ruleId")

    override suspend fun requests(filter: RequestFilter): List<ContentRequest> =
        http.get(
            "/parent/requests",
            mapOf(
                "child_id" to filter.childId?.toString(),
                "status" to filter.status?.wire,
                "since" to filter.since?.toString(),
                "until" to filter.until?.toString(),
                "limit" to filter.limit.toString(),
            ),
        )

    override suspend fun request(requestId: Int): ContentRequest = http.get("/parent/requests/$requestId")

    override suspend fun decide(requestId: Int, decision: Decision): ContentRequest =
        http.post("/parent/requests/$requestId/decision", decision)

    override suspend fun startReward(childId: Int, reward: RewardStart): Child =
        http.post("/parent/children/$childId/reward", reward)

    override suspend fun stopReward(childId: Int): Child = http.deleteFor("/parent/children/$childId/reward")

    override suspend fun history(childId: Int, limit: Int, before: Instant?): List<HistoryEntry> =
        http.get(
            "/parent/children/$childId/history",
            mapOf("limit" to limit.toString(), "before" to before?.toString()),
        )

    override suspend fun stats(childId: Int, days: Int): Stats =
        http.get("/parent/children/$childId/stats", mapOf("days" to days.toString()))
}
