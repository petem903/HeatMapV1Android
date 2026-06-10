val useLocalMirror = System.getenv("HEATMAP_USE_MIRROR") == "1"

pluginManagement {
    repositories {
        if (useLocalMirror) {
            maven { url = uri("http://127.0.0.1:8765/google/");  isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:8765/plugins/"); isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:8765/central/"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useLocalMirror) {
            maven { url = uri("http://127.0.0.1:8765/google/");  isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:8765/central/"); isAllowInsecureProtocol = true }
            maven { url = uri("http://127.0.0.1:8765/plugins/"); isAllowInsecureProtocol = true }
        } else {
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "HeatMapV1Android"
include(":app")
