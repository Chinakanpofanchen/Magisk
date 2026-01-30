plugins {
    id("MagiskPlugin")
}

apply(from = "signing.gradle.kts")

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)

    subprojects.forEach {
        dependsOn(":${it.name}:clean")
    }
}