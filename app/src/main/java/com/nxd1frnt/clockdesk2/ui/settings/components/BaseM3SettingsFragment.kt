package com.nxd1frnt.clockdesk2.ui.settings.components

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nxd1frnt.clockdesk2.R

abstract class BaseM3SettingsFragment : Fragment() {

    protected lateinit var prefs: SharedPreferences
    protected lateinit var recyclerView: RecyclerView
    protected lateinit var adapter: M3SettingsAdapter

    private val rebuildRunnable = Runnable {
        if (isAdded && !isDetached) {
            rebuildSettings()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = requireContext().getSharedPreferences("ClockDeskPrefs", Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_base_m3_settings, container, false)
        recyclerView = view.findViewById(R.id.settings_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = M3SettingsAdapter(
            context = requireContext(),
            prefs = prefs,
            items = buildSettings(),
            onPreferenceChanged = { key, value ->
                onPreferenceChanged(key, value)
                scheduleRebuild()
            }
        )
        recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        view?.removeCallbacks(rebuildRunnable)
        super.onDestroyView()
    }

    abstract fun buildSettings(): List<SettingsItem>

    open fun onPreferenceChanged(key: String, value: Any) {}

    fun rebuildSettings() {
        if (::adapter.isInitialized) {
            adapter.submitList(buildSettings())
        }
    }

    fun scheduleRebuild(delayMs: Long = 250L) {
        view?.removeCallbacks(rebuildRunnable)
        view?.postDelayed(rebuildRunnable, delayMs)
    }

    fun openSubFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .setTransition(androidx.fragment.app.FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .replace(R.id.settings_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    protected fun settings(block: SettingsDslBuilder.() -> Unit): List<SettingsItem> {
        val builder = SettingsDslBuilder(prefs)
        builder.block()
        return builder.build()
    }

    class SettingsDslBuilder(private val prefs: SharedPreferences) {
        private val items = mutableListOf<SettingsItem>()
        private var currentGroupId = 0

        fun category(title: String, block: CategoryDslBuilder.() -> Unit) {
            items.add(SettingsItem.Header(title))
            val categoryBuilder = CategoryDslBuilder(prefs, currentGroupId++)
            categoryBuilder.block()
            items.addAll(categoryBuilder.entries)
        }

        fun build(): List<SettingsItem> = items
    }

    class CategoryDslBuilder(val prefs: SharedPreferences, val groupId: Int) {
        val entries = mutableListOf<SettingsItem.Entry>()

        fun switch(
            key: String,
            title: String,
            summary: String? = null,
            iconRes: Int? = null,
            defaultValue: Boolean = false,
            isVisible: () -> Boolean = { true },
            isEnabled: () -> Boolean = { true },
            onCheckedChange: ((Boolean) -> Unit)? = null
        ) {
            entries.add(
                SettingsItem.Switch(
                    key = key,
                    title = title,
                    summary = summary,
                    iconRes = iconRes,
                    defaultValue = defaultValue,
                    groupId = groupId,
                    isVisible = isVisible,
                    isEnabled = isEnabled,
                    onCheckedChange = onCheckedChange
                )
            )
        }

        fun choice(
            key: String,
            title: String,
            summary: String? = null,
            iconRes: Int? = null,
            entries: Array<String>,
            entryValues: Array<String>,
            defaultValue: String = "",
            useSimpleSummary: Boolean = true,
            isVisible: () -> Boolean = { true },
            isEnabled: () -> Boolean = { true },
            onSelectionChange: ((String) -> Unit)? = null
        ) {
            this.entries.add(
                SettingsItem.SingleChoice(
                    key = key,
                    title = title,
                    summary = summary,
                    iconRes = iconRes,
                    entries = entries,
                    entryValues = entryValues,
                    defaultValue = defaultValue,
                    useSimpleSummary = useSimpleSummary,
                    groupId = groupId,
                    isVisible = isVisible,
                    isEnabled = isEnabled,
                    onSelectionChange = onSelectionChange
                )
            )
        }

        fun slider(
            key: String,
            title: String,
            summary: String? = null,
            iconRes: Int? = null,
            min: Float,
            max: Float,
            step: Float = 1f,
            defaultValue: Float = min,
            valueFormatter: ((Float) -> String)? = null,
            isVisible: () -> Boolean = { true },
            isEnabled: () -> Boolean = { true },
            onValueChange: ((Float) -> Unit)? = null
        ) {
            entries.add(
                SettingsItem.Slider(
                    key = key,
                    title = title,
                    summary = summary,
                    iconRes = iconRes,
                    min = min,
                    max = max,
                    step = step,
                    defaultValue = defaultValue,
                    valueFormatter = valueFormatter,
                    groupId = groupId,
                    isVisible = isVisible,
                    isEnabled = isEnabled,
                    onValueChange = onValueChange
                )
            )
        }

        fun textEdit(
            key: String,
            title: String,
            summary: String? = null,
            iconRes: Int? = null,
            dialogTitle: String = title,
            inputType: Int = InputType.TYPE_CLASS_TEXT,
            defaultValue: String = "",
            useSimpleSummary: Boolean = true,
            isVisible: () -> Boolean = { true },
            isEnabled: () -> Boolean = { true },
            onTextChange: ((String) -> Unit)? = null
        ) {
            entries.add(
                SettingsItem.TextEdit(
                    key = key,
                    title = title,
                    summary = summary,
                    iconRes = iconRes,
                    dialogTitle = dialogTitle,
                    inputType = inputType,
                    defaultValue = defaultValue,
                    useSimpleSummary = useSimpleSummary,
                    groupId = groupId,
                    isVisible = isVisible,
                    isEnabled = isEnabled,
                    onTextChange = onTextChange
                )
            )
        }

        fun action(
            key: String? = null,
            title: String,
            summary: String? = null,
            iconRes: Int? = null,
            statusText: String? = null,
            showChevron: Boolean = false,
            isVisible: () -> Boolean = { true },
            isEnabled: () -> Boolean = { true },
            onClick: () -> Unit
        ) {
            entries.add(
                SettingsItem.Clickable(
                    key = key,
                    title = title,
                    summary = summary,
                    iconRes = iconRes,
                    statusText = statusText,
                    showChevron = showChevron,
                    groupId = groupId,
                    isVisible = isVisible,
                    isEnabled = isEnabled,
                    onClick = onClick
                )
            )
        }
    }
}
