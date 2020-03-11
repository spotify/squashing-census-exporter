# Squashing OpenCensus Trace Exporter

![Maven Central](https://img.shields.io/maven-central/v/com.spotify.tracing/squashing-census-exporter?style=flat-square)
[![Build Status](https://img.shields.io/circleci/build/github/spotify/squashing-census-exporter)](https://circleci.com/gh/spotify/squashing-census-exporter)
[![Apache-2.0 license](https://img.shields.io/github/license/spotify/squashing-census-exporter.svg)](LICENSE)

The Squashing OpenCensus Trace Exporter is a trace exporter that tries to squash together repetitive spans. It was created to help reduce the amount of noise for very large traces. For example, complex queries in the [Heroic TSDB](https://spotify.github.io/heroic/) can sometimes create thousands or tens of thousands of spans. While this is fine from an instrumentation perspective, this volume of spans can be hard to use in most tracing UIs.

A preferrable approach is to reduce the spans by updating the instrumentation. This exporter should only be used where that is not a feasible solution - a common scenario is that the spans are very useful in low volume but at high volume they become too noisy to work with.

## Installation

### Maven

Add the exporter to your pom.xml

```xml
<dependency>
    <groupId>com.spotify.tracing</groupId>
    <artifactId>squashing-census-exporter</artifactId>
    <version>VERSION</version>
</dependency>
```

### Gradle

Add the exporter to your build.gradle

```groovy
dependencies {
    implementation "com.spotify.tracing:squashing-census-exporter:VERSION"
}
```

## Usage

```java
// Instantiate a downstream export handler
SpanExporter.Handler handler = new MyPreferredExportHandler();

// Instantiate and register the squashing exporter
SquashingTraceExporter.createAndRegister(handler);
```

The `createAndRegister` method takes two optional arguments in addition to the handler:

- `threshold`: the number of repetitive spans to trigger a squash. Defaults to 50.
- `whitelist`: A list of span names that trigger squashes. If set, any spans not matching the whitelist will be untouched.

```java
// Instantiate a downstream export handler
SpanExporter.Handler handler = new MyPreferredExportHandler();

// Register exporter with optional parameters
int threshold = 100;
List<String> whitelist = List.of("span1", "span2");
SquashingTraceExporter.createAndRegister(handler, threshold, whitelist);
```

## Behavior

This exporter tries to retain as much information as possible when a group of spans is being squashed together. The squashed span will have the start time of the earliest started span, and an end time of the latest stopped span. All tags, children, logs, etc are kept from the first span.

## Caveats

Due to how OpenCensus exporting works, there is no guarantee that the squashing exporter will see all spans in a given trace at once. With a very large or long-lived trace, spans may be broken into multiple export calls and each set is independantly evaluated for squashing. In practice this still drastically reduces the number of duplicate spans; however it does not completely eliminate them.

As a rough approximation `x` duplicate spans with `y` children each will become at least `(x * (y + 1)) / 500` squashed spans.
