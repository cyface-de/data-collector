/*
 * Copyright 2025 Cyface GmbH
 *
 * This file is part of the Cyface Data Collector.
 *
 * The Cyface Data Collector is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The Cyface Data Collector is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the Cyface Data Collector. If not, see <http://www.gnu.org/licenses/>.
 */
package de.cyface.collector.auth

import de.cyface.collector.handler.HTTPStatus
import io.vertx.core.Future
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.web.RoutingContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

/**
 * Unit tests for [MultiProviderJWTAuthHandler].
 *
 * Vert.x already-completed futures (succeededFuture / failedFuture) invoke their callbacks synchronously
 * when there is no Vert.x event-loop context, which is the case in plain unit tests.
 * This makes it safe to use mocked [JWTAuth] providers and verify side-effects synchronously.
 *
 * @author Klemens Muthmann
 */
@ExtendWith(MockitoExtension::class)
class MultiProviderJWTAuthHandlerTest {

    private val user = mock<User>()

    @Test
    fun `accepts when first provider succeeds and does not consult second`() {
        val provider1 = mock<JWTAuth>()
        val provider2 = mock<JWTAuth>()
        whenever(provider1.authenticate(any<JsonObject>())).thenReturn(Future.succeededFuture(user))

        val handler = MultiProviderJWTAuthHandler(listOf(provider1, provider2))
        val ctx = mockContextWithToken("Bearer test.token.value")

        handler.handle(ctx)

        verify(ctx).setUser(user)
        verify(ctx).next()
        verifyNoInteractions(provider2)
    }

    @Test
    fun `accepts when first provider fails and second succeeds`() {
        val provider1 = mock<JWTAuth>()
        val provider2 = mock<JWTAuth>()
        whenever(provider1.authenticate(any<JsonObject>())).thenReturn(Future.failedFuture("Invalid token"))
        whenever(provider2.authenticate(any<JsonObject>())).thenReturn(Future.succeededFuture(user))

        val handler = MultiProviderJWTAuthHandler(listOf(provider1, provider2))
        val ctx = mockContextWithToken("Bearer test.token.value")

        handler.handle(ctx)

        verify(ctx).setUser(user)
        verify(ctx).next()
    }

    @Test
    fun `rejects when all providers fail`() {
        val provider1 = mock<JWTAuth>()
        val provider2 = mock<JWTAuth>()
        whenever(provider1.authenticate(any<JsonObject>())).thenReturn(Future.failedFuture("Invalid"))
        whenever(provider2.authenticate(any<JsonObject>())).thenReturn(Future.failedFuture("Invalid"))

        val handler = MultiProviderJWTAuthHandler(listOf(provider1, provider2))
        val ctx = mockContextWithToken("Bearer test.token.value")

        handler.handle(ctx)

        verify(ctx).fail(HTTPStatus.UNAUTHORIZED)
    }

    @Test
    fun `rejects when Authorization header is absent`() {
        val provider = mock<JWTAuth>()
        val handler = MultiProviderJWTAuthHandler(listOf(provider))
        val ctx = mockContextWithToken(null)

        handler.handle(ctx)

        verify(ctx).fail(HTTPStatus.UNAUTHORIZED)
        verifyNoInteractions(provider)
    }

    @Test
    fun `rejects when Authorization header has no Bearer prefix`() {
        val provider = mock<JWTAuth>()
        val handler = MultiProviderJWTAuthHandler(listOf(provider))
        val ctx = mockContextWithToken("Basic dXNlcjpwYXNz")

        handler.handle(ctx)

        verify(ctx).fail(HTTPStatus.UNAUTHORIZED)
        verifyNoInteractions(provider)
    }

    private fun mockContextWithToken(authHeader: String?): RoutingContext {
        val ctx = mock<RoutingContext>()
        val request = mock<HttpServerRequest>()
        whenever(ctx.request()).thenReturn(request)
        whenever(request.getHeader("Authorization")).thenReturn(authHeader)
        return ctx
    }
}
