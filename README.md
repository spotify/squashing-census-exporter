[![Apache-2.0 license](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

# Squashing OpenCensus Trace Exporter

The Squashing OpenCensus Trace Exporter is a trace exporter that tries to squash together repetitive spans.

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
    implementation "com.spotify.tracing:squashing-trace-exporter:VERSION"
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
