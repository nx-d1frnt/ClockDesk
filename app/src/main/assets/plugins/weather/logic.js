function init() {
    updateWeather();
}

function onWeatherUpdated(dataJson) {
    if (dataJson) {
        try {
            const data = JSON.parse(dataJson);
            ClockDesk.updateState({
                "weather_main": {
                    "temp": data.temp,
                    "description": data.description,
                    "forecast": data.forecast
                }
            });
        } catch (e) {
            // ignore
        }
    }
}

function updateWeather() {
    const dataJson = ClockDesk.getWeatherData();
    if (dataJson) {
        onWeatherUpdated(dataJson);
    }
}
