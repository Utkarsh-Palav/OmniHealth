plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.omnihealth"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-mail")

	implementation("org.flywaydb:flyway-database-postgresql")

	runtimeOnly("org.postgresql:postgresql")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.9")

	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")
	testAnnotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")

	developmentOnly("org.springframework.boot:spring-boot-docker-compose")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")

	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
}

tasks.withType<JavaCompile> {
	options.encoding = "UTF-8"
	options.compilerArgs.add("-parameters")
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	jvmArgs("-Duser.timezone=Asia/Kolkata")
}