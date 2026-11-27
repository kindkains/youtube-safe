package de.safetube.parent.testing

import de.safetube.core.api.Child
import de.safetube.core.api.ContentRequest
import de.safetube.core.api.DayStat
import de.safetube.core.api.Device
import de.safetube.core.api.DisplayMode
import de.safetube.core.api.Effect
import de.safetube.core.api.HistoryEntry
import de.safetube.core.api.Parent
import de.safetube.core.api.RequestStatus
import de.safetube.core.api.Rule
import de.safetube.core.api.Stats
import de.safetube.core.api.TargetType
import java.time.Instant

/** Beispieldaten für Tests. Zeitpunkt „jetzt“ ist fest. */
object Samples {
    val NOW: Instant = Instant.parse("2026-11-15T10:00:00Z")

    fun parent(id: Int = 1, name: String = "Sandro") = Parent(id, name, "$name@example.org".lowercase(), true, true)

    fun child(id: Int = 1, name: String = "Mia", rewardUntil: Instant? = null, includeBlocked: Boolean = false) = Child(
        id = id,
        name = name,
        displayMode = DisplayMode.COMPACT,
        shortsEnabled = false,
        rewardActive = rewardUntil != null && rewardUntil.isAfter(NOW),
        rewardUntil = rewardUntil,
        rewardIncludesBlocked = includeBlocked,
    )

    fun request(
        id: Int,
        childId: Int = 1,
        type: TargetType = TargetType.VIDEO,
        status: RequestStatus = RequestStatus.OPEN,
        createdAt: Instant = NOW.minusSeconds(60L * id),
        sourceVideoId: String = "",
        liftsBlock: Boolean = false,
        childMessage: String = "",
    ) = ContentRequest(
        id = id,
        childId = childId,
        childName = if (childId == 1) "Mia" else "Antonio",
        targetType = type,
        targetId = if (type == TargetType.VIDEO) "vid$id-aaaaa".take(11) else "UCchannel$id".padEnd(24, 'x'),
        title = "Titel $id",
        channelId = "UCchannel$id".padEnd(24, 'x'),
        channelName = "Kanal $id",
        url = "https://www.youtube.com/watch?v=vid$id",
        thumbnailUrl = "",
        childMessage = childMessage,
        sourceVideoId = sourceVideoId,
        sourceVideoTitle = if (sourceVideoId.isEmpty()) "" else "Ursprungsvideo",
        status = status,
        decidedByName = null,
        decidedAt = null,
        reason = "",
        createdAt = createdAt,
        liftsBlock = liftsBlock,
    )

    fun rule(
        id: Int,
        childId: Int?,
        type: TargetType,
        targetId: String,
        effect: Effect,
        title: String = "",
        channelName: String = "",
        note: String = "",
    ) = Rule(id, childId, type, targetId, title, channelName, effect, note, NOW, NOW)

    fun device(id: Int, childId: Int = 1, unpaired: Boolean = false) =
        Device(id, childId, "0.1.0", NOW.minusSeconds(86400), NOW.minusSeconds(600), if (unpaired) NOW else null)

    fun history(id: Int, startedAt: Instant = NOW.minusSeconds(600L * id), allowed: Boolean = true) = HistoryEntry(
        id = id,
        videoId = "vid$id",
        channelId = "UCchan$id",
        title = "Video $id",
        channelName = "Kanal $id",
        startedAt = startedAt,
        playedSeconds = 120,
        inRewardMode = false,
        allowedNow = allowed,
        reasonNow = if (allowed) "channel_allow" else "default",
    )

    fun stats(today: Int = 0, reward: Int = 0) = Stats(
        days = listOf(DayStat("2026-11-15", today, reward)),
        topChannels = emptyList(),
        totalSeconds = today,
        rewardSeconds = reward,
    )
}
