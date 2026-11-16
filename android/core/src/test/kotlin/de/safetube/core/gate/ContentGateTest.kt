package de.safetube.core.gate

import de.safetube.core.api.ApiJson
import de.safetube.core.api.Effect
import de.safetube.core.api.TargetType
import java.io.File
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentGateTest {

    private val cases = run {
        val path = System.getProperty("gateCases") ?: error("Systemeigenschaft gateCases fehlt (siehe core/build.gradle.kts)")
        ApiJson.parseToJsonElement(File(path).readText()).jsonObject.getValue("cases").jsonArray
    }

    @Test
    fun `alle gemeinsamen Fälle aus shared gate-cases json`() {
        assertTrue("zu wenige Fälle gefunden: ${cases.size}", cases.size >= 13)
        for (raw in cases) {
            val case = raw.jsonObject
            val name = case.getValue("name").jsonPrimitive.content
            val rules = case.getValue("rules").jsonArray.map { element ->
                val rule = element.jsonObject
                RuleView(
                    childId = rule.getValue("child_id").jsonPrimitive.intOrNull,
                    targetType = TargetType.fromWire(rule.getValue("target_type").jsonPrimitive.content),
                    targetId = rule.getValue("target_id").jsonPrimitive.content,
                    effect = Effect.fromWire(rule.getValue("effect").jsonPrimitive.content),
                )
            }

            val decision = ContentGate.decide(
                rules = rules,
                childId = 1,
                videoId = case.getValue("video_id").jsonPrimitive.content,
                channelId = case.getValue("channel_id").jsonPrimitive.content,
                rewardActive = case.getValue("reward_active").jsonPrimitive.boolean,
                rewardIncludesBlocked = case.getValue("reward_includes_blocked").jsonPrimitive.boolean,
            )

            val expected = case.getValue("expected").jsonObject
            assertEquals(name, expected.getValue("allowed").jsonPrimitive.boolean, decision.allowed)
            assertEquals(name, expected.getValue("reason").jsonPrimitive.content, decision.reason.wire)
        }
    }
}
