// CI-only: adds Google/Maven Central to plugin repositories without touching the project.
settingsEvaluated { settings ->
    settings.pluginManagement.repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
