package ru.abrikosov.cleanrate

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import ru.abrikosov.cleanrate.data.CbrRepository

@RunWith(AndroidJUnit4::class)
class AndroidRuntimeSmokeTest {
    @Test
    fun bundledCbrXmlParsesOnAndroidRuntime() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val snapshot = CbrRepository(context).loadSnapshot()

        assertFalse(snapshot.rates.isEmpty())
        assertEquals(0, BigDecimal.ONE.compareTo(snapshot.rates["USD"]))
    }

    @Test
    fun mainActivityStartsWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }
}
