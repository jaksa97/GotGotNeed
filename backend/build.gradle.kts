plugins {
	alias(libs.plugins.kotlinJvm)
	alias(libs.plugins.kotlinSpring)
	alias(libs.plugins.kotlinJpa)
	alias(libs.plugins.springBoot)
	alias(libs.plugins.springDependencyManagement)
}

group = "com.gotgotneed"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

dependencies {
	implementation(libs.spring.boot.starterDataJpa)
	implementation(libs.spring.boot.starterFlyway)
	implementation(libs.spring.boot.starterSecurity)
	implementation(libs.spring.boot.starterValidation)
	implementation(libs.spring.boot.starterWebmvc)
	implementation(libs.flyway.mysql)
	implementation(libs.kotlin.reflect)
	implementation(libs.jackson.moduleKotlin)
	developmentOnly(libs.spring.boot.devtools)
	runtimeOnly(libs.mysql.connectorJ)
	testImplementation(libs.spring.boot.starterDataJpaTest)
	testImplementation(libs.spring.boot.starterFlywayTest)
	testImplementation(libs.spring.boot.starterSecurityTest)
	testImplementation(libs.spring.boot.starterValidationTest)
	testImplementation(libs.spring.boot.starterWebmvcTest)
	testImplementation(libs.kotlin.testJunit5)
	testRuntimeOnly(libs.junit.platformLauncher)
	implementation(libs.springdoc.openapi)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
