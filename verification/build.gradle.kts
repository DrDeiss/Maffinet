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
                "io/maffinet/android/core/domains/DomainSourceDownloader.kt",
                "io/maffinet/android/core/domains/BuiltInDomainLists.kt",
                "io/maffinet/android/core/domains/LegacyStrategyAliases.kt",
                "io/maffinet/android/data/domains/UserDomainStore.kt",
                "io/maffinet/android/core/dpibypass/ByeDpiArgumentCompiler.kt",
                "io/maffinet/android/core/services/ServiceProfile.kt",
                "io/maffinet/android/core/services/ServiceCatalog.kt",
                "io/maffinet/android/core/strategy/StrategyEvaluation.kt",
                "io/maffinet/android/core/strategy/DefaultStrategyCatalog.kt",
                "io/maffinet/android/core/strategy/ProbeTargets.kt",
                "io/maffinet/android/core/strategy/HttpProbeBodyValidator.kt",
                "io/maffinet/android/core/access/HostAccessPolicy.kt",
                "io/maffinet/android/core/access/RouteHintRegistry.kt",
                "io/maffinet/android/core/access/HostAccessDecisionCache.kt",
                "io/maffinet/android/core/access/HostAccessRecovery.kt",
                "io/maffinet/android/core/access/HostAccessWorkQueue.kt",
                "io/maffinet/android/core/access/AutomaticAccessArguments.kt",
                "io/maffinet/android/core/access/AutomaticAccessStatus.kt",
                "io/maffinet/android/core/access/DnsProbeResolver.kt",
                "io/maffinet/android/core/access/GenericHttpsProbe.kt",
                "io/maffinet/android/core/connection/ConnectionModes.kt",
                "io/maffinet/android/core/tgproxy/ProxyLifecycleState.kt",
                "io/maffinet/android/core/dns/**",
                "io/maffinet/android/ui/components/DnsPresets.kt",
            )
        }
        test {
            kotlin.srcDir("../app/src/test/java")
            kotlin.include(
                "io/maffinet/android/core/domains/**",
                "io/maffinet/android/core/dns/**",
                "io/maffinet/android/ui/components/DnsPresetsTest.kt",
                "io/maffinet/android/core/dpibypass/ByeDpiArgumentCompilerTest.kt",
                "io/maffinet/android/data/domains/UserDomainStoreTest.kt",
                "io/maffinet/android/core/services/**",
                "io/maffinet/android/core/strategy/StrategyScorerTest.kt",
                "io/maffinet/android/core/strategy/ProbeTargetsTest.kt",
                "io/maffinet/android/core/strategy/HttpProbeBodyValidatorTest.kt",
                "io/maffinet/android/core/access/**",
                "io/maffinet/android/core/connection/ConnectionModesTest.kt",
                "io/maffinet/android/core/tgproxy/ProxyLifecycleStateTest.kt",
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
