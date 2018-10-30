package org.http4k.traffic

import org.http4k.core.*
import org.http4k.filter.TrafficFilters

/**
 * Provides HTTP Handlers which respond using pre-stored Requests.
 */
object Responder {
    private val fallback: HttpHandler = { Response(Status.SERVICE_UNAVAILABLE.description("no more traffic to replay")) }

    /**
     * An HTTP Handler which responds to particular requests with the matching cached responses, or a 503.
     */
    fun from(source: Source): HttpHandler = TrafficFilters.ServeCachedFrom(source).then(fallback)

    /**
     * An HTTP Handler which responds to from a stream of cached responses, or a 503 once the stream is exhausted.
     */
    fun from(replay: Replay, shouldReplay: (HttpMessage) -> Boolean = { true }): HttpHandler =
        replay.responses()
            .filter(shouldReplay)
            .iterator()
            .let {
                Filter { next -> { req -> if (it.hasNext()) it.next() else next(req) } }.then(fallback)
            }
}