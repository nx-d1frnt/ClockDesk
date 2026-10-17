package com.nxd1frnt.clockdesk2.background.provider

object BackgroundPluginContract {
    /**
     * Action for background plugin discovery.
     * External plugins register a BroadcastReceiver responding to this action.
     */
    const val ACTION_QUERY_BACKGROUND_PLUGINS = "com.nxd1frnt.clockdesk2.ACTION_QUERY_BACKGROUND_PLUGINS"

    /**
     * Meta-data key in AndroidManifest.xml providing the plugin's metadata.
     */
    const val META_DATA_PLUGIN_INFO = "com.nxd1frnt.clockdesk2.background.PLUGIN_RECEIVER"

    /**
     * Action sent by ClockDesk requesting background data from the plugin.
     */
    const val ACTION_REQUEST_BACKGROUND_DATA = "com.nxd1frnt.clockdesk2.ACTION_REQUEST_BACKGROUND_DATA"

    /**
     * Action sent by the plugin to ClockDesk when background data is updated.
     */
    const val ACTION_UPDATE_BACKGROUND_DATA = "com.nxd1frnt.clockdesk2.ACTION_UPDATE_BACKGROUND_DATA"

    // Data keys
    const val KEY_PLUGIN_ID = "plugin_id"
    const val KEY_PLUGIN_PACKAGE = "plugin_package_name"
    const val KEY_TITLE = "title"
    const val KEY_DESCRIPTION = "description"
    const val KEY_ICON_NAME = "icon_name"
    const val KEY_COLOR = "color"
    const val KEY_IMAGE_URI = "image_uri"
    const val KEY_PREVIEW_URI = "preview_uri"
    const val KEY_THUMBNAIL_URI = "thumbnail_uri"
    const val KEY_SETTINGS_ACTIVITY = "settings_activity"
    const val KEY_SUPPORTS_BLUR = "supports_blur"
    const val KEY_SUPPORTS_CROP = "supports_crop"
    const val KEY_SUPPORTS_WEATHER = "supports_weather"
    const val KEY_SUPPORTS_DIMMING = "supports_dimming"
}
