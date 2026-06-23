import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    // 코드 스타일 통일을 위한 Spotless 플러그인
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.example"
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
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web & Validation & Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // 👇 [미래 확장] 주석 해제하여 사용할 라이브러리 구역
    // 1. 데이터베이스 및 ORM (JPA) 라이브러리 추가
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 2. PostgreSQL (주 DBMS 연동 시 주석 해제)
    runtimeOnly("org.postgresql:postgresql")

    // Database migrations
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // 3. Redis & Spring Session (세션 관리용 Redis 연동 시 주석 해제)
    // implementation("org.springframework.boot:spring-boot-starter-data-redis")
    // implementation("org.springframework.session:spring-session-data-redis")

    // 4. Spring Security & OAuth 2.0 (소셜 로그인 및 보안 적용 시 주석 해제)
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // AI agent

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

    testImplementation("org.testcontainers:testcontainers:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.testcontainers:postgresql:1.20.1")

    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
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
