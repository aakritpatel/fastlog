package dev.aakrit.fastlog.model

import kotlinx.serialization.Serializable

/** A fast currently in progress. */
@Serializable
data class ActiveFast(
    val startEpochMs: Long,
    val goalHours: Double,
    val protocolId: String,
)

/** A finished fast, kept in history. */
@Serializable
data class FastRecord(
    val id: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val goalHours: Double,
    val protocolId: String,
) {
    val durationMs: Long get() = endEpochMs - startEpochMs
    val durationHours: Double get() = durationMs / 3_600_000.0
    val goalReached: Boolean get() = durationHours >= goalHours
    val protocol: Protocol get() = Protocol.resolve(protocolId, goalHours)
}

/** Everything the app persists, as one serializable blob on disk. */
@Serializable
data class FastingState(
    val active: ActiveFast? = null,
    val history: List<FastRecord> = emptyList(),
)
