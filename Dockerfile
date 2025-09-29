FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
COPY build/libs/shopify-reai-sync-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8083
CMD ["java", "-XX:+UseCompactObjectHeaders", "-jar", "/app.jar"]