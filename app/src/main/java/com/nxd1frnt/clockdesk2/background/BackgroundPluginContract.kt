package com.nxd1frnt.clockdesk2.background

object BackgroundPluginContract {
    /**
     * Action for plugin DISCOVERY.
     * Plugins must have a service that responds to this action.
     */
    const val ACTION_BACKGROUND_PLUGIN_SERVICE = "com.nxd1frnt.clockdesk2.background.PLUGIN"

    /**
     * Meta-data key for the plugin's info XML file.
     * This meta-data must be inside the <service> tag.
     */
    const val META_DATA_PLUGIN_INFO = "com.nxd1frnt.clockdesk2.background.PLUGIN_INFO"

    /**
     * Action for DATA REQUEST.
     * ClockDesk sends this to plugins to request a background image.
     */
    const val ACTION_REQUEST_BACKGROUND = "com.nxd1frnt.clockdesk2.background.REQUEST_BACKGROUND"

    /**
     * Action for DATA RESPONSE.
     * Plugins send this back to ClockDesk with the background URL.
     */
    const val ACTION_UPDATE_BACKGROUND = "com.nxd1frnt.clockdesk2.background.UPDATE_BACKGROUND"

    // Keys for data exchange
    const val KEY_PLUGIN_PACKAGE = "plugin_package_name"
    const val KEY_BACKGROUND_URL = "background_url"
    const val KEY_BACKGROUND_URI = "background_uri"
    const val KEY_IS_AVAILABLE = "is_available"
    const val KEY_ERROR_MESSAGE = "error_message"
}
