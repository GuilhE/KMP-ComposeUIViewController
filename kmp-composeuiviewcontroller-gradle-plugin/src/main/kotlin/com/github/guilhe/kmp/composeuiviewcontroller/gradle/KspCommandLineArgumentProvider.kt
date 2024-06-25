package com.github.guilhe.kmp.composeuiviewcontroller.gradle

import org.gradle.process.CommandLineArgumentProvider

internal class KspCommandLineArgumentProvider(private val parameters: ComposeUiViewControllerParameters) : CommandLineArgumentProvider {

    override fun asArguments(): Iterable<String> = listOfNotNull(
        "composeuiviewcontroller.iosAppFolderName=${parameters.iosAppFolderName}",
        "composeuiviewcontroller.iosAppName=${parameters.iosAppName}",
        "composeuiviewcontroller.targetName=${parameters.targetName}",
        "composeuiviewcontroller.autoExport=${parameters.autoExport}"
    )
}