FROM eclipse-temurin:25-jre AS builder

WORKDIR /application
ARG JAR_FILE=/application/build/libs/*.jar
COPY $JAR_FILE application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
LABEL maintainer="pudans"
WORKDIR /application
COPY --from=builder /application/extracted/dependencies/ ./
COPY --from=builder /application/extracted/spring-boot-loader/ ./
COPY --from=builder /application/extracted/snapshot-dependencies/ ./
COPY --from=builder /application/extracted/application/ ./

ENV JVM_OPTS="" \
    PUDANS_WORK_DIR="/root/.pudans" \
    SPRING_CONFIG_LOCATION="optional:classpath:/;optional:file:/root/.pudans/" \
    TZ=Asia/Shanghai

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime \
    && echo $TZ > /etc/timezone

RUN java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh -jar application.jar --pudans.work-dir=/tmp/pudans \
    && rm -rf /tmp/pudans

EXPOSE 8090

ENTRYPOINT ["sh", "-c", "exec java $JVM_OPTS -XX:SharedArchiveFile=application.jsa -jar application.jar \"$@\"", "--"]
