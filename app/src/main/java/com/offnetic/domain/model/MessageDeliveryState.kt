package com.offnetic.domain.model

enum class MessageDeliveryState {
    SAVED,
    SENT_LOCAL,
    SENT_RELAY,
    DELIVERED,
    READ,
    FAILED
}
