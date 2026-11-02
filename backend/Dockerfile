FROM ubuntu:24.04 AS build

ENV DEBIAN_FRONTEND=noninteractive
WORKDIR /workspace

RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates openjdk-21-jdk-headless \
    && rm -rf /var/lib/apt/lists/*

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon bootJar \
    && set -- build/libs/*.jar \
    && for artifact in "$@"; do \
        case "$artifact" in *-plain.jar) continue ;; esac; \
        cp "$artifact" /workspace/togafmate-backend.jar; \
        break; \
    done \
    && test -s /workspace/togafmate-backend.jar

FROM ubuntu:24.04

ARG APP_VERSION=1.0
LABEL org.opencontainers.image.title="TOGAFmate Backend" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.description="Spring Boot REST API for TOGAFmate"

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install --no-install-recommends -y ca-certificates openjdk-21-jre-headless \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/togafmate-backend.jar /app/app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
