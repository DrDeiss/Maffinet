import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { kotlin("jvm") version "2.2.10" }

// Compile the production pure models directly: no duplicate implementations,
// Android stubs, SDK packages or license acceptance are involved.
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
    sourceSets {
        main {
            kotlin.srcDir("../app/src/main/java")
            kotlin.include(
                "io/maffinet/android/core/domains/DomainList.kt",
                "io/maffinet/android/core/domains/DomainParser.kt",
                "io/maffinet/android/core/domains/BuiltInDomainLists.kt",
                "io/maffinet/android/core/domains/LegacyStrategyAliases.kt",
                "io/maffinet/android/data/domains/UserDomainStore.kt",
                "io/maffinet/android/core/dpibypass/ByeDpiArgumentCompiler.kt",
                "io/maffinet/android/core/services/ServiceProfile.kt",
                "io/maffinet/android/core/services/ServiceCatalog.kt",
                "io/maffinet/android/core/strategy/StrategyEvaluation.kt",
            )
        }
        test {
            kotlin.srcDir("../app/src/test/java")
            kotlin.include(
                "io/maffinet/android/core/domains/**",
                "io/maffinet/android/core/dpibypass/ByeDpiArgumentCompilerTest.kt",
                "io/maffinet/android/data/domains/UserDomainStoreTest.kt",
                "io/maffinet/android/core/services/**",
                "io/maffinet/android/core/strategy/StrategyScorerTest.kt",
                "io/maffinet/verification/**",
            )
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test {
    useJUnit()
    systemProperty("maffinet.nativeFixture", providers.gradleProperty("maffinet.nativeFixture").getOrElse(""))
    testLogging { events("passed", "skipped", "failed") }
}
