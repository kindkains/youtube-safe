package de.safetube.android.system

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BatteryOptimizationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `erkennt die Ausnahme`() {
        assertFalse(BatteryOptimization.isIgnoring(context))

        shadowOf(context.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(context.packageName, true)

        assertTrue(BatteryOptimization.isIgnoring(context))
    }

    @Test
    fun `Intent fragt direkt für diese App`() {
        val intent = BatteryOptimization.requestIntent(context)

        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }
}
