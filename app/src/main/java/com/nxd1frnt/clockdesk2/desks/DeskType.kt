package com.nxd1frnt.clockdesk2.desks

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nxd1frnt.clockdesk2.R

enum class DeskType(
    val typeId: String,
    @StringRes val titleResId: Int,
    @DrawableRes val iconResId: Int
) {
    WIDGETS("widgets", R.string.desk_type_widgets, R.drawable.ic_clock),
    APPS("apps", R.string.desk_type_apps, R.drawable.ic_grid),
    DEVICES("devices", R.string.desk_type_devices, R.drawable.ic_devices),
    WEATHER("weather", R.string.desk_type_weather, R.drawable.ic_mostly_cloudy_day),
    TIMER("timer", R.string.desk_type_timer, R.drawable.ic_timer),
    WEB_KIOSK("web_kiosk", R.string.desk_type_web_kiosk, R.drawable.ic_web),
    NOTIFICATIONS("notifications", R.string.desk_type_notifications, R.drawable.ic_notifications);

    companion object {
        fun fromTypeId(typeId: String): DeskType =
            entries.firstOrNull { it.typeId.equals(typeId, ignoreCase = true) } ?: WIDGETS
    }
}
