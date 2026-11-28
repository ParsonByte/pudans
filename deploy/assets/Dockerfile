ARG SERVICE

FROM gradle:9.9.0-jdk25 AS build

WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :pudans-${SERVICE}:clean :pudans-${SERVICE}:bootJar --no-daemon

FROM eclipse-temurin:25-jre

ARG SERVICE

RUN groupadd --system pudans \
    && useradd --system --gid pudans --home-dir /app --shell /usr/sbin/nologin pudans \
    && mkdir -p /app /var/log/pudans /var/lib/pudans/repository /var/lib/pudans/keys \
    && chown -R pudans:pudans /app /var/log/pudans /var/lib/pudans

WORKDIR /app
COPY --from=build --chown=pudans:pudans \
    /workspace/pudans-${SERVICE}/build/libs/pudans-${SERVICE}-*.jar /app/app.jar

USER pudans

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]