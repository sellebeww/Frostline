// Copyright (c) 2026 Vassel Goleyu. Licensed under the MIT License.
package com.vassel.frostline.ui.screens

import com.vassel.frostline.data.nfc.TagInfo
import com.vassel.frostline.domain.model.CustodyEvent
import com.vassel.frostline.domain.model.CustodyType
import com.vassel.frostline.domain.port.CustodyRepository
import com.vassel.frostline.domain.port.LocationProvider
import com.vassel.frostline.domain.port.LocationResult
import com.vassel.frostline.domain.port.ReadingRepository
import com.vassel.frostline.domain.port.TripRepository
import com.vassel.frostline.domain.usecase.CompleteTrip
import com.vassel.frostline.domain.usecase.RecordCustodyEvent
import com.vassel.frostline.domain.usecase.TapGate

/** A tag tap waiting for the operator's confirmation. [location] is null while it is still being acquired. */
data class HandoverCandidate(
    val tripId: String,
    val tag: TagInfo,
    val uidHash: String,
    val proposed: CustodyType,
    val sameTagAsLast: Boolean,
    val canDeliver: Boolean,
    val tempC: Double?,
    val location: LocationResult? = null,
)

sealed interface TapOutcome {
    data object Ignored : TapOutcome
    data object NoActiveTrip : TapOutcome
    data class Candidate(val candidate: HandoverCandidate) : TapOutcome
}

enum class RejectReason { TRIP_NOT_ACTIVE, INVALID_SEQUENCE }

sealed interface ConfirmOutcome {
    data class Recorded(val event: CustodyEvent, val tripCompleted: Boolean) : ConfirmOutcome
    data class Rejected(val reason: RejectReason) : ConfirmOutcome
}

/**
 * The handover flow without Android types: a tap becomes a candidate (proposed type, temperature, same-tag warning),
 * the location is taken at the tap, and the operator's confirmation records the event atomically. DELIVERY completes
 * the trip and then [onTripCompleted] stops the monitoring service.
 */
class HandoverController(
    private val trips: TripRepository,
    private val readings: ReadingRepository,
    private val custody: CustodyRepository,
    private val record: RecordCustodyEvent,
    private val complete: CompleteTrip,
    private val location: LocationProvider,
    private val gate: TapGate,
    private val onTripCompleted: () -> Unit,
) {
    suspend fun onTag(tag: TagInfo, uidHash: String): TapOutcome {
        if (!gate.accept()) return TapOutcome.Ignored
        val trip = trips.getActive() ?: return TapOutcome.NoActiveTrip
        val last = custody.getLast(trip.id)
        return TapOutcome.Candidate(
            HandoverCandidate(
                tripId = trip.id,
                tag = tag,
                uidHash = uidHash,
                proposed = if (last == null) CustodyType.PICKUP else CustodyType.HANDOFF,
                sameTagAsLast = last?.tagUid == tag.tagId,
                canDeliver = last != null,
                tempC = readings.getAll(trip.id).lastOrNull()?.tempC,
            ),
        )
    }

    /** Takes the location for [candidate] (up to 10 s, never throws). */
    suspend fun locate(candidate: HandoverCandidate): HandoverCandidate = candidate.copy(location = location.currentLocation())

    suspend fun confirm(candidate: HandoverCandidate, deliver: Boolean, userId: String): ConfirmOutcome {
        val fix = candidate.location ?: location.currentLocation()
        return try {
            if (deliver) {
                val event = complete(candidate.tripId, candidate.tag.tagId, candidate.tag.label, userId, fix, candidate.uidHash)
                onTripCompleted()
                ConfirmOutcome.Recorded(event, tripCompleted = true)
            } else {
                val event = record(candidate.tripId, candidate.proposed, candidate.tag.tagId, candidate.tag.label, userId, fix, candidate.uidHash)
                ConfirmOutcome.Recorded(event, tripCompleted = false)
            }
        } catch (_: IllegalArgumentException) {
            ConfirmOutcome.Rejected(RejectReason.INVALID_SEQUENCE) // for example DELIVERY as the first event
        } catch (_: IllegalStateException) {
            ConfirmOutcome.Rejected(RejectReason.TRIP_NOT_ACTIVE)
        }
    }
}
