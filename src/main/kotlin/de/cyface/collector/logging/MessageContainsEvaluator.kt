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

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.boolex.EventEvaluatorBase

/**
 * A logback evaluator, which matches the log events with a raw message that contains [text].
 *
 * Logback 1.5.13 removed the Janino `<expression>` evaluator. This class replaces filters such as
 * `<expression>message.contains("HetrixTools Uptime Monitoring Bot")</expression>`:
 *
 * ```xml
 * <filter class="ch.qos.logback.core.filter.EvaluatorFilter">
 *   <evaluator class="de.cyface.collector.logging.MessageContainsEvaluator">
 *     <text>HetrixTools Uptime Monitoring Bot</text>
 *   </evaluator>
 *   <OnMatch>DENY</OnMatch>
 *   <OnMismatch>NEUTRAL</OnMismatch>
 * </filter>
 * ```
 *
 * @author Armin Schnabel
 * @property text The text to find in the message. Logback sets it from the `<text>` element.
 */
class MessageContainsEvaluator : EventEvaluatorBase<ILoggingEvent>() {
    var text: String? = null

    override fun start() {
        if (text.isNullOrEmpty()) {
            addError("No <text> set for the evaluator named [$name]")
            return
        }
        super.start()
    }

    override fun evaluate(event: ILoggingEvent): Boolean {
        val text = this.text ?: return false
        return event.message?.contains(text) == true
    }
}
