# Build any module:  docker build --build-arg MODULE=order-service -t restaurant/order-service:latest .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN mvn -q -B -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --uid 10001 app
USER app
COPY --from=build /src/${MODULE}/target/*-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
