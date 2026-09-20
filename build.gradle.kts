import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    // 코드 스타일 통일을 위한 Spotless 플러그인
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.butingbe"
version = "0.0.1-SNAPSHOT"
description = "buting-be"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// Spotless 세부 규칙 정의 (Google Java Format 적용)
spotless {
    java {
        googleJavaFormat("1.33.0") // Java 25 compatible Google Java Format
        trimTrailingWhitespace() // 줄 끝 공백 제거
        endWithNewline() // 파일 끝에 개행 추가
        targetExclude("build/**/*") // 빌드 결과물은 포맷팅에서 제외
    }
}

jacoco {
    toolVersion = "0.8.15"
}

val jacocoCoverageExcludes =
    listOf(
        "**/ButingBeApplication*",
        "**/global/config/**",
        "**/*Dto*",
    )

tasks.jacocoTestReport {
    dependsOn(tasks.test)

    classDirectories.setFrom(
        files(
            classDirectories.files.map {
                fileTree(it) {
                    exclude(jacocoCoverageExcludes)
                }
            }
        )
    )

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)

    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)

    violationRules {
        rule {
            enabled = true
            element = "BUNDLE"

            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation("org.springframework.ai:spring-ai-starter-model-openai")
    implementation(platform("software.amazon.awssdk:bom:2.31.63"))
    implementation("software.amazon.awssdk:s3")
    // Web & Validation & Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Database migrations
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // 스케줄러 분산 잠금. 인스턴스가 늘어나도 라운드 정산 같은 작업이 두 번 돌지 않게 한다.
    implementation("net.javacrumbs.shedlock:shedlock-spring:6.9.2")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:6.9.2")

    // Security & OAuth 2.0
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.projectlombok:lombok")

    // Test Dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.restdocs:spring-restdocs-mockmvc")

    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.testcontainers:testcontainers:1.21.4")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testImplementation("org.springframework.security:spring-security-test")

    implementation("org.springframework.boot:spring-boot-starter-websocket")
}

// 배포 스크립트와 Dockerfile 이 파일 이름에 기대므로 버전 규칙과 무관하게 고정한다.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("app.jar")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // 운영 JVM 은 ButingBeApplication.main 에서 기본 타임존을 Asia/Seoul 로 고정한다. 테스트는 main 을
    // 거치지 않아 러너 OS 타임존을 그대로 쓴다 -- 로컬(KST)과 GitHub Actions(UTC)가 달라진다.
    // 이때 spring.jackson.time-zone(Asia/Seoul)과 JVM 기본 타임존이 어긋나면 OffsetDateTime 이
    // JSON 직렬화/역직렬화를 거치며 같은 시각인데 오프셋만 바뀌어(Z -> +09:00) equals 비교가 깨진다.
    // 테스트 JVM 도 운영과 같은 타임존으로 고정한다.
    systemProperty("user.timezone", "Asia/Seoul")
    outputs.dir(layout.buildDirectory.dir("generated-snippets"))
    finalizedBy(tasks.jacocoTestReport)
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named<BootRun>("bootRun") {
    args("--spring.config.import=optional:classpath:application-oauth.yaml")

    val envFile = layout.projectDirectory.file(".env").asFile
    if (envFile.exists()) {
        envFile.readLines()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .forEach { line ->
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim().trim('"', '\'')
                environment(key, value)
            }
    }
}

tasks.register<Copy>("openapi3") {
    dependsOn(tasks.test)
    from(layout.projectDirectory.file("src/main/resources/static/docs/openapi3.yaml"))
    into(layout.buildDirectory.dir("api-spec"))
}
