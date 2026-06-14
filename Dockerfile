FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY gradlew settings.backend.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY backend ./backend

RUN chmod +x ./gradlew
RUN ./gradlew -c settings.backend.gradle.kts :backend:buildFatJar --no-daemon

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV HOST=0.0.0.0
ENV KTOR_DEVELOPMENT=false

COPY --from=build /workspace/backend/build/libs/cyl-backend.jar ./cyl-backend.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/cyl-backend.jar"]
