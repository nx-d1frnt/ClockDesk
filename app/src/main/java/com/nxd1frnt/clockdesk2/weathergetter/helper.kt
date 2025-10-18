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
