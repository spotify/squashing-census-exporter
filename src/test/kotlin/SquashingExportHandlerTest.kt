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

import io.mockk.every
import io.mockk.mockk
import io.opencensus.common.Timestamp
import io.opencensus.trace.SpanId
import io.opencensus.trace.export.SpanData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

class SquashingExportHandlerTest {
    lateinit var handler: SquashingExporterHandler
    lateinit var rootSpan: SpanData
    lateinit var dupeSpan: SpanData
    lateinit var dupeSpans: MutableList<SpanData>

    @BeforeEach
    fun setup() {
        handler = SquashingExporterHandler(mockk())

        rootSpan = mockk(relaxed = true) {
            every { name } returns "Recv.test"
            every { parentSpanId } returns null
        }

        val parentId = SpanId.generateRandomId(Random())

        val endTimes = (1..100).map {
            Timestamp.create(315576000 + it.toLong(), 0)
        }

        dupeSpan = mockk(relaxed = true) {
            every { name } returns "test"
            every { parentSpanId } returns parentId
            every { startTimestamp } returns Timestamp.create(315576000, 0)
            every { endTimestamp } returnsMany endTimes
        }

        dupeSpans = mutableListOf()
        repeat(100) { dupeSpans.add(dupeSpan) }
    }

    @Test
    fun `Traces below the squash limit should be retained`() {
        val spans = dupeSpans.drop(1).plus(rootSpan)

        val squashed = handler.squashTrace(spans)
        assertEquals(100, squashed.size)
    }

    @Test
    fun `Duplicate spans squashed into one`() {
        val spans = dupeSpans.plus(rootSpan)

        val squashed = handler.squashTrace(spans)
        assertEquals(2, squashed.size)
        val span = squashed.first { it != rootSpan }
        assertEquals(315576000, span.startTimestamp.seconds)
        assertEquals(315576100, span.endTimestamp?.seconds)
    }
}