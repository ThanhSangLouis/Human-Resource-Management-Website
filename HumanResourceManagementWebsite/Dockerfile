FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

ENV PORT=8080

COPY target/hcmute-1.0.jar app.jar

EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
