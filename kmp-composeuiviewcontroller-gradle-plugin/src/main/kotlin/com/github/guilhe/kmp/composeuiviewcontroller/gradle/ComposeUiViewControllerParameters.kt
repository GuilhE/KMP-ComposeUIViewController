@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

/**
 * Gradle extension to configure the plugin parameters
 * @property iosAppFolderName Name of the folder containing the iosApp in the root's project tree
 * @property iosAppName Name of the iOS project (name.xcodeproj)
 * @property targetName Name of the iOS project's target
 * @property exportFolderName Name of the destination folder inside iOS project ([iosAppFolderName]) where the generated files will be copied to when
 * [autoExport] is `true`
 * @property autoExport Enables auto export generated files to Xcode project. If set to false, you will find the generated files under `/build/generated/ksp/`. Warning: avoid deleting [iosAppFolderName]/[exportFolderName] without first using Xcode to `Remove references`
 */
public open class ComposeUiViewControllerParameters {
    /**
     * Name of the folder containing the iosApp in the root's project tree
     */
    public var iosAppFolderName: String = "iosApp"
        set(value) {
            require(value.isNotBlank()) { "iosAppFolderName cannot be blank" }
            field = value
        }

    /**
     * Name of the iOS project (name.xcodeproj)
     */
    public var iosAppName: String = "iosApp"
        set(value) {
            require(value.isNotBlank()) { "iosAppName cannot be blank" }
            field = value
        }

    /**
     *  Name of the iOS project's target
     */
    public var targetName: String = "iosApp"
        set(value) {
            require(value.isNotBlank()) { "targetName cannot be blank" }
            field = value
        }

    /**
     *  Name of the destination folder inside iOS project ([iosAppFolderName]) where the Composable files will be copied to when [autoExport] is `true`
     */
    public var exportFolderName: String = "Representables"
        set(value) {
            require(value.isNotBlank()) { "exportFolderName cannot be blank" }
            field = value
        }

    /**
     *  Auto export generated files to Xcode project. If set to `false`, you will find the generated files under `/build/generated/ksp/`. Warning: avoid deleting `[iosAppFolderName]/[exportFolderName]` without first using Xcode to `Remove references`.
     */
    public var autoExport: Boolean = true
}
