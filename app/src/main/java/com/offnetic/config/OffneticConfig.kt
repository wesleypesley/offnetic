package com.offnetic.config

/**
 * Central home for tunable constants that were previously scattered as inline
 * literals across the codebase (H4–H19). Changing a value here changes it
 * everywhere — several of these (max file size, call timeout) used to exist as
 * duplicated literals that could silently drift apart.
 */
object OffneticConfig {

    // --- Relay transport (H4) ---
    val DEFAULT_RELAYS = listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.primal.net",
        "wss://offchain.pub"
    )

    // --- Blossom media servers (H5, H13) ---
    val DEFAULT_BLOSSOM_SERVERS = listOf(
        "https://nostr.download",
        "https://cdn.hzrd149.com",
        "https://blossom.dreamith.to"
    )
    const val BLOSSOM_CONNECT_TIMEOUT_S = 30L
    const val BLOSSOM_READ_TIMEOUT_S = 120L
    const val BLOSSOM_CALL_TIMEOUT_S = 180L

    // --- WebRTC / calls (H6–H8, H10–H12) ---
    val STUN_SERVERS = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302"
    )
    const val P2P_CANDIDATE_PORT = 58000
    const val P2P_SUBNET_PREFIX = "192.168.49."
    const val CALL_TIMEOUT_MS = 60_000L
    const val ICE_GATHERING_TIMEOUT_MS = 8_000L
    const val STALE_CALL_OFFER_MS = 45_000L

    // --- Attachments (H9 — single source instead of three copies) ---
    const val MAX_FILE_SIZE_BYTES = 100L * 1024 * 1024

    // --- Voice notes (H14, H15) ---
    const val VOICE_NOTE_MAX_DURATION_MS = 2 * 60 * 1000
    const val VOICE_NOTE_MAX_BYTES = (1.5 * 1024 * 1024).toLong()
    const val VOICE_NOTE_SAMPLE_RATE_HZ = 16_000
    const val VOICE_NOTE_BIT_RATE = 32_000

    // --- Relay housekeeping (H16, H17) ---
    // 5000 was too small under concurrent multi-relay load — each event arrives
    // once per relay, so effective capacity was capacity / relayCount
    const val EVENT_DEDUPER_CAPACITY = 20_000
    const val RELAY_OUTBOX_CAP = 50

    // --- NCAPI (H18, H19) ---
    const val ENDPOINT_RETRY_ATTEMPTS = 30
    const val ENDPOINT_RETRY_INTERVAL_MS = 2_000L
    const val PROXIMITY_SILENT_MS = 5 * 60 * 1000L
}
