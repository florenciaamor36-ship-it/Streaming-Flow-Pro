package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.theme.*

data class PlatformInfo(
    val id: String,
    val name: String,
    val defaultMaxProfiles: Int,
    val color: Color,
    val icon: ImageVector,
    val isPopular: Boolean = false
)

object StreamingPlatforms {
    val NETFLIX = PlatformInfo("netflix", "Netflix", 5, ColorNetflix, Icons.Default.Movie, true)
    val DISNEY = PlatformInfo("disney", "Disney+", 7, ColorDisney, Icons.Default.Tv, true)
    val MAX = PlatformInfo("max", "Max / HBO", 5, ColorMax, Icons.Default.Movie, true)
    val PRIME = PlatformInfo("prime", "Prime Video", 6, ColorPrime, Icons.Default.Movie, true)
    val SPOTIFY = PlatformInfo("spotify", "Spotify", 6, ColorSpotify, Icons.Default.MusicNote, true)
    val PARAMOUNT = PlatformInfo("paramount", "Paramount+", 5, ColorParamount, Icons.Default.Tv)
    val CRUNCHYROLL = PlatformInfo("crunchyroll", "Crunchyroll", 4, ColorCrunchyroll, Icons.Default.Tv)
    val APPLE_TV = PlatformInfo("appletv", "Apple TV+", 5, ColorAppleTV, Icons.Default.Tv)
    val IPTV = PlatformInfo("iptv", "IPTV Premium", 3, ColorIPTV, Icons.Default.LiveTv, true)
    val STAR_PLUS = PlatformInfo("starplus", "Star+", 4, ColorStar, Icons.Default.Movie)
    val VIX = PlatformInfo("vix", "ViX Premium", 3, ColorViX, Icons.Default.Tv)
    val YOUTUBE = PlatformInfo("youtube", "YouTube Premium", 5, ColorYouTube, Icons.Default.Movie)
    val OTRO = PlatformInfo("otro", "Otro / Personalizado", 5, BrandPrimary, Icons.Default.Tv, true)

    val ALL = listOf(
        NETFLIX, DISNEY, MAX, PRIME, SPOTIFY, PARAMOUNT, CRUNCHYROLL, APPLE_TV, IPTV, STAR_PLUS, VIX, YOUTUBE, OTRO
    )

    fun getByName(name: String): PlatformInfo {
        return ALL.find { it.name.equals(name, ignoreCase = true) }
            ?: PlatformInfo(
                id = name.lowercase().replace(" ", "_"),
                name = name,
                defaultMaxProfiles = 5,
                color = BrandPrimary,
                icon = Icons.Default.Tv
            )
    }
}
