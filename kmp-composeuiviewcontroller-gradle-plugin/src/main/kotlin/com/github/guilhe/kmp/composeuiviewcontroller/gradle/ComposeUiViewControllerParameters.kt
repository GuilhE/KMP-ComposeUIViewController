package com.github.guilhe.kmp.composeuiviewcontroller.gradle

/**
 * Gradle extension to configure the plugin parameters
 * @property iosAppFolderName Name of the folder containing the iosApp in the root's project tree
 * @property iosAppName Name of the iOS project (name.xcodeproj)
 * @property targetName Name of the iOS project's target
 * @property exportFolderName Name of the destination folder inside iOS project ([iosAppFolderName]) where the Composable files will be copied to when [autoExport] is `true`
 * @property autoExport Enables auto export generated files to Xcode project. If set to false, you will find the generated files under /build/generated/ksp/. Warning: avoid deleting [iosAppFolderName]/[exportFolderName] without first using Xcode to Remove references
 * @property experimentalNamespaceFeature Enables experimental feature to import types from external modules. Read more in CHANGELOG.md#2020-beta1-1611-beta-4
 */
public open class ComposeUiViewControllerParameters {
    /**
     * Name of the folder containing the iosApp in the root's project tree
     */
    public var iosAppFolderName: String = "iosApp"

    /**
     * Name of the iOS project (name.xcodeproj)
     */
    public var iosAppName: String = "iosApp"

    /**
     *  Name of the iOS project's target
     */
    public var targetName: String = "iosApp"

    /**
     *  Name of the destination folder inside iOS project ([iosAppFolderName]) where the Composable files will be copied to when [autoExport] is `true`
     */
    public var exportFolderName: String = "Representables"

    /**
     *  Auto export generated files to Xcode project. If set to `false`, you will find the generated files under `/build/generated/ksp/`. Warning: avoid deleting `[iosAppFolderName]/[exportFolderName]` without first using Xcode to Remove references.
     */
    public var autoExport: Boolean = true

    /**
     *  Enables experimental feature to import types from external modules. Read more in CHANGELOG.md#2020-beta1-1611-beta-4
     */
    public var experimentalNamespaceFeature: Boolean = false
}
