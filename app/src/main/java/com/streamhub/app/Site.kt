package com.streamhub.app

import android.graphics.Color

data class Site(
    val name: String,
    val url: String,
    val domain: String,
    val avatar: String,
    val color: Int
)

object Sites {
    val ALL = listOf(
        Site("YouTube", "https://m.youtube.com/", "youtube.com", "YT", Color.parseColor("#FF0033")),
        Site("TikTok", "https://www.tiktok.com/", "tiktok.com", "TT", Color.parseColor("#161616")),
        Site("Instagram", "https://www.instagram.com/", "instagram.com", "IG", Color.parseColor("#C13584")),
        Site("Facebook", "https://m.facebook.com/", "facebook.com", "FB", Color.parseColor("#1877F2")),
        Site("X", "https://x.com/", "x.com", "X", Color.parseColor("#1A1A1A")),
        Site("Reddit", "https://www.reddit.com/", "reddit.com", "RD", Color.parseColor("#FF4500")),
        Site("Twitch", "https://m.twitch.tv/", "twitch.tv", "TW", Color.parseColor("#9146FF")),
        Site("Kick", "https://kick.com/", "kick.com", "KK", Color.parseColor("#1F7A00")),
        Site("Vimeo", "https://vimeo.com/", "vimeo.com", "VM", Color.parseColor("#0B7EA2")),
        Site("Dailymotion", "https://www.dailymotion.com/", "dailymotion.com", "DM", Color.parseColor("#0A5FCC")),
        Site("SoundCloud", "https://m.soundcloud.com/", "soundcloud.com", "SC", Color.parseColor("#FF5500")),
        Site("Bandcamp", "https://bandcamp.com/", "bandcamp.com", "BC", Color.parseColor("#1E6E7E")),
        Site("Rumble", "https://rumble.com/", "rumble.com", "RM", Color.parseColor("#3F7A1E")),
        Site("Odysee", "https://odysee.com/", "odysee.com", "OD", Color.parseColor("#C2185B")),
        Site("Bilibili", "https://m.bilibili.com/", "bilibili.com", "BL", Color.parseColor("#D93B6A")),
        Site("Internet Archive", "https://archive.org/", "archive.org", "IA", Color.parseColor("#44546A"))
    )
}
