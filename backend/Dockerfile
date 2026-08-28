FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /workspace/target/idax-ostris-backend-0.1.0.jar app.jar
EXPOSE 8095
USER 10001
ENTRYPOINT ["java","-jar","/app/app.jar"]
