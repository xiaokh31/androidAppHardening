pluginManagement {
    repositories {
        google()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
                -> useModule("com.android.tools.build:gradle:${requested.version}")

                "org.jetbrains.kotlin.jvm" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

buildCache {
    local {
        isEnabled = true
    }
}

rootProject.name = "androidAppHardening"

include(
    ":host:cli",
    ":host:apk-inspector",
    ":host:axml",
    ":host:container",
    ":host:repacker",
    ":runtime:bootstrap",
    ":runtime:native",
    ":runtime:policy",
    ":fixtures:android",
    ":integration-tests",
    ":benchmarks:host",
    ":benchmarks:android",
    ":tools:validation",
    ":distribution",
)
