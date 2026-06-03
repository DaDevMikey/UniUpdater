package com.example.uniupdater.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RomUpdateInfo(
    val rom_name: String,
    val rom_device: String,
    val rom_version: String,
    val rom_latest: Long, // Numeric timestamp/build number (e.g. 20260603)
    val banner_img: String,
    val changelog_md: String,
    val download_url: String,
    val file_size: String,
    val sha256: String,
    val rom_date: String? = null
)
