/*
 * Copyright 2026 Cyface GmbH
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
package de.cyface.collector.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import org.slf4j.Logger

/**
 * Tests that [MessageContainsEvaluator] works in an `EvaluatorFilter` of a logback configuration.
 *
 * @author Armin Schnabel
 */
class MessageContainsEvaluatorTest {

    /**
     * Ensures that the filter denies the events that contain the text, and keeps all other events.
     */
    @Test
    fun `Filter with MessageContainsEvaluator, should deny only matching events`() {
        // Arrange
        val context = LoggerContext()
        val configurator = JoranConfigurator()
        configurator.context = context
        configurator.doConfigure(CONFIGURATION.byteInputStream())
        val logger = context.getLogger("test")
        val appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LIST") as ListAppender<*>

        // Act
        logger.info("GET /api/v4/ 200 HetrixTools Uptime Monitoring Bot")
        logger.info("GET /api/v4/ 200 Mozilla/5.0")

        // Assert
        val messages = appender.list.map { (it as ILoggingEvent).message }
        assertThat(messages, equalTo(listOf("GET /api/v4/ 200 Mozilla/5.0")))
    }

    companion object {
        private val CONFIGURATION = """
            <configuration>
              <appender name="LIST" class="ch.qos.logback.core.read.ListAppender">
                <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
                  <evaluator class="de.cyface.collector.logging.MessageContainsEvaluator">
                    <text>HetrixTools Uptime Monitoring Bot</text>
                  </evaluator>
                  <OnMatch>DENY</OnMatch>
                  <OnMismatch>NEUTRAL</OnMismatch>
                </filter>
              </appender>
              <root level="INFO">
                <appender-ref ref="LIST" />
              </root>
            </configuration>
        """.trimIndent()
    }
}
