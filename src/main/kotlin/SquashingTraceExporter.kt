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

import io.opencensus.trace.Tracing
import io.opencensus.trace.export.SpanExporter

/**
 * Helper class to create and register the squashing trace exporter handler.
 * The name of the exporter will be set from the delegate handler.
 */
open class SquashingTraceExporter(delegate: SpanExporter.Handler, threshold: Int, whitelist: List<String>?) {
    private val registerName: String = delegate::class.java.name

    init {
        val handler = SquashingExporterHandler(delegate, threshold, whitelist)
        Tracing.getExportComponent().spanExporter.registerHandler(registerName, handler)
    }

    fun unregister() {
        Tracing.getExportComponent().spanExporter.unregisterHandler(registerName)
    }

    companion object {
        /**
         * Helper method to create a SquashingTraceExporter.
         * @param delegate The {@link SpanExporter.Handler} to export spans to after squashing.
         * @param threshold The number of duplicate spans to detect in a trace before squashing.
         * @param whitelist A list of span operation names to explicitly true to squash. If this is `null` then all
         * spans can potentially be squashed.
         */
        @JvmStatic
        @JvmOverloads
        fun createAndRegister(
            delegate: SpanExporter.Handler, threshold: Int = 50, whitelist: List<String>? = null
        ): SquashingTraceExporter {
            return SquashingTraceExporter(delegate, threshold, whitelist)
        }
    }
}