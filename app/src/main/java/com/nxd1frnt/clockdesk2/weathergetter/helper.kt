package com.nxd1frnt.clockdesk2.weathergetter

import com.nxd1frnt.clockdesk2.R

fun celsiusToFahrenheit(celsius: Double): Double {
    return celsius * 9 / 5 + 32
}

fun fahrenheitToCelsius(fahrenheit: Double): Double {
    return (fahrenheit - 32) * 5 / 9
}

fun getWeatherIconRes(weatherCode: Int, isDay: Boolean): Int {
    return when (weatherCode) {
        0 -> if (isDay) R.drawable.ic_clear_day else R.drawable.ic_clear_night

        1, 2, 3 -> if (isDay) R.drawable.ic_mostly_cloudy_day else R.drawable.ic_mostly_cloudy_night
        45, 48 -> R.drawable.ic_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_snow
        95, 96, 99 -> R.drawable.ic_thunderstorm
        else -> R.drawable.ic_weather_unknown
    }
}

fun getWeatherConditionDescription(context: android.content.Context, weatherCode: Int): String {
    return when (weatherCode) {
        0 -> context.getString(R.string.weather_code_0)
        1 -> context.getString(R.string.weather_code_1)
        2 -> context.getString(R.string.weather_code_2)
        3 -> context.getString(R.string.weather_code_3)
        45, 48 -> context.getString(R.string.weather_code_fog)
        51, 53, 55 -> context.getString(R.string.weather_code_drizzle)
        56, 57 -> context.getString(R.string.weather_code_freezing_drizzle)
        61, 63, 65 -> context.getString(R.string.weather_code_rain)
        66, 67 -> context.getString(R.string.weather_code_freezing_rain)
        71, 73, 75 -> context.getString(R.string.weather_code_snow)
        77 -> context.getString(R.string.weather_code_snow_grains)
        80, 81, 82 -> context.getString(R.string.weather_code_rain_showers)
        85, 86 -> context.getString(R.string.weather_code_snow_showers)
        95 -> context.getString(R.string.weather_code_thunderstorm)
        96, 99 -> context.getString(R.string.weather_code_thunderstorm_hail)
        else -> context.getString(R.string.weather_code_unknown)
    }
}

fun getUvIndexDescription(uv: Double): String {
    return when {
        uv < 3.0 -> "Low"
        uv < 6.0 -> "Moderate"
        uv < 8.0 -> "High"
        uv < 11.0 -> "Very High"
        else -> "Extreme"
    }
}
