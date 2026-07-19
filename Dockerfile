FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew ./
COPY gradle ./gradle
COPY ai-action-contract ./ai-action-contract
COPY backend ./backend

RUN chmod +x ./gradlew
WORKDIR /workspace/backend

RUN ../gradlew buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV HOST=0.0.0.0
ENV KTOR_DEVELOPMENT=false

COPY --from=build /workspace/backend/build/libs/cyl-backend.jar ./cyl-backend.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/cyl-backend.jar"]
