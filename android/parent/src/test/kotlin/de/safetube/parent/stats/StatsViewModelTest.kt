package de.safetube.parent.stats

import de.safetube.core.api.ChannelStat
import de.safetube.core.api.DayStat
import de.safetube.core.api.Stats
import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StatsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply {
        childrenList += Samples.child(1, "Mia")
        statsByChild[1] = Stats(
            days = listOf(DayStat("2026-11-14", 1200, 0), DayStat("2026-11-15", 2400, 1200)),
            topChannels = listOf(ChannelStat("UCmaus", "Die Maus", 3000), ChannelStat("UClego", "LEGO", 600)),
            totalSeconds = 3600,
            rewardSeconds = 900,
        )
    }

    @Test
    fun `7 Tage mit Kennzahlen, umschalten auf 30`() {
        val vm = StatsViewModel(api, childId = 1).apply { load() }

        val state = vm.state.value
        assertEquals("Mia", state.childName)
        assertEquals(2400, state.maxDaySeconds)
        assertEquals(25, state.rewardPercent)
        assertEquals("Die Maus", state.stats!!.topChannels.first().channelName)
        assertTrue("stats 1 7" in api.calls)

        vm.setDays(30)

        assertEquals(30, vm.state.value.days)
        assertTrue("stats 1 30" in api.calls)
    }

    @Test
    fun `ohne Schauzeit kein Teilen durch null`() {
        api.statsByChild[1] = Samples.stats(today = 0)

        val vm = StatsViewModel(api, childId = 1).apply { load() }

        assertEquals(0, vm.state.value.rewardPercent)
        assertEquals(0, vm.state.value.maxDaySeconds)
    }
}
