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
     *  Name of the destination folder inside iOS project ([iosAppFolderName]) where the Composable files will be copied to
     */
    public var exportFolderName: String = "SharedComposables"

    /**
     *  Auto export generated files to Xcode project
     */
    public var autoExport: Boolean = true

    public fun toList(): List<*> = listOf(iosAppFolderName, iosAppName, targetName, autoExport, exportFolderName)
}