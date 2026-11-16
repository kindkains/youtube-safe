package de.safetube.core.gate

import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType

/** Eine Regel, reduziert auf das, was die Entscheidung braucht. childId = null: gilt für alle Kinder. */
data class RuleView(
    val childId: Int?,
    val targetType: TargetType,
    val targetId: String,
    val effect: Effect,
)

enum class GateReason(val wire: String) {
    REWARD_ALL("reward_all"),
    VIDEO_BLOCK("video_block"),
    VIDEO_ALLOW("video_allow"),
    CHANNEL_BLOCK("channel_block"),
    CHANNEL_ALLOW("channel_allow"),
    REWARD("reward"),
    DEFAULT("default"),
}

data class GateDecision(val allowed: Boolean, val reason: GateReason)

/**
 * Darf ein Kind ein Video sehen? Gleiche Logik wie server/app/gate.py, beide gegen
 * shared/gate-cases.json getestet.
 *
 * Reihenfolge (erste zutreffende Stufe gewinnt):
 * 1. Belohnungsmodus mit „auch Gesperrtes“ → erlaubt
 * 2./3. Video-Regel (block / allow)
 * 4./5. Kanal-Regel (block / allow)
 * 6. Belohnungsmodus → erlaubt
 * 7. sonst → gesperrt
 * Innerhalb einer Stufe schlägt die Regel des Kindes die Regel für alle Kinder.
 */
object ContentGate {

    fun decide(
        rules: List<RuleView>,
        childId: Int,
        videoId: String,
        channelId: String,
        rewardActive: Boolean,
        rewardIncludesBlocked: Boolean,
    ): GateDecision {
        if (rewardActive && rewardIncludesBlocked) return GateDecision(true, GateReason.REWARD_ALL)

        when (effectiveEffect(rules, childId, TargetType.VIDEO, videoId)) {
            Effect.BLOCK -> return GateDecision(false, GateReason.VIDEO_BLOCK)
            Effect.ALLOW -> return GateDecision(true, GateReason.VIDEO_ALLOW)
            null -> Unit
        }
        when (effectiveEffect(rules, childId, TargetType.CHANNEL, channelId)) {
            Effect.BLOCK -> return GateDecision(false, GateReason.CHANNEL_BLOCK)
            Effect.ALLOW -> return GateDecision(true, GateReason.CHANNEL_ALLOW)
            null -> Unit
        }
        if (rewardActive) return GateDecision(true, GateReason.REWARD)
        return GateDecision(false, GateReason.DEFAULT)
    }

    private fun effectiveEffect(rules: List<RuleView>, childId: Int, type: TargetType, targetId: String): Effect? {
        var specific: RuleView? = null
        var general: RuleView? = null
        for (rule in rules) {
            if (rule.targetType != type || rule.targetId != targetId) continue
            when (rule.childId) {
                null -> general = rule
                childId -> specific = rule
            }
        }
        return (specific ?: general)?.effect
    }
}
