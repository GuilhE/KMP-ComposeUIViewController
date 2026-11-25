plugins {
	id("kmp-composeuiviewcontroller-publish")
	alias(libs.plugins.kotlin.jvm)
	alias(libs.plugins.kotlinx.serialization)
}

dependencies {
	implementation(libs.ksp.api)
	implementation(libs.kotlinx.serialization)
	implementation(projects.kmpComposeuiviewcontrollerCommon)

	testImplementation(libs.test.kotlin)
	testImplementation(libs.test.compile.core)
	testImplementation(libs.test.compile.ksp)
	// kctfork bundles an older KSP implementation; keep it in sync with ksp-api to avoid NoSuchMethodError at runtime
	testImplementation(libs.ksp.impl)
	testImplementation(libs.ksp.commonDeps)
	testImplementation(libs.ksp.aaEmbeddable)
	testImplementation(projects.kmpComposeuiviewcontrollerAnnotations)
}

kotlin {
	explicitApi()
	jvmToolchain(17)
}

java {
	withSourcesJar()
}

tasks.compileKotlin.configure {
	compilerOptions {
		freeCompilerArgs.add("-jvm-default=no-compatibility")
	}
}