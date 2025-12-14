plugins {
    alias(global.plugins.kotlin.multiplatform) apply false
    alias(local.plugins.compose.multiplatform) apply false
    alias(local.plugins.compose.compiler) apply false
}

tasks.register("exportFrameworkForXcode") {
    dependsOn(":shared:embedAndSignAppleFrameworkForXcode")
    finalizedBy(":shared-models:embedAndSignAppleFrameworkForXcode")
}