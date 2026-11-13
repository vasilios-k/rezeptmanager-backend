FROM gradle:9-jdk25 AS build

WORKDIR /home/gradle/project

COPY --chown=gradle:gradle . .

RUN bash ./gradlew build --no-daemon


FROM eclipse-temurin:25-jdk-jammy

WORKDIR /app

COPY --from=build /home/gradle/project/build/libs/rezeptmanager-backend-0.0.1-SNAPSHOT.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]