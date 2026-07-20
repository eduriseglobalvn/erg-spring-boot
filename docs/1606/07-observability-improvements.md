# Observability & Logging Improvements

## 1. No GraphQL Query Timing Metrics — 🟡 MEDIUM

The `ApiAccessLogFilter` logs overall request duration:
```java
log.info("api {} {} -> {} ({} ms) [requestId={}, tenantId={}, userId={}]",
    request.getMethod(), path, response.getStatus(), durationMs, ...);
```

But it doesn't log:
- Which GraphQL operation was executed (operation name)
- How many DB queries were triggered by the operation
- Whether the request was a cache hit or miss

**Fix:** Add Micrometer timers for GraphQL operations:
```java
// In GraphQlContextInterceptor
Timer.Sample sample = Timer.start(meterRegistry);
// After execution completes:
sample.stop(Timer.builder("graphql.operation")
    .tag("operation", operationName)
    .tag("result", "success")
    .register(meterRegistry));
```

Also add `@Timed` annotations to read model service methods for per-operation DB timing.

## 2. No Hibernate Statistics for Query Count — 🟡 MEDIUM

There is no way to count how many SQL queries each GraphQL request triggers. This is essential for diagnosing N+1 problems.

**Fix:** Enable Hibernate statistics in dev/test profiles:
```yaml
# application.yml — dev profile
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,hibernate
```

And add a request-scoped counter that logs the number of queries per request:
```java
@Component
public class QueryCountInterceptor implements EmptyInterceptor {
    // Count queries per request using ThreadLocal
}
```

## 3. No Distributed Tracing — 🟡 MEDIUM

The config references Jaeger:
```yaml
telemetry:
  jaeger-endpoint: ${TELEMETRY__JAEGER_ENDPOINT:}
```

But there's no Spring Cloud Sleuth or Micrometer Tracing dependency in pom.xml. Without distributed tracing, it's impossible to trace a request from the GraphQL controller through services to DB.

**Fix:** Add Micrometer Tracing + Zipkin/Jaeger bridge:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

## 4. GraphQL Error Responses Don't Include Request ID — 🟢 LOW

When a GraphQL error occurs, the error response doesn't include the `X-Request-ID`. The `GlobalExceptionHandler` adds `X-Request-ID` to REST responses, but GraphQL errors follow a different path through the GraphQL execution engine.

**Fix:** Add a `DataFetcherExceptionHandler` that includes the request ID in GraphQL error extensions.

---

## Summary

| # | Issue | Severity | Effort |
|---|-------|----------|--------|
| 1 | No GraphQL operation timing metrics | Medium | Medium |
| 2 | No Hibernate query count per request | Medium | Small |
| 3 | No distributed tracing (Jaeger configured but no dependency) | Medium | Medium |
| 4 | GraphQL errors lack request ID | Low | Small |