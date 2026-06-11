package com.offnetic.data.local.db.util

import androidx.room.TypeConverter
import android.util.Base64

class ByteArrayConverter {
    @TypeConverter
    fun fromByteArray(value: ByteArray?): String? {
        return value?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    @TypeConverter
    fun toByteArray(value: String?): ByteArray? {
        return value?.let { Base64.decode(it, Base64.NO_WRAP) }
    }
}