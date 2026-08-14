package com.nxd1frnt.clockdesk2.smartchips

object ChipPluginContract {
    /**
     * Action for plugin DISCOVERY.
     * Plugins must have a receiver that listens for this action.
     */
    const val ACTION_QUERY_PLUGINS = "com.nxd1frnt.clockdesk2.ACTION_QUERY_SMART_CHIPS"

    /**
     * Meta-data key for the plugin's info XML file.
     * This meta-data must be inside the <receiver> tag.
     */
    const val META_DATA_PLUGIN_INFO = "com.nxd1frnt.clockdesk2.smartchip.PLUGIN_RECEIVER"

    /**
     * Action for DATA REQUEST.
     * ClockDesk sends this to plugins to ask for data.
     */
    const val ACTION_REQUEST_DATA = "com.nxd1frnt.clockdesk2.ACTION_REQUEST_CHIP_DATA"

    /**
     * Action for DATA RESPONSE.
     * Plugins send this back to ClockDesk.
     */
    const val ACTION_UPDATE_DATA = "com.nxd1frnt.clockdesk2.ACTION_UPDATE_CHIP_DATA"

    // Keys for data exchange
    const val KEY_LOCATION = "location"
    const val KEY_PLUGIN_PACKAGE = "plugin_package_name"
    const val KEY_CHIP_VISIBLE = "chip_visible"

    /**
     * Unique identifier for a specific chip instance (e.g. "download_task_42").
     * When posting multiple chips, each chip payload must specify a distinct [KEY_CHIP_ID].
     * If omitted, defaults to the channel/preferenceKey ID for single-chip plugins.
     */
    const val KEY_CHIP_ID = "chip_id"

    /**
     * Notification Channel / Category ID (e.g. "downloads_channel").
     * Multiple chip instances can be posted under a single [KEY_CHANNEL_ID].
     * The user enables, disables, or reorders the channel in Settings, controlling all chips under that channel.
     * If omitted, defaults to the plugin's preferenceKey.
     */
    const val KEY_CHANNEL_ID = "channel_id"

    /**
     * Extra key containing an ArrayList<Bundle> for multi-chip broadcast responses.
     * Each Bundle in the list represents a single chip payload containing [KEY_CHIP_ID], [KEY_CHANNEL_ID],
     * [KEY_CHIP_TEXT], [KEY_CHIP_ICON_NAME], [KEY_CHIP_VISIBLE], and optional [KEY_CHIP_CLICK_ACTIVITY].
     */
    const val KEY_CHIPS_ARRAY = "chips_array"

    const val KEY_CHIP_TEXT = "chip_text"

    const val KEY_CHIP_ICON_NAME = "chip_icon_name" // e.g., "ic_plugin_icon"
    const val KEY_CHIP_PRIORITY = "chip_priority"
    const val KEY_CHIP_CLICK_ACTIVITY = "chip_click_activity"
}