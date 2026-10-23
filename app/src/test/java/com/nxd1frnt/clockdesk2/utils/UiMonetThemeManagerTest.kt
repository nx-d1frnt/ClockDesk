package com.nxd1frnt.clockdesk2.utils

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class UiMonetThemeManagerTest {
    private lateinit var context: Context

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
            .edit().clear().putInt(UiMonetThemeManager.KEY_DYNAMIC_SEED_COLOR, 0xff4285f4.toInt()).commit()
    }

    @Test fun repeatedPageRequestsReuseTheSeededPaletteAcrossContexts() {
        val first = UiMonetThemeManager.getPalette(context)
        val pageContext = context.createConfigurationContext(Configuration(context.resources.configuration))
        repeat(100) {
            assertSame(first, UiMonetThemeManager.getPalette(pageContext))
        }
    }

    @Test fun wallpaperSeedChangesProduceNewColorsAndReturningReusesThePalette() {
        val first = UiMonetThemeManager.getPalette(context)
        val prefs = context.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
        prefs.edit().putInt(UiMonetThemeManager.KEY_DYNAMIC_SEED_COLOR, 0xffff0000.toInt()).commit()
        val changed = UiMonetThemeManager.getPalette(context)
        assertNotSame(first, changed)
        assertNotEquals(first.primary, changed.primary)
        prefs.edit().putInt(UiMonetThemeManager.KEY_DYNAMIC_SEED_COLOR, 0xff4285f4.toInt()).commit()
        assertSame(first, UiMonetThemeManager.getPalette(context))
    }

    @Test fun nightModeHasAnIndependentPalette() {
        fun modeContext(mode: Int) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            }
        )
        val day = modeContext(Configuration.UI_MODE_NIGHT_NO)
        val night = modeContext(Configuration.UI_MODE_NIGHT_YES)
        val dayPalette = UiMonetThemeManager.getPalette(day)
        val nightPalette = UiMonetThemeManager.getPalette(night)
        assertFalse(dayPalette.isDark)
        assertTrue(nightPalette.isDark)
        assertNotEquals(dayPalette.surface, nightPalette.surface)
        assertSame(dayPalette, UiMonetThemeManager.getPalette(day))
        assertSame(nightPalette, UiMonetThemeManager.getPalette(night))
    }

    @Test fun disablingMonetDoesNotReturnACachedSeededPalette() {
        val seeded = UiMonetThemeManager.getPalette(context)
        val prefs = context.getSharedPreferences(UiMonetThemeManager.PREF_FILE, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(UiMonetThemeManager.KEY_UI_MONET_FROM_BACKGROUND, false).commit()
        assertFalse(UiMonetThemeManager.getPalette(context).isCustomSeed)
        prefs.edit().putBoolean(UiMonetThemeManager.KEY_UI_MONET_FROM_BACKGROUND, true).commit()
        assertSame(seeded, UiMonetThemeManager.getPalette(context))
    }
}
