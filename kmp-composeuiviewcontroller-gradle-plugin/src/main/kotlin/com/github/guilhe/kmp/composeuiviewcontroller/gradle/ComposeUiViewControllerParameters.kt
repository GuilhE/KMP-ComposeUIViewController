package com.github.guilhe.kmp.composeuiviewcontroller.gradle

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
    public var exportFolderName: String = "Composables"

    /**
     *  Auto export generated files to Xcode project
     *
     *  If set to `false`, you will find the generated files under `/build/generated/ksp/`.
     *  Warning: avoid deleting `[iosAppFolderName]/[exportFolderName]` without first using Xcode to Remove references.
     */
    public var autoExport: Boolean = true
}