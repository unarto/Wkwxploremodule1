package com.wakwau.xplore.core.storage.operation

import kotlinx.coroutines.CancellationException

/** Destination is complete; failure must not roll it back. */
interface CommittedDestination
class SourceRemovalException(cause: Throwable) :
    RuntimeException("Destination committed; source removal did not complete", cause), CommittedDestination
class SourceRemovalCancelled(cause: CancellationException) :
    CancellationException("Destination committed; source removal was cancelled"), CommittedDestination {
    init { initCause(cause) }
}
