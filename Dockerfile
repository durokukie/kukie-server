# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
# root 대신 jar를 실행하기 위한 nologin 유저
RUN useradd --system --no-create-home --shell /usr/sbin/nologin kukie
COPY --from=build /app/build/libs/app.jar /app/app.jar
USER kukie
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
