# Backend

Spring Boot 4.1.1 application built with Gradle and Java 26.

## Run

```shell
./gradlew bootRun
```

The HTTP endpoint is `GET http://localhost:8080/api/hello` and returns:

```json
{"message":"Hello, World!"}
```

The raw WebSocket endpoint is `ws://localhost:8080/ws/hello`. It sends
`Hello, World!` immediately after a client connects.

## Test

```shell
./gradlew test
```
