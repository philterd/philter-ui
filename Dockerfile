# --- Build stage ---
# The Vaadin plugin installs its own Node.js when none is found, so a Maven image with Java 25 is enough.
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /build

COPY pom.xml package.json package-lock.json tsconfig.json types.d.ts vite.config.ts ./
COPY src ./src

# Tests run in CI; -U re-checks SNAPSHOT dependencies such as philter-sdk-java.
RUN mvn -B -U package -DskipTests

# --- Runtime image ---
FROM eclipse-temurin:25-jre

RUN groupadd --system --gid 10001 philter \
    && useradd --system --uid 10001 --gid philter --home-dir /opt/philter-ui \
        --shell /usr/sbin/nologin philter \
    && mkdir -p /opt/philter-ui

COPY LICENSE.txt README.md /opt/philter-ui/
COPY --from=build /build/target/philter-ui-*.jar /opt/philter-ui/philter-ui.jar

RUN chown -R philter:philter /opt/philter-ui

USER philter

EXPOSE 8081

WORKDIR /opt/philter-ui
ENTRYPOINT ["java", "-jar", "/opt/philter-ui/philter-ui.jar"]
