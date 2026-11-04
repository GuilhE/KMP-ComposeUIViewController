package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import java.io.File
import javax.inject.Inject
import org.gradle.api.flow.BuildWorkResult
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Deletes the plugin's temporary folder when the build fails.
 *
 * It replaces `Gradle.buildFinished`, whose registration isn't supported by the Configuration Cache. Dataflow actions
 * run at the end of every build, on Configuration Cache hits and misses alike, and expose the build result.
 */
internal abstract class CleanTempFolderOnFailure : FlowAction<CleanTempFolderOnFailure.Parameters> {
	interface Parameters : FlowParameters {
		@get:Input
		val buildWorkResult: Property<BuildWorkResult>

		@get:Input
		val tempFolder: Property<String>
	}

	override fun execute(parameters: Parameters) {
		if (parameters.buildWorkResult.get().failure.isPresent) {
			File(parameters.tempFolder.get()).takeIf { it.exists() }?.deleteRecursively()
		}
	}
}

/**
 * Registers [CleanTempFolderOnFailure]. [FlowScope] and [FlowProviders] only exist since Gradle 8.1, so this class must only
 * be instantiated (through `ObjectFactory.newInstance`) on versions that provide them.
 */
internal abstract class BuildFailureCleanup @Inject constructor(
	private val flowScope: FlowScope,
	private val flowProviders: FlowProviders
) {
	fun cleanOnFailure(tempFolder: File) {
		flowScope.always(CleanTempFolderOnFailure::class.java) { spec ->
			spec.parameters.buildWorkResult.set(flowProviders.buildWorkResult)
			spec.parameters.tempFolder.set(tempFolder.path)
		}
	}
}
