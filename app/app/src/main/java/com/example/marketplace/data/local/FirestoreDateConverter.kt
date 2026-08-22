package com.example.marketplace.data.local

import java.time.LocalDateTime
import java.time.ZoneOffset

object FirestoreDateConverter {
    fun paraMillis(data: LocalDateTime): Long =
        data.toEpochSecond(ZoneOffset.UTC) * 1000

    fun deMillis(millis: Long?): LocalDateTime =
        millis?.let {
            LocalDateTime.ofEpochSecond(it / 1000, ((it % 1000) * 1_000_000).toInt(), ZoneOffset.UTC)
        } ?: LocalDateTime.now()
}
