# dockerfile
# Use Alpine with OpenJDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder

# Define user and application port
ARG USERNAME=edc_be-sb
ARG JAR_FILE=${APP_HOME}/target/*.jar
ENV APP_HOME=/app
ENV JAVA_OPTS=""

# Create a non-root user
RUN addgroup -S ${USERNAME} && adduser -S ${USERNAME} -G ${USERNAME}

# Set the user to run the application
USER ${USERNAME}:${USERNAME}

# Create the application directory
WORKDIR ${APP_HOME}

# Copy the JAR (no wildcard in COPY)
COPY ${JAR_FILE} app.jar

# Expose the application port (change if needed)
EXPOSE 8080

# Runtime command
CMD ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]