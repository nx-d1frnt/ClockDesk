package com.nxd1frnt.clockdesk2.utils

import androidx.annotation.StringRes
import com.nxd1frnt.clockdesk2.R

enum class MediaStyle(
    val id: String,
    @StringRes val titleRes: Int,
    val previewTitle: String,
    val previewArtist: String?,
    val isCard: Boolean = false,
    val isExpanded: Boolean = false
) {
    MINIMAL_TICKER("MINIMAL_TICKER", R.string.media_style_minimal_ticker, "Song Title - Artist", null, isCard = false, isExpanded = false),
    COMPACT_CARD("COMPACT_CARD", R.string.media_style_compact_card, "Song Title", "Artist", isCard = true, isExpanded = false),
    EXPANDED_PLAYER("EXPANDED_PLAYER", R.string.media_style_expanded_player, "Song Title", "Artist", isCard = true, isExpanded = true);

    val isMinimal: Boolean get() = this == MINIMAL_TICKER

    companion object {
        fun fromId(id: String?): MediaStyle {
            return values().find { it.id == id } ?: MINIMAL_TICKER
        }
    }
}
