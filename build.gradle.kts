plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}