# syntax=docker/dockerfile:1

# Local publication for the neo/26.1.2 branch:
#   docker build --load -f Dockerfile -t ghcr.io/nibelungorum/mmcr-ci:26.1.2 .
#   docker push ghcr.io/nibelungorum/mmcr-ci:26.1.2

FROM eclipse-temurin:25-jdk-noble AS toolchain

ARG GRADLE_VERSION=8.14.5
ENV GRADLE_HOME=/opt/gradle
ENV PATH="${GRADLE_HOME}/bin:${PATH}"

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl git unzip \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv "/opt/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}" \
    && rm /tmp/gradle.zip \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* \
    && gradle --version

FROM toolchain AS warmup

ENV CI=true
ENV GRADLE_USER_HOME=/opt/gradle-home
WORKDIR /__w/ModularMachinery-Community-Refoxed/ModularMachinery-Community-Refoxed

COPY build.gradle settings.gradle gradle.properties ./
COPY gradle/scripts ./gradle/scripts
COPY src/main ./src/main

RUN gradle --no-daemon compileJava runClientData runServerData \
    && mkdir -p /opt/mmcr-seed \
    && cp -a build /opt/mmcr-seed/build \
    && cp -a .gradle /opt/mmcr-seed/project-gradle

FROM toolchain AS runtime

RUN apt-get update \
    && apt-get install -y --no-install-recommends gh jq \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

LABEL org.opencontainers.image.source="https://github.com/Nibelungorum/ModularMachinery-Community-Refoxed" \
      org.opencontainers.image.description="Prewarmed MMCR 26.1.2 NeoForge CI environment"

ENV GRADLE_USER_HOME=/opt/gradle-home
WORKDIR /__w/ModularMachinery-Community-Refoxed/ModularMachinery-Community-Refoxed

COPY --from=warmup /opt/gradle-home /opt/gradle-home
COPY --from=warmup /opt/mmcr-seed /opt/mmcr-seed

CMD ["/bin/bash"]
