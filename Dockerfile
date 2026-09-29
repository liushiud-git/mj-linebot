# 第一階段：用 Maven 編譯出 JAR（target/ 不納入版控，需在建置時產生）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# 第二階段：只帶 JAR 進執行環境
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/liushiud-mj-linebot-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
