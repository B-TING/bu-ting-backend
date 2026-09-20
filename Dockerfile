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
# 컨테이너에 할당된 메모리를 기준으로 힙을 잡는다. 없으면 호스트 전체 메모리를 기준으로 잡아
# 컨테이너 한도를 넘기고 OOM 으로 죽는다.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Seoul -XX:MaxRAMPercentage=75"

# bootJar 이름을 app.jar 로 고정했으므로 글로브를 쓰지 않는다. 버전 규칙이 바뀌어도 깨지지 않는다.
COPY --from=builder /build/build/libs/app.jar app.jar
COPY --from=builder /build/src/main/resources/certs/global-bundle.pem /app/certs/global-bundle.pem

# root 로 돌리지 않는다. 컨테이너가 뚫렸을 때 할 수 있는 일을 줄인다.
RUN addgroup -S buting && adduser -S -G buting buting && chown -R buting:buting /app
USER buting

# 스프링 부트 컨테이너가 외부와 통신할 기본 포트 개방
EXPOSE 8080

# 배포 스크립트의 헬스체크와 별개로, 도커가 직접 컨테이너 상태를 판단할 수 있게 한다.
HEALTHCHECK --interval=30s --timeout=3s --start-period=90s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

# 애플리케이션 실행 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
