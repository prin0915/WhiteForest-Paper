plugins {
    kotlin("jvm") version "2.2.10"
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "org.prin"

// ===========================
// 자동 버전 증가 로직
// ===========================
val versionPropsFile = file("gradle.properties")

var major = project.findProperty("versionMajor")?.toString()?.toInt() ?: 1
var minor = project.findProperty("versionMinor")?.toString()?.toInt() ?: 0
var patch = project.findProperty("versionPatch")?.toString()?.toInt() ?: 0

val versionLevel = project.findProperty("versionLevel")?.toString()?.lowercase() ?: "patch"

when (versionLevel) {
    "major" -> {
        major += 1
        minor = 0
        patch = 0
    }
    "minor" -> {
        minor += 1
        patch = 0
    }
    "patch" -> {
        patch += 1
    }
    else -> throw GradleException("versionLevel must be major, minor, or patch")
}

version = "$major.$minor.$patch"
println("빌드 버전: $version")

tasks.register("updateVersion") {
    doLast {
        val propsText = versionPropsFile.readText()
            .replace(Regex("versionMajor=\\d+"), "versionMajor=$major")
            .replace(Regex("versionMinor=\\d+"), "versionMinor=$minor")
            .replace(Regex("versionPatch=\\d+"), "versionPatch=$patch")
        versionPropsFile.writeText(propsText)
        println("자동 버전 업데이트 완료: $version")
    }
}

tasks.build {
    finalizedBy("updateVersion")
}

// ===========================
// 저장소 및 종속성
// ===========================
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")

    // Kotlin/JDA 포함 (shadowJar 안에 들어감)
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("net.dv8tion:JDA:5.0.0-beta.24") // 5.6.1이 안정화 안되었으면 최신 beta 사용 가능
}

// ===========================
// Paper 서버 실행 설정
// ===========================
tasks {
    runServer {
        minecraftVersion("1.21")
    }
}

// ===========================
// Kotlin JVM 설정
// ===========================
val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

// ===========================
// shadowJar 설정
// ===========================
tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("WhiteForest")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    // 메인 클래스 설정
    manifest {
        attributes(mapOf("Main-Class" to "org.prin.Main")) // ← 여기를 실제 메인 클래스로 바꿔주세요
    }

    // Paper가 중복 클래스 충돌 방지 (특히 JDA)
    relocate("net.dv8tion.jda", "org.prin.libs.jda")
    relocate("kotlin", "org.prin.libs.kotlin")
}
