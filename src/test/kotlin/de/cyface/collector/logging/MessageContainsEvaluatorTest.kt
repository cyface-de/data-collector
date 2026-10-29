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
        val configuration = configuration("<text>HetrixTools Uptime Monitoring Bot</text>")

        // Act
        val messages = logBothMessages(configuration)

        // Assert
        assertThat(messages, equalTo(listOf(OTHER_MESSAGE)))
    }

    /**
     * Ensures that the filter keeps all events, when the configuration has no text.
     */
    @Test
    fun `Filter with MessageContainsEvaluator without text, should keep all events`() {
        // Arrange
        val configuration = configuration("")

        // Act
        val messages = logBothMessages(configuration)

        // Assert
        assertThat(messages, equalTo(listOf(BOT_MESSAGE, OTHER_MESSAGE)))
    }

    private fun logBothMessages(configuration: String): List<String> {
        val context = LoggerContext()
        val configurator = JoranConfigurator()
        configurator.context = context
        configurator.doConfigure(configuration.byteInputStream())
        val logger = context.getLogger("test")
        val appender = context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("LIST") as ListAppender<*>
        logger.info(BOT_MESSAGE)
        logger.info(OTHER_MESSAGE)
        return appender.list.map { (it as ILoggingEvent).message }
    }

    private fun configuration(text: String) = """
        <configuration>
          <appender name="LIST" class="ch.qos.logback.core.read.ListAppender">
            <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
              <evaluator class="de.cyface.collector.logging.MessageContainsEvaluator">
                $text
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

    companion object {
        private const val BOT_MESSAGE = "GET /api/v4/ 200 HetrixTools Uptime Monitoring Bot"
        private const val OTHER_MESSAGE = "GET /api/v4/ 200 Mozilla/5.0"
    }
}
