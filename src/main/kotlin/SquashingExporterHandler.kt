/*
 * Copyright (c) 2020 Spotify AB.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.tracing

import io.opencensus.common.Timestamp
import io.opencensus.trace.AttributeValue.*
import io.opencensus.trace.export.SpanData
import io.opencensus.trace.export.SpanExporter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


// TODO: make this configurable
const val SQUASH_THRESHOLD = 100
// TODO: add span name and parent span name filtering

// TODO: remove children of squashed spans

class SquashingExporterHandler(private val delegate: SpanExporter.Handler): SpanExporter.Handler() {
    override fun export(spanDataList: MutableCollection<SpanData>) {
        val spans = spanDataList.toList()
        GlobalScope.launch {
            val squashed = spans
                .groupBy { it.context.traceId!! }
                .values
                .flatMap(::squashTrace)

            delegate.export(squashed)
        }
    }

    fun squashTrace(trace: List<SpanData>): List<SpanData> {
        return trace.groupBy { Pair(it.name, it.parentSpanId) }
            .values
            .fold(mutableListOf<SpanData>(), { acc, spanData ->
                if (spanData.size < SQUASH_THRESHOLD) {
                    acc.addAll(spanData)
                } else {
                    acc.add(squashedSpan(spanData))
                }
                acc
            })
    }

    fun squashedSpan(spanData: List<SpanData>): SpanData {
        val span = spanData.minBy { it.startTimestamp }!!
        val endTime = spanData
            .filterNot { it.endTimestamp == null }
            .maxBy { it.endTimestamp as Timestamp }
            ?.endTimestamp

        val attributes = span.attributes.attributeMap.toMutableMap()
        attributes.putAll(mapOf(
            "trace.squashed" to booleanAttributeValue(true),
            "trace.squash_count" to doubleAttributeValue(spanData.size.toDouble())
        ))

        return SpanData.create(
            span.context,
            span.parentSpanId,
            span.hasRemoteParent,
            span.name,
            span.kind,
            span.startTimestamp,
            SpanData.Attributes.create(attributes, span.attributes.droppedAttributesCount),
            span.annotations,
            span.messageEvents,
            span.links,
            span.childSpanCount,
            span.status,
            endTime
        )
    }
}