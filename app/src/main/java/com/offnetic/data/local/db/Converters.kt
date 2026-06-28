package com.offnetic.data.local.db

import androidx.room.TypeConverter
import com.offnetic.data.local.db.entity.RelayOutboxState
import com.offnetic.data.local.db.entity.RequestDirection
import com.offnetic.data.local.db.entity.RequestState
import com.offnetic.domain.model.MessageDeliveryState

class Converters {
    @TypeConverter
    fun fromDeliveryState(state: MessageDeliveryState): String = state.name

    @TypeConverter
    fun toDeliveryState(value: String): MessageDeliveryState = MessageDeliveryState.valueOf(value)

    @TypeConverter
    fun fromRelayOutboxState(state: RelayOutboxState): String = state.name

    @TypeConverter
    fun toRelayOutboxState(value: String): RelayOutboxState = RelayOutboxState.valueOf(value)

    @TypeConverter
    fun fromRequestDirection(direction: RequestDirection): String = direction.name

    @TypeConverter
    fun toRequestDirection(value: String): RequestDirection = RequestDirection.valueOf(value)

    @TypeConverter
    fun fromRequestState(state: RequestState): String = state.name

    @TypeConverter
    fun toRequestState(value: String): RequestState = RequestState.valueOf(value)
}
