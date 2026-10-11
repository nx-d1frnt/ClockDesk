package com.nxd1frnt.clockdesk2.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import com.google.android.material.color.MaterialColors
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.Scheme
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputLayout
import com.nxd1frnt.clockdesk2.R

data class UiMonetPalette(
    val isDark: Boolean,
    val isCustomSeed: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val onTertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val error: Int,
    val onError: Int,
    val errorContainer: Int,
    val onErrorContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val outline: Int,
    val outlineVariant: Int,
    val inverseSurface: Int,
    val inverseOnSurface: Int,
    val inversePrimary: Int
)

object UiMonetThemeManager {

    const val PREF_FILE = "ClockDeskPrefs"
    const val KEY_UI_MONET_FROM_BACKGROUND = "ui_monet_from_background"
    const val KEY_DYNAMIC_SEED_COLOR = "dynamic_seed_color"

    fun isUiMonetEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_UI_MONET_FROM_BACKGROUND, true)
    }

    fun getActiveSeedColor(context: Context): Int? {
        if (!isUiMonetEnabled(context)) return null
        val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
        val seed = prefs.getInt(KEY_DYNAMIC_SEED_COLOR, -1)
        return if (seed != -1) seed else null
    }

    fun isNightMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun applyToActivity(activity: Activity) {
        val seedColor = getActiveSeedColor(activity)
        if (seedColor != null) {
            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(seedColor)
                .build()
            DynamicColors.applyToActivityIfAvailable(activity, options)
        } else {
            DynamicColors.applyToActivityIfAvailable(activity)
        }
    }

    /**
     * Applies Monet palette to Settings / Sub-Settings activities (including Android 11 and below
     * where DynamicColors.applyToActivityIfAvailable is a no-op) and automatically themes all
     * child fragments as their views are created.
     */
    fun applyToSettingsActivity(activity: AppCompatActivity) {
        val palette = getPalette(activity)
        activity.window.setBackgroundDrawable(ColorDrawable(palette.surface))

        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content)
        val rootView = contentRoot?.getChildAt(0)
        rootView?.let {
            it.setBackgroundColor(palette.surface)
            applyLiveToViewHierarchy(it, palette)
        }

        activity.findViewById<AppBarLayout>(R.id.appBarLayout)?.let { appBar ->
            appBar.setBackgroundColor(palette.surface)
        }
        activity.findViewById<CollapsingToolbarLayout>(R.id.collapsingToolbar)?.let { collapsing ->
            collapsing.setCollapsedTitleTextColor(palette.onSurface)
            collapsing.setExpandedTitleColor(palette.onSurface)
            collapsing.setContentScrimColor(palette.surfaceContainer)
            collapsing.setStatusBarScrimColor(palette.surfaceContainer)
        }
        activity.findViewById<MaterialToolbar>(R.id.toolbar)?.let { toolbar ->
            toolbar.setTitleTextColor(palette.onSurface)
            toolbar.setSubtitleTextColor(palette.onSurfaceVariant)
            toolbar.setNavigationIconTint(palette.onSurface)
            val menu = toolbar.menu
            for (i in 0 until menu.size()) {
                menu.getItem(i)?.icon?.mutate()?.setTint(palette.onSurface)
            }
        }

        activity.supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(
                    fm: FragmentManager,
                    f: Fragment,
                    v: View,
                    savedInstanceState: Bundle?
                ) {
                    val currentPalette = getPalette(activity)
                    applyLiveToViewHierarchy(v, currentPalette)
                }

                override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                    val currentPalette = getPalette(activity)
                    f.view?.let { applyLiveToViewHierarchy(it, currentPalette) }
                    activity.findViewById<MaterialToolbar>(R.id.toolbar)?.let { toolbar ->
                        val menu = toolbar.menu
                        for (i in 0 until menu.size()) {
                            menu.getItem(i)?.icon?.mutate()?.setTint(currentPalette.onSurface)
                        }
                    }
                }
            },
            true
        )
    }

    fun wrapContext(context: Context): Context {
        var activityContext: Context? = context
        while (activityContext is ContextWrapper && activityContext !is Activity) {
            activityContext = activityContext.baseContext
        }
        val base = activityContext ?: context
        val seedColor = getActiveSeedColor(base)
        return if (seedColor != null) {
            val options = DynamicColorsOptions.Builder()
                .setContentBasedSource(seedColor)
                .build()
            DynamicColors.wrapContextIfAvailable(base, options)
        } else {
            val themed = ContextThemeWrapper(base, R.style.Theme_ClockDesk)
            DynamicColors.wrapContextIfAvailable(themed)
        }
    }

    private fun createSystemMaterialContext(context: Context): Context {
        return try {
            val configContext = context.applicationContext.createConfigurationContext(context.resources.configuration)
            val themed = ContextThemeWrapper(configContext, R.style.Theme_ClockDesk)
            DynamicColors.wrapContextIfAvailable(themed)
        } catch (e: Exception) {
            DynamicColors.wrapContextIfAvailable(context)
        }
    }

    @SuppressLint("RestrictedApi")
    fun getPalette(context: Context, seedColorOverride: Int? = getActiveSeedColor(context)): UiMonetPalette {
        val isDark = isNightMode(context)
        val activeSeed = if (isUiMonetEnabled(context)) seedColorOverride else null

        if (activeSeed != null) {
            val scheme = if (isDark) Scheme.dark(activeSeed) else Scheme.light(activeSeed)
            val core = CorePalette.of(activeSeed)
            val n1 = core.n1

            val surfaceTone = if (isDark) n1.tone(6) else n1.tone(98)
            val surfaceContainerLowest = if (isDark) n1.tone(4) else n1.tone(100)
            val surfaceContainerLow = if (isDark) n1.tone(10) else n1.tone(96)
            val surfaceContainer = if (isDark) n1.tone(12) else n1.tone(94)
            val surfaceContainerHigh = if (isDark) n1.tone(17) else n1.tone(92)
            val surfaceContainerHighest = if (isDark) n1.tone(22) else n1.tone(90)

            return UiMonetPalette(
                isDark = isDark,
                isCustomSeed = true,
                primary = scheme.primary,
                onPrimary = scheme.onPrimary,
                primaryContainer = scheme.primaryContainer,
                onPrimaryContainer = scheme.onPrimaryContainer,
                secondary = scheme.secondary,
                onSecondary = scheme.onSecondary,
                secondaryContainer = scheme.secondaryContainer,
                onSecondaryContainer = scheme.onSecondaryContainer,
                tertiary = scheme.tertiary,
                onTertiary = scheme.onTertiary,
                tertiaryContainer = scheme.tertiaryContainer,
                onTertiaryContainer = scheme.onTertiaryContainer,
                error = scheme.error,
                onError = scheme.onError,
                errorContainer = scheme.errorContainer,
                onErrorContainer = scheme.onErrorContainer,
                background = surfaceTone,
                onBackground = scheme.onBackground,
                surface = surfaceTone,
                onSurface = scheme.onSurface,
                surfaceVariant = scheme.surfaceVariant,
                onSurfaceVariant = scheme.onSurfaceVariant,
                surfaceContainerLowest = surfaceContainerLowest,
                surfaceContainerLow = surfaceContainerLow,
                surfaceContainer = surfaceContainer,
                surfaceContainerHigh = surfaceContainerHigh,
                surfaceContainerHighest = surfaceContainerHighest,
                outline = scheme.outline,
                outlineVariant = scheme.outlineVariant,
                inverseSurface = scheme.inverseSurface,
                inverseOnSurface = scheme.inverseOnSurface,
                inversePrimary = scheme.inversePrimary
            )
        }

        val fallbackCtx = createSystemMaterialContext(context)
        return UiMonetPalette(
            isDark = isDark,
            isCustomSeed = false,
            primary = resolveAttrColor(fallbackCtx, androidx.appcompat.R.attr.colorPrimary, R.color.md_theme_primary),
            onPrimary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnPrimary, R.color.md_theme_onPrimary),
            primaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorPrimaryContainer, R.color.md_theme_primaryContainer),
            onPrimaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.md_theme_onPrimaryContainer),
            secondary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSecondary, R.color.md_theme_secondary),
            onSecondary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnSecondary, R.color.md_theme_onSecondary),
            secondaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSecondaryContainer, R.color.md_theme_secondaryContainer),
            onSecondaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnSecondaryContainer, R.color.md_theme_onSecondaryContainer),
            tertiary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorTertiary, R.color.md_theme_tertiary),
            onTertiary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnTertiary, R.color.md_theme_onTertiary),
            tertiaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorTertiaryContainer, R.color.md_theme_tertiaryContainer),
            onTertiaryContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnTertiaryContainer, R.color.md_theme_onTertiaryContainer),
            error = resolveAttrColor(fallbackCtx, androidx.appcompat.R.attr.colorError, R.color.md_theme_error),
            onError = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnError, R.color.md_theme_onError),
            errorContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorErrorContainer, R.color.md_theme_errorContainer),
            onErrorContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnErrorContainer, R.color.md_theme_onErrorContainer),
            background = resolveAttrColor(fallbackCtx, android.R.attr.colorBackground, R.color.md_theme_background),
            onBackground = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnBackground, R.color.md_theme_onBackground),
            surface = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurface, R.color.md_theme_surface),
            onSurface = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnSurface, R.color.md_theme_onSurface),
            surfaceVariant = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceVariant, R.color.md_theme_surfaceVariant),
            onSurfaceVariant = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnSurfaceVariant, R.color.md_theme_onSurfaceVariant),
            surfaceContainerLowest = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceContainerLowest, R.color.md_theme_surfaceContainerLowest),
            surfaceContainerLow = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceContainerLow, R.color.md_theme_surfaceContainerLow),
            surfaceContainer = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceContainer, R.color.md_theme_surfaceContainer),
            surfaceContainerHigh = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceContainerHigh, R.color.md_theme_surfaceContainerHigh),
            surfaceContainerHighest = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceContainerHighest, R.color.md_theme_surfaceContainerHighest),
            outline = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOutline, R.color.md_theme_outline),
            outlineVariant = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOutlineVariant, R.color.md_theme_outlineVariant),
            inverseSurface = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorSurfaceInverse, R.color.md_theme_inverseSurface),
            inverseOnSurface = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorOnSurfaceInverse, R.color.md_theme_inverseOnSurface),
            inversePrimary = resolveAttrColor(fallbackCtx, com.google.android.material.R.attr.colorPrimaryInverse, R.color.md_theme_inversePrimary)
        )
    }

    private fun resolveAttrColor(context: Context, attrRes: Int, fallbackRes: Int): Int {
        val tv = TypedValue()
        if (context.theme.resolveAttribute(attrRes, tv, true)) {
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data
            }
            if (tv.resourceId != 0) {
                return try {
                    ContextCompat.getColor(context, tv.resourceId)
                } catch (e: Exception) {
                    ContextCompat.getColor(context, fallbackRes)
                }
            }
        }
        return MaterialColors.getColor(context, attrRes, ContextCompat.getColor(context, fallbackRes))
    }

    /**
     * Styles an [AlertDialog] window, title, message, list choices, and action buttons
     * so dialogs respect the Monet palette on Android 11 and below.
     */
    fun styleAlertDialog(dialog: AlertDialog, palette: UiMonetPalette = getPalette(dialog.context)) {
        dialog.window?.decorView?.let { decor ->
            // Tint the dialog surface background while preserving its rounded shape
            val contentPanel = dialog.findViewById<View>(androidx.appcompat.R.id.parentPanel)
            val bgTarget = (contentPanel?.parent as? View) ?: contentPanel ?: decor
            bgTarget.background?.mutate()?.setTint(palette.surfaceContainerHigh)
            dialog.window?.setBackgroundDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 28f * dialog.context.resources.displayMetrics.density
                    setColor(palette.surfaceContainerHigh)
                }
            )
        }

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.setTextColor(palette.onSurface)
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(palette.onSurfaceVariant)

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(palette.primary)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(palette.primary)
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(palette.primary)

        dialog.listView?.let { lv ->
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            val tint = ColorStateList(states, intArrayOf(palette.primary, palette.onSurfaceVariant))
            lv.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    (child as? TextView)?.let { tv ->
                        tv.setTextColor(palette.onSurface)
                        tv.compoundDrawableTintList = tint
                    }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })
            for (i in 0 until lv.childCount) {
                (lv.getChildAt(i) as? TextView)?.let { tv ->
                    tv.setTextColor(palette.onSurface)
                    tv.compoundDrawableTintList = tint
                }
            }
        }
    }

    /**
     * Updates MainActivity's UI surfaces live in-place without calling recreate().
     * Only touches UI chrome (dock, FAB, side sheets, background sheet, crop overlay),
     * leaving desk widgets untouched.
     */
    fun applyLiveToMainActivity(activity: Activity) {
        applyToActivity(activity)
        val palette = getPalette(activity)

        // 1. Background Customization FAB
        activity.findViewById<FloatingActionButton>(R.id.background_customization_fab)?.let { fab ->
            fab.backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
            fab.imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
            fab.setRippleColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.onPrimaryContainer, 32)))
        }

        // 2. Edit Mode Dock
        activity.findViewById<MaterialCardView>(R.id.edit_mode_action_bar)?.let { dock ->
            dock.setCardBackgroundColor(palette.surfaceContainer)
            dock.strokeColor = palette.outlineVariant
            applyLiveToViewHierarchy(dock, palette)
        }

        // 3. Customization Side Sheet
        activity.findViewById<ViewGroup>(R.id.side_sheet)?.let { sheet ->
            sheet.setBackgroundColor(palette.surface)
            if (sheet.childCount > 0) {
                sheet.getChildAt(0)?.setBackgroundColor(palette.surface)
            }
            applyLiveToViewHierarchy(sheet, palette)
        }

        // 4. Widget Gallery Side Sheet
        activity.findViewById<ViewGroup>(R.id.widget_gallery_side_sheet)?.let { sheet ->
            sheet.setBackgroundColor(palette.surface)
            if (sheet.childCount > 0) {
                sheet.getChildAt(0)?.setBackgroundColor(palette.surface)
            }
            applyLiveToViewHierarchy(sheet, palette)
        }

        // 5. Background Bottom Sheet
        activity.findViewById<ViewGroup>(R.id.background_bottom_sheet)?.let { sheet ->
            applyLiveToViewHierarchy(sheet, palette)
        }

        // 6. Crop Mode Overlay
        activity.findViewById<ViewGroup>(R.id.crop_overlay)?.let { overlay ->
            applyLiveToViewHierarchy(overlay, palette)
        }
    }

    /**
     * Traverses a UI container and updates Material components in-place with the given [palette].
     */
    fun applyLiveToViewHierarchy(root: View, palette: UiMonetPalette = getPalette(root.context)) {
        when (root.id) {
            R.id.buttons_container -> {
                root.setBackgroundColor(palette.surfaceContainer)
            }
            R.id.transparency_preview -> {
                // Keep transparency preview white
                return
            }
            R.id.notification_preview_viewport -> {
                (root as? MaterialCardView)?.strokeColor = palette.outlineVariant
                // Do not traverse into the dark AMOLED notification preview mockup stage
                return
            }
            R.id.burn_in_preview_canvas,
            R.id.smart_pixels_loupe -> {
                root.invalidate()
                return
            }
            R.id.shade_empty_state -> {
                if (root is ViewGroup && root.childCount > 0) {
                    val circleFrame = root.getChildAt(0)
                    (circleFrame?.background?.mutate() as? GradientDrawable)?.setColor(palette.surfaceContainerHigh)
                }
            }
        }

        when (root) {
            is MaterialCardView -> {
                val skipChildren = styleCardView(root, palette)
                if (skipChildren) return
            }
            is MaterialButtonToggleGroup -> styleButtonToggleGroup(root, palette)
            is MaterialButton -> {
                if (root.parent !is MaterialButtonToggleGroup) {
                    styleMaterialButton(root, palette)
                }
            }
            is FloatingActionButton -> {
                root.backgroundTintList = ColorStateList.valueOf(palette.primaryContainer)
                root.imageTintList = ColorStateList.valueOf(palette.onPrimaryContainer)
            }
            is MaterialSwitch -> styleMaterialSwitch(root, palette)
            is Slider -> styleSlider(root, palette)
            is RangeSlider -> styleRangeSlider(root, palette)
            is RadioButton -> styleRadioButton(root, palette)
            is TextInputLayout -> styleTextInputLayout(root, palette)
            is BottomSheetDragHandleView -> {
                root.imageTintList = ColorStateList.valueOf(palette.outline)
            }
            is MaterialToolbar -> {
                root.setTitleTextColor(palette.onSurface)
                root.setSubtitleTextColor(palette.onSurfaceVariant)
                root.setNavigationIconTint(palette.onSurface)
            }
            is CollapsingToolbarLayout -> {
                root.setCollapsedTitleTextColor(palette.onSurface)
                root.setExpandedTitleColor(palette.onSurface)
                root.setContentScrimColor(palette.surfaceContainer)
            }
            is AppBarLayout -> {
                root.setBackgroundColor(palette.surface)
            }
            is ProgressBar -> {
                root.indeterminateTintList = ColorStateList.valueOf(palette.primary)
                root.progressTintList = ColorStateList.valueOf(palette.primary)
            }
            is ImageButton -> {
                if (root.id == R.id.btn_dismiss_shade_item) {
                    root.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
                }
            }
            is ImageView -> styleImageView(root, palette)
            is TextView -> styleTextView(root, palette)
            is RecyclerView -> {
                root.adapter?.notifyDataSetChanged()
            }
            else -> {
                // Style thin dividers inside docks/cards
                val lp = root.layoutParams
                if (lp != null && (lp.width in 1..4 || lp.height in 1..4) && root.background is ColorDrawable) {
                    root.setBackgroundColor(palette.outlineVariant)
                }
            }
        }

        if (root is ViewGroup && root !is RecyclerView) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child != null) {
                    applyLiveToViewHierarchy(child, palette)
                }
            }
        }
    }

    /**
     * Returns true if child traversal should be skipped (e.g. dark AMOLED preview viewports).
     */
    private fun styleCardView(card: MaterialCardView, palette: UiMonetPalette): Boolean {
        // Dark AMOLED preview viewport cards inside Display Settings
        if (card.findViewById<View>(R.id.burn_in_preview_canvas) != null && card.id != R.id.card_container) {
            card.strokeColor = palette.outlineVariant
            card.findViewById<View>(R.id.burn_in_preview_canvas)?.invalidate()
            return true
        }
        if (card.findViewById<View>(R.id.smart_pixels_loupe) != null && card.id != R.id.card_container) {
            card.strokeColor = palette.outlineVariant
            card.findViewById<TextView>(R.id.preview_clock_time)?.setTextColor(palette.primary)
            card.findViewById<View>(R.id.smart_pixels_loupe)?.invalidate()
            return true
        }

        when (card.id) {
            R.id.edit_mode_action_bar -> {
                card.setCardBackgroundColor(palette.surfaceContainer)
                card.strokeColor = palette.outlineVariant
            }
            R.id.bottom_nav_card,
            R.id.settings_main_card,
            R.id.crop_hint_card -> {
                card.setCardBackgroundColor(palette.surfaceContainerHigh)
            }
            R.id.icon_badge -> {
                card.setCardBackgroundColor(palette.surfaceContainerHigh)
            }
            R.id.about_identity_card -> {
                card.setCardBackgroundColor(palette.surfaceVariant)
            }
            R.id.card_remove_widget -> {
                card.setCardBackgroundColor(palette.errorContainer)
            }
            else -> {
                val parentId = (card.parent as? View)?.id
                if (parentId == R.id.crop_overlay) {
                    card.setCardBackgroundColor(palette.surfaceContainerHigh)
                } else if (card.getChildAt(0)?.id == R.id.transparency_preview) {
                    card.strokeColor = palette.outline
                } else if (card.findViewById<View>(R.id.dialog_temp_text) != null) {
                    // WeatherDialog main card
                    card.setCardBackgroundColor(palette.surfaceVariant)
                } else if (card.findViewById<View>(R.id.shade_item_app_icon) != null ||
                    card.findViewById<View>(R.id.plugin_switch) != null ||
                    card.findViewById<View>(R.id.device_icon) != null ||
                    card.id == R.id.card_kde_credits
                ) {
                    card.setCardBackgroundColor(palette.surfaceContainer)
                    if (card.strokeWidth > 0) {
                        card.strokeColor = palette.outlineVariant
                    }
                    card.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
                } else {
                    card.setCardBackgroundColor(palette.surfaceContainerLow)
                    if (card.strokeWidth > 0) {
                        card.strokeColor = palette.outlineVariant
                    }
                    card.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
                }
            }
        }
        return false
    }

    fun styleButtonToggleGroup(group: MaterialButtonToggleGroup, palette: UiMonetPalette) {
        val checkedIds = group.checkedButtonIds
        val isPillNav = group.id == R.id.bottom_nav_group

        for (i in 0 until group.childCount) {
            val btn = group.getChildAt(i) as? MaterialButton ?: continue
            val isChecked = checkedIds.contains(btn.id)
            if (isPillNav) {
                if (isChecked) {
                    btn.backgroundTintList = ColorStateList.valueOf(palette.secondaryContainer)
                    btn.setTextColor(palette.onSecondaryContainer)
                    btn.iconTint = ColorStateList.valueOf(palette.onSecondaryContainer)
                } else {
                    btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                    btn.setTextColor(palette.onSurfaceVariant)
                    btn.iconTint = ColorStateList.valueOf(palette.onSurfaceVariant)
                }
            } else {
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                )
                btn.backgroundTintList = ColorStateList(
                    states,
                    intArrayOf(palette.secondaryContainer, Color.TRANSPARENT)
                )
                val fgColors = ColorStateList(
                    states,
                    intArrayOf(palette.onSecondaryContainer, palette.onSurface)
                )
                btn.setTextColor(fgColors)
                btn.iconTint = fgColors
                btn.strokeColor = ColorStateList.valueOf(palette.outline)
            }
            btn.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
        }
    }

    fun styleMaterialButton(btn: MaterialButton, palette: UiMonetPalette) {
        when (btn.id) {
            // Filled Primary Buttons
            R.id.btn_done_edit,
            R.id.crop_position_button,
            R.id.about_btn_github,
            R.id.about_btn_license,
            R.id.btn_action -> {
                btn.backgroundTintList = ColorStateList.valueOf(palette.primary)
                btn.setTextColor(palette.onPrimary)
                btn.iconTint = ColorStateList.valueOf(palette.onPrimary)
            }
            // Filled Tonal Buttons
            R.id.btn_add_widget,
            R.id.apply_button,
            R.id.apply_background_button,
            R.id.cancel_background_button,
            R.id.crop_apply_btn,
            R.id.add_widget_button,
            R.id.btn_preset_subtle,
            R.id.btn_preset_normal,
            R.id.btn_preset_aggressive,
            R.id.btn_pixel_preset_light,
            R.id.btn_pixel_preset_balanced,
            R.id.btn_pixel_preset_strong,
            R.id.btn_replay_notification_preview,
            R.id.btn_clear_all_notifications -> {
                val states = arrayOf(
                    intArrayOf(android.R.attr.state_enabled),
                    intArrayOf(-android.R.attr.state_enabled)
                )
                btn.backgroundTintList = ColorStateList(
                    states,
                    intArrayOf(
                        palette.secondaryContainer,
                        ColorUtils.setAlphaComponent(palette.onSurface, 31)
                    )
                )
                val fg = ColorStateList(
                    states,
                    intArrayOf(
                        palette.onSecondaryContainer,
                        ColorUtils.setAlphaComponent(palette.onSurface, 97)
                    )
                )
                btn.setTextColor(fg)
                btn.iconTint = fg
            }
            // Error Container Button
            R.id.remove_widget_button -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.setTextColor(palette.onErrorContainer)
                btn.iconTint = ColorStateList.valueOf(palette.onErrorContainer)
            }
            // Outlined Buttons
            R.id.move_up_button,
            R.id.move_down_button -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.setTextColor(palette.primary)
                btn.iconTint = ColorStateList.valueOf(palette.primary)
                btn.strokeColor = ColorStateList.valueOf(palette.outline)
            }
            // Icon Buttons
            R.id.close_gallery_button -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.iconTint = ColorStateList.valueOf(palette.onSurfaceVariant)
            }
            R.id.btn_refresh_devices -> {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.iconTint = ColorStateList.valueOf(palette.primary)
            }
            // Text Buttons (settings_button, demo_button, btn_reset_layout, cancel_button, crop_reset_btn, crop_cancel_btn, etc.)
            else -> {
                if (btn.strokeWidth > 0) {
                    btn.strokeColor = ColorStateList.valueOf(palette.outline)
                }
                val bgTint = btn.backgroundTintList?.defaultColor
                if (bgTint == null || bgTint == Color.TRANSPARENT) {
                    btn.setTextColor(palette.primary)
                    btn.iconTint = ColorStateList.valueOf(palette.primary)
                }
            }
        }
        btn.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 32))
    }

    fun styleMaterialSwitch(sw: MaterialSwitch, palette: UiMonetPalette) {
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        sw.trackTintList = ColorStateList(
            states,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.surfaceContainerHighest, 97),
                palette.primary,
                palette.surfaceContainerHighest
            )
        )
        sw.thumbTintList = ColorStateList(
            states,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.onSurface, 97),
                palette.onPrimary,
                palette.outline
            )
        )
        sw.trackDecorationTintList = ColorStateList(
            states,
            intArrayOf(
                ColorUtils.setAlphaComponent(palette.onSurface, 31),
                Color.TRANSPARENT,
                palette.outline
            )
        )
        sw.setTextColor(palette.onSurface)
    }

    fun styleSlider(slider: Slider, palette: UiMonetPalette) {
        slider.thumbTintList = ColorStateList.valueOf(palette.primary)
        slider.trackActiveTintList = ColorStateList.valueOf(palette.primary)
        slider.trackInactiveTintList = ColorStateList.valueOf(palette.surfaceVariant)
        slider.tickActiveTintList = ColorStateList.valueOf(palette.onPrimary)
        slider.tickInactiveTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        slider.haloTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 48))
    }

    fun styleRangeSlider(slider: RangeSlider, palette: UiMonetPalette) {
        slider.thumbTintList = ColorStateList.valueOf(palette.primary)
        slider.trackActiveTintList = ColorStateList.valueOf(palette.primary)
        slider.trackInactiveTintList = ColorStateList.valueOf(palette.surfaceVariant)
        slider.tickActiveTintList = ColorStateList.valueOf(palette.onPrimary)
        slider.tickInactiveTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
        slider.haloTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(palette.primary, 48))
    }

    fun styleRadioButton(rb: RadioButton, palette: UiMonetPalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        rb.buttonTintList = ColorStateList(
            states,
            intArrayOf(palette.primary, palette.onSurfaceVariant)
        )
        rb.setTextColor(palette.onSurface)
    }

    fun styleTextInputLayout(til: TextInputLayout, palette: UiMonetPalette) {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_focused),
            intArrayOf(-android.R.attr.state_focused)
        )
        til.setBoxStrokeColorStateList(
            ColorStateList(states, intArrayOf(palette.primary, palette.outline))
        )
        til.hintTextColor = ColorStateList.valueOf(palette.primary)
        til.defaultHintTextColor = ColorStateList.valueOf(palette.onSurfaceVariant)
        til.setStartIconTintList(ColorStateList.valueOf(palette.onSurfaceVariant))
        til.setEndIconTintList(ColorStateList.valueOf(palette.onSurfaceVariant))
        til.editText?.setTextColor(palette.onSurface)
    }

    private fun styleImageView(iv: ImageView, palette: UiMonetPalette) {
        when (iv.id) {
            // Multi-color or raw icons that must NOT be tinted
            R.id.about_app_icon,
            R.id.about_card_pattern,
            R.id.dialog_weather_icon,
            R.id.forecast_item_icon,
            R.id.shade_item_app_icon,
            R.id.notification_app_icon -> return

            R.id.widget_icon -> {
                iv.imageTintList = ColorStateList.valueOf(palette.primary)
                (iv.background?.mutate() as? GradientDrawable)?.setStroke(4, palette.outlineVariant)
            }
            R.id.dialog_location_icon,
            R.id.dialog_cycle_icon,
            R.id.device_icon -> {
                iv.imageTintList = ColorStateList.valueOf(palette.primary)
            }
            R.id.dialog_alert_icon -> {
                iv.imageTintList = ColorStateList.valueOf(palette.error)
            }
            R.id.settings_indicator -> {
                iv.imageTintList = ColorStateList.valueOf(palette.secondary)
            }
            R.id.drag_handle -> {
                iv.imageTintList = ColorStateList.valueOf(palette.outline)
            }
            R.id.plugin_icon -> {
                iv.imageTintList = ColorStateList.valueOf(palette.onSurface)
            }
            R.id.icon_free_mode,
            R.id.icon_grid_snap,
            R.id.icon_ignore_collision,
            R.id.icon_show_media_icon,
            R.id.item_icon,
            R.id.item_chevron,
            R.id.category_icon -> {
                iv.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
            }
            else -> {
                val parentId = (iv.parent as? View)?.id
                if (iv.rotation == 180f) {
                    iv.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
                } else if (parentId == R.id.about_row_developer ||
                    parentId == R.id.about_row_version ||
                    parentId == R.id.about_row_license ||
                    parentId == R.id.about_row_source ||
                    parentId == R.id.about_row_kde_connect
                ) {
                    iv.imageTintList = ColorStateList.valueOf(palette.primary)
                } else if ((iv.parent?.parent as? View)?.id == R.id.shade_empty_state ||
                    (iv.parent as? View)?.id == R.id.card_kde_credits ||
                    (iv.parent?.parent as? View)?.id == R.id.card_kde_credits
                ) {
                    iv.imageTintList = ColorStateList.valueOf(palette.primary)
                } else if (iv.imageTintList != null) {
                    iv.imageTintList = ColorStateList.valueOf(palette.onSurfaceVariant)
                }
            }
        }
    }

    private fun styleTextView(tv: TextView, palette: UiMonetPalette) {
        when (tv.id) {
            R.id.weather_auto_temp,
            R.id.header_title,
            R.id.item_status_text,
            R.id.burn_in_value_label,
            R.id.smart_pixels_value_label,
            R.id.preview_clock_time,
            R.id.txt_device_name,
            R.id.shade_item_header,
            R.id.dialog_location_mode_badge,
            R.id.dialog_forecast_title -> tv.setTextColor(palette.primary)

            R.id.dialog_alert_text -> tv.setTextColor(palette.error)

            R.id.shade_count_badge -> {
                tv.setTextColor(palette.onPrimaryContainer)
                (tv.background?.mutate() as? GradientDrawable)?.setColor(palette.primaryContainer)
            }

            R.id.gallery_subtitle,
            R.id.widget_description,
            R.id.weather_auto_detail,
            R.id.size_value,
            R.id.max_width_value,
            R.id.var_weight_value,
            R.id.var_width_value,
            R.id.var_roundness_value,
            R.id.item_summary,
            R.id.category_subtitle,
            R.id.item_value_label,
            R.id.burn_in_summary,
            R.id.smart_pixels_summary,
            R.id.notification_preview_summary,
            R.id.plugin_desc,
            R.id.txt_no_paired,
            R.id.txt_discovering,
            R.id.txt_fingerprint,
            R.id.device_status,
            R.id.shade_item_body,
            R.id.city_coords_text,
            R.id.city_search_status_text,
            R.id.forecast_item_time,
            R.id.forecast_item_condition,
            R.id.about_app_name,
            R.id.about_version,
            R.id.about_footer,
            R.id.dialog_temp_text,
            R.id.dialog_condition_text -> tv.setTextColor(palette.onSurfaceVariant)

            R.id.weather_auto_icon,
            R.id.preview_clock_date -> { /* Leave untouched */ }
            else -> tv.setTextColor(palette.onSurface)
        }
    }
}
