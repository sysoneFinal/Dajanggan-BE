# Build stage
FROM gradle:8.5-jdk17 AS build
WORKDIR /app
# Gradle 파일 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew ./
# 소스 코드 복사
COPY src ./src
# 빌드 (테스트 제외)
RUN gradle build --no-daemon -x test
# Run stage
FROM openjdk:17-jdk-slim
WORKDIR /app
# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar
# 환경변수 설정
ENV SPRING_PROFILES_ACTIVE=prod
# 포트 노출
EXPOSE 8080
# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]