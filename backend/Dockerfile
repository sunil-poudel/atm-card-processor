# Stage 1: Build & Layer Extraction
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /workspace

COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests clean package
RUN java -Djarmode=tools -jar target/*.jar extract --launcher --layers --destination /extracted

# Stage 2: Minimal Distroless / Alpine Runtime
FROM eclipse-temurin:21-jre-alpine

RUN addgroup -g 10001 -S app && adduser -u 10001 -S app -G app

WORKDIR /app

COPY --from=builder --chown=app:app /extracted/dependencies/ ./
COPY --from=builder --chown=app:app /extracted/spring-boot-loader/ ./
COPY --from=builder --chown=app:app /extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=app:app /extracted/application/ ./

USER app

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=15s \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
