# =====================================================================
# Imagen de producción: una sola app que entrega la API y la aplicación web.
# Render la construye desde este archivo (ver render.yaml). Las pruebas no se
# corren aquí: ya las corre la integración continua de GitHub antes de publicar.
# =====================================================================

# --- 1) Aplicación web (React + Vite) ---
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# --- 2) API (Spring Boot), con la aplicación web adentro ---
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# Por si el archivo llega con saltos de línea de Windows
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./mvnw -B -q package -DskipTests

# --- 3) Imagen final: solo el Java necesario para correr ---
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd --system --create-home app
COPY --from=backend /app/backend/target/inventario-*.jar inventario.jar
# Servidor gratuito con 512 MB y poca CPU: memoria fija y la misma al construir y al correr (la caché
# AOT lo exige; con menos de 64 MB iniciales no carga), recolector simple y arranque rápido
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-Xms64m -Xmx256m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
# Arranque más rápido (caché AOT de Java 25): se arranca la app una vez al construir la imagen, sin base de
# datos, y se guardan las clases ya cargadas. Al despertar en Render, la app las reutiliza.
RUN java -Djarmode=tools -jar inventario.jar extract --destination app && rm inventario.jar \
    && java -XX:AOTCacheOutput=app/app.aot -Dspring.context.exit=onRefresh \
        -DAPP_CLAVE_RECORDAR=solo-para-construir-la-imagen \
        -Dspring.datasource.url=jdbc:postgresql://localhost:1/sin-base \
        -Dspring.flyway.enabled=false \
        -Dspring.jpa.hibernate.ddl-auto=none \
        -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
        -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect \
        -jar app/inventario.jar \
    && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:AOTCache=app/app.aot", "-jar", "app/inventario.jar"]
