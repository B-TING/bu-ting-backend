# =========================================================================
# 1단계: 빌드 스테이지 (Gradle을 이용해 자바 코드를 컴파일하고 완제품 JAR 생성)
# =========================================================================
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /build

# 빌드 환경 설정에 필요한 핵심 파일들만 우선 복사 (도커 레이어 캐싱 활용)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# 라이브러리 미리 다운로드 (소스 코드가 바뀌어도 이 단계는 캐싱되어 빌드 속도가 엄청나게 빨라집니다)
RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon

# 실제 자바 소스 코드 복사 후 실행 파일(bootJar) 빌드
# (테스트와 Spotless 검사는 로컬/CI에서 검증하므로 컨테이너 빌드 시에는 제외하여 속도 최적화)
COPY src src
RUN ./gradlew bootJar -x test -x spotlessCheck --no-daemon

# =========================================================================
# 2단계: 실행 스테이지 (실제 서버가 구동되는 가볍고 안전한 런타임 환경)
# =========================================================================
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

ENV TZ=Asia/Seoul
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul"

# 빌드 스테이지에서 생성된 껍데기 없는 순수 완제품 JAR 파일만 쏙 빼서 복사
COPY --from=builder /build/build/libs/*-SNAPSHOT.jar app.jar

# 스프링 부트 컨테이너가 외부와 통신할 기본 포트 개방
EXPOSE 8080

# 애플리케이션 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]