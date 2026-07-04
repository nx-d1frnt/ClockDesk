function init() {
    // Initial state setup if any
}

function onMusicStateChanged(state) {
    ClockDesk.updateState({
        "music_player": {
            "trackTitle": state.trackTitle,
            "trackArtist": state.trackArtist,
            "state": state.isPlaying
        }
    });
}

function mediaPlayPause() {
    ClockDesk.controlMedia("playPause");
}
