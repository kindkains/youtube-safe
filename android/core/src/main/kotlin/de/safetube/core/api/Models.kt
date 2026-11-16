@file:UseSerializers(InstantSerializer::class)

package de.safetube.core.api

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

// Spiegel von server/app/schemas.py. Feldnamen dort ändern = hier ändern.

// --- Aufzählungen -------------------------------------------------------------------

@Serializable
enum class TargetType(val wire: String) {
    @SerialName("channel") CHANNEL("channel"),
    @SerialName("video") VIDEO("video");

    companion object {
        fun fromWire(value: String): TargetType = entries.first { it.wire == value }
    }
}

@Serializable
enum class Effect(val wire: String) {
    @SerialName("allow") ALLOW("allow"),
    @SerialName("block") BLOCK("block");

    companion object {
        fun fromWire(value: String): Effect = entries.first { it.wire == value }
    }
}

@Serializable
enum class DisplayMode {
    @SerialName("compact") COMPACT,
    @SerialName("full") FULL,
}

@Serializable
enum class RequestStatus(val wire: String) {
    @SerialName("open") OPEN("open"),
    @SerialName("approved") APPROVED("approved"),
    @SerialName("rejected") REJECTED("rejected"),
}

@Serializable
enum class DecisionAction {
    @SerialName("approve") APPROVE,
    @SerialName("approve_video_only") APPROVE_VIDEO_ONLY,
    @SerialName("reject") REJECT,
    @SerialName("reject_and_block") REJECT_AND_BLOCK,
}

@Serializable
enum class Scope {
    @SerialName("child") CHILD,
    @SerialName("all") ALL,
}

// --- Eltern -------------------------------------------------------------------------

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class Parent(
    val id: Int,
    val name: String,
    val email: String,
    @SerialName("mail_new_request") val mailNewRequest: Boolean,
    @SerialName("mail_alerts") val mailAlerts: Boolean,
)

@Serializable
data class LoginResponse(val token: String, val parent: Parent)

@Serializable
data class ParentCreate(val name: String, val email: String, val password: String)

@Serializable
data class MeUpdate(
    val name: String? = null,
    @SerialName("mail_new_request") val mailNewRequest: Boolean? = null,
    @SerialName("mail_alerts") val mailAlerts: Boolean? = null,
    val password: String? = null,
)

@Serializable
data class PushInfo(
    @SerialName("ntfy_url") val ntfyUrl: String,
    @SerialName("ntfy_topic") val ntfyTopic: String,
)

// --- Kinder und Geräte --------------------------------------------------------------

@Serializable
data class Child(
    val id: Int,
    val name: String,
    @SerialName("display_mode") val displayMode: DisplayMode,
    @SerialName("shorts_enabled") val shortsEnabled: Boolean,
    @SerialName("reward_active") val rewardActive: Boolean,
    @SerialName("reward_until") val rewardUntil: Instant?,
    @SerialName("reward_includes_blocked") val rewardIncludesBlocked: Boolean,
)

@Serializable
data class ChildCreate(val name: String)

@Serializable
data class ChildUpdate(
    val name: String? = null,
    @SerialName("display_mode") val displayMode: DisplayMode? = null,
    @SerialName("shorts_enabled") val shortsEnabled: Boolean? = null,
)

@Serializable
data class Device(
    val id: Int,
    @SerialName("child_id") val childId: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("paired_at") val pairedAt: Instant,
    @SerialName("last_seen") val lastSeen: Instant,
    @SerialName("unpaired_at") val unpairedAt: Instant?,
)

@Serializable
data class PairingCode(
    val code: String,
    @SerialName("expires_at") val expiresAt: Instant,
    @SerialName("qr_payload") val qrPayload: String,
)

// --- Regeln -------------------------------------------------------------------------

@Serializable
data class Rule(
    val id: Int,
    @SerialName("child_id") val childId: Int?,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val title: String,
    @SerialName("channel_name") val channelName: String,
    val effect: Effect,
    val note: String,
    @SerialName("created_at") val createdAt: Instant,
    @SerialName("updated_at") val updatedAt: Instant,
)

@Serializable
data class RuleCreate(
    @SerialName("child_id") val childId: Int?,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val effect: Effect,
    val title: String = "",
    @SerialName("channel_name") val channelName: String = "",
    val note: String = "",
)

@Serializable
data class RuleUpdate(val effect: Effect? = null, val note: String? = null)

// --- Anfragen -----------------------------------------------------------------------

@Serializable
data class ContentRequest(
    val id: Int,
    @SerialName("child_id") val childId: Int,
    @SerialName("child_name") val childName: String,
    @SerialName("target_type") val targetType: TargetType,
    @SerialName("target_id") val targetId: String,
    val title: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_name") val channelName: String,
    val url: String,
    @SerialName("thumbnail_url") val thumbnailUrl: String,
    @SerialName("child_message") val childMessage: String,
    @SerialName("source_video_id") val sourceVideoId: String,
    @SerialName("source_video_title") val sourceVideoTitle: String,
    val status: RequestStatus,
    @SerialName("decided_by_name") val decidedByName: String?,
    @SerialName("decided_at") val decidedAt: Instant?,
    val reason: String,
    @SerialName("created_at") val createdAt: Instant,
    /** Beim Anfragen galt eine Sperrregel: das Kind bittet um Aufhebung (Festlegung 15). */
    @SerialName("lifts_block") val liftsBlock: Boolean = false,
)

@Serializable
data class Decision(
    val action: DecisionAction,
    val scope: Scope = Scope.CHILD,
    val reason: String = "",
)

/** Filter für GET /parent/requests; null = nicht filtern. */
data class RequestFilter(
    val childId: Int? = null,
    val status: RequestStatus? = null,
    val since: Instant? = null,
    val until: Instant? = null,
    val limit: Int = 200,
)

// --- Belohnungsmodus ----------------------------------------------------------------

/** Genau eines von [minutes] oder [until] setzen (prüft auch der Server). */
@Serializable
data class RewardStart(
    val minutes: Int? = null,
    val until: Instant? = null,
    @SerialName("include_blocked") val includeBlocked: Boolean = false,
)

// --- Verlauf und Statistik ----------------------------------------------------------

@Serializable
data class HistoryEntry(
    val id: Int,
    @SerialName("video_id") val videoId: String,
    @SerialName("channel_id") val channelId: String,
    val title: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("started_at") val startedAt: Instant,
    @SerialName("played_seconds") val playedSeconds: Int,
    @SerialName("in_reward_mode") val inRewardMode: Boolean,
    @SerialName("allowed_now") val allowedNow: Boolean,
    @SerialName("reason_now") val reasonNow: String,
)

@Serializable
data class DayStat(
    val date: String,
    val seconds: Int,
    @SerialName("reward_seconds") val rewardSeconds: Int,
)

@Serializable
data class ChannelStat(
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_name") val channelName: String,
    val seconds: Int,
)

@Serializable
data class Stats(
    val days: List<DayStat>,
    @SerialName("top_channels") val topChannels: List<ChannelStat>,
    @SerialName("total_seconds") val totalSeconds: Int,
    @SerialName("reward_seconds") val rewardSeconds: Int,
)
