# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# Runtime stage: JRE only, non-root user
FROM eclipse-temurin:21-jre
RUN groupadd --system ssm && useradd --system --gid ssm --home /app ssm \
    && mkdir -p /app /data && chown ssm:ssm /app /data
WORKDIR /app
COPY --from=build /workspace/target/smart-secure-email-*.jar /app/app.jar
USER ssm
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
