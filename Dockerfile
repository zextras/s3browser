# ──────────────────────────────────────────────
# Stage 1: Build  (Maven + JDK 21)
# ──────────────────────────────────────────────
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Bağımlılıkları önce indir (layer cache kazancı)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Kaynak kodunu kopyala ve paketle
COPY src ./src
RUN mvn package -DskipTests -q

# ──────────────────────────────────────────────
# Stage 2: Runtime  (sadece JRE 21)
# ──────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Güvenlik: root olmayan kullanıcı
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

COPY --from=builder /build/target/s3browser-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

