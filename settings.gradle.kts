import javax.inject.Inject

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

/**
 * Patched versions of libraries the Android Gradle Plugin and lint bring in, flagged by Dependabot. Build tools only:
 * none of them ends up in the app. Never a downgrade, as the highest requested version wins. Remove an entry once the
 * Android Gradle Plugin requires that version or a later one.
 */
val patchedBuildDependencies = listOf(
    "org.bouncycastle:bcprov-jdk18on:1.85",
    "org.bouncycastle:bcpkix-jdk18on:1.85",
    "org.jdom:jdom2:2.0.6.1",
    "org.apache.httpcomponents:httpclient:4.5.13",
    "org.apache.commons:commons-lang3:3.18.0",
    "org.bitbucket.b_c:jose4j:0.9.6",
)

/** Constrains the dependencies of the module it applies to, and so the whole graph, to [constraints]. */
abstract class ConstrainDependencies @Inject constructor(private val constraints: List<String>) : ComponentMetadataRule {
    override fun execute(context: ComponentMetadataContext) = context.details.allVariants {
        withDependencyConstraints { constraints.forEach { add(it) } }
    }
}

// The Android Gradle Plugin, on the build script classpath.
gradle.rootProject {
    buildscript.dependencies.constraints { patchedBuildDependencies.forEach { add("classpath", it) } }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    // Lint, which the Android Gradle Plugin resolves on its own, out of reach of plain dependency constraints.
    components {
        withModule<ConstrainDependencies>("com.android.tools.lint:lint-gradle") { params(patchedBuildDependencies) }
    }
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "CaffeineDose"
include(":app", ":core")
