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

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import io.opencensus.common.Timestamp
import io.opencensus.trace.AttributeValue.booleanAttributeValue
import io.opencensus.trace.AttributeValue.doubleAttributeValue
import io.opencensus.trace.SpanId
import io.opencensus.trace.TraceId
import io.opencensus.trace.export.SpanData
import io.opencensus.trace.export.SpanExporter
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class SquashingExporterHandler(
    private val delegate: SpanExporter.Handler,
    private val threshold: Int,
    private val whitelist: List<String>? = null
): SpanExporter.Handler() {
    val cache = Caffeine.newBuilder()
        .weakKeys()
        .maximumSize(1_000)
        .expireAfterAccess(2, TimeUnit.MINUTES)
        .removalListener { traceId: TraceId?, spans: MutableList<SpanData>?, cause ->
            if (cause == RemovalCause.EXPIRED || cause == RemovalCause.COLLECTED || cause == RemovalCause.SIZE) {
                delegate.export(spans)
            }
        }
        .build<TraceId, MutableList<SpanData>>()

    override fun export(spanDataList: MutableCollection<SpanData>) {
        if (whitelist?.isEmpty() == true) {
            // An empty non-null whitelist means nothing will be squashed, so just immediately forward all spans.
            return delegate.export(spanDataList)
        }

        val spans = spanDataList.toList()

        GlobalScope.launch {
            spans
                .groupBy { it.context.traceId!! }
                .values
                .forEach(::cacheOrExport)
        }
    }

    fun cacheOrExport(newSpans: List<SpanData>) {
        val traceId = newSpans.first().context.traceId
        val spans = cache.getIfPresent(traceId) ?: mutableListOf()
        spans.addAll(newSpans)

        val rootSpan = newSpans.find { it.hasRemoteParent == true || it.parentSpanId == null }
        if (rootSpan != null) {
            cache.invalidate(traceId)
            val squashed = squashTrace(spans)
            delegate.export(squashed)
        } else {
            cache.put(traceId, spans)
        }
    }

    fun squashTrace(trace: List<SpanData>): List<SpanData> {
        val squashed = trace.groupBy { it.parentSpanId to Pair(it.name, it.status) }
            .values
            .fold(mutableListOf<SpanData>() to mutableListOf<SpanId>(), { (acc, dropped), spanData ->
                val spanName: String = spanData.first().name
                val skipSquash = whitelist != null && !whitelist.contains(spanName)

                if (skipSquash || spanData.size < threshold) {
                    acc.addAll(spanData)
                } else {
                    val squashed = squashSpan(spanData)
                    acc.add(squashed)
                    spanData
                        .stream()
                        .filter { it.context.spanId != squashed.context.spanId }
                        .map { it.context.spanId }
                        .forEach { dropped.add(it) }
                }
                acc to dropped
            })

        // Remove all children of dropped spans
        return dropChildren(squashed.first, squashed.second)
    }

    fun squashSpan(spanData: List<SpanData>): SpanData {
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

    fun dropChildren(spanData: List<SpanData>, droppedParents: List<SpanId>): List<SpanData> {
        val grouped = spanData.groupBy { it.parentSpanId in droppedParents }
        val dropped = grouped[true]?.map { it.context.spanId }

        val kept = grouped[false] ?: listOf()
        return if (dropped == null) {
            kept
        } else {
            dropChildren(kept, dropped)
        }
    }
}
