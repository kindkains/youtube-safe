package de.safetube.parent.reward

import de.safetube.parent.testing.FakeParentApi
import de.safetube.parent.testing.MainDispatcherRule
import de.safetube.parent.testing.Samples
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RewardViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val api = FakeParentApi().apply { childrenList += Samples.child(1, "Mia") }

    private fun viewModel() = RewardViewModel(api, childId = 1, clock = { Samples.NOW }).apply { load() }

    @Test
    fun `Vorbelegung eine Stunde, Endzeit in einer Stunde auf Viertelstunde gerundet`() {
        val state = viewModel().state.value

        assertEquals("Mia", state.child!!.name)
        assertEquals("1", state.hours)
        // NOW = 10:00 UTC = 11:00 Berlin → 12:00
        assertEquals(LocalDate.of(2026, 11, 15), state.date)
        assertEquals(LocalTime.of(12, 0), state.time)
    }

    @Test
    fun `Dauer starten`() {
        val vm = viewModel()
        vm.setDays("0")
        vm.setHours("1")
        vm.setMinutes("30")
        vm.setIncludeBlocked(true)

        vm.start()

        assertEquals(90, api.lastReward!!.minutes)
        assertNull(api.lastReward!!.until)
        assertTrue(api.lastReward!!.includeBlocked)
        assertTrue(vm.state.value.child!!.rewardActive)
        assertTrue(vm.state.value.message!!.startsWith("Belohnungsmodus läuft bis"))
    }

    @Test
    fun `ungültige Dauer geht nicht an den Server`() {
        val vm = viewModel()
        vm.setHours("0")

        vm.start()

        assertEquals("Bitte eine Dauer zwischen 1 Minute und 30 Tagen angeben.", vm.state.value.error)
        assertFalse("startReward 1" in api.calls)
    }

    @Test
    fun `Endzeit starten`() {
        val vm = viewModel()
        vm.setMode(RewardMode.UNTIL)
        vm.setDate(LocalDate.of(2026, 11, 15))
        vm.setTime(LocalTime.of(18, 30))

        vm.start()

        assertEquals(Instant.parse("2026-11-15T17:30:00Z"), api.lastReward!!.until)
        assertNull(api.lastReward!!.minutes)
    }

    @Test
    fun `Endzeit in der Vergangenheit`() {
        val vm = viewModel()
        vm.setMode(RewardMode.UNTIL)
        vm.setTime(LocalTime.of(9, 0))

        vm.start()

        assertEquals("Die Endzeit muss in der Zukunft liegen (höchstens 30 Tage).", vm.state.value.error)
    }

    @Test
    fun `nur Ziffern in den Dauerfeldern`() {
        val vm = viewModel()

        vm.setMinutes("4a5")

        assertEquals("45", vm.state.value.minutes)
    }

    @Test
    fun `sofort beenden`() {
        val vm = viewModel()
        vm.start()

        vm.stop()

        assertFalse(vm.state.value.child!!.rewardActive)
        assertEquals("Belohnungsmodus beendet", vm.state.value.message)
    }
}
