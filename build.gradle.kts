import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    application
}

group = "ru.mcpserver"
version = "1.0.0"

application {
    mainClass.set("ru.mcpserver.api.DataApiServerKt")
    applicationDefaultJvmArgs = listOf("-Xmx512m")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.datetime)

    implementation(libs.mcp.server)
}

val shadowJarApi by tasks.registering(ShadowJar::class) {
    description = "Builds a fat JAR for the API data server (port 4453) with all get_* and search_* tools"
    archiveClassifier.set("api")
    mergeServiceFiles()
    manifest.attributes("Main-Class" to "ru.mcpserver.api.DataApiServerKt")
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

val shadowJarPipeline by tasks.registering(ShadowJar::class) {
    description = "Builds a fat JAR for the Pipeline server (port 4455) with summarize_data and save_data tools"
    archiveClassifier.set("pipeline")
    mergeServiceFiles()
    manifest.attributes("Main-Class" to "ru.mcpserver.pipeline.PipelineServerKt")
    from(sourceSets.main.get().output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
}

tasks.build {
    dependsOn(shadowJarApi, shadowJarPipeline)
}
