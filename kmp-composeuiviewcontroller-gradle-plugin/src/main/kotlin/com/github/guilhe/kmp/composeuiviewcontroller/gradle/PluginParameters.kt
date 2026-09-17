@file:Suppress("SpellCheckingInspection")

package com.github.guilhe.kmp.composeuiviewcontroller.gradle

/**
 * Gradle extension to configure the plugin parameters.
 *
 * Parameters are ordered below from most to least commonly configured.
 *
 * @property iosAppName Name of the iOS project (name.xcodeproj)
 * @property targetName Name of the iOS project's target
 * @property iosAppFolderName Name of the folder containing the iosApp in the root's project tree
 * @property exportFolderName Name of the folder inside the iOS project ([iosAppFolderName]) that holds the generated files when [autoExport] is
 * `true`. By default, (SPM export) this is the local Swift Package folder — containing `Package.swift` and `Sources/[exportFolderName]/` — created
 * by `createRepresentablesPackage` and kept in sync on every build. When [legacyMode] is `true`, the generated `UIViewControllerRepresentable` files are copied directly into this folder instead.
 * @property iosDeploymentTarget Minimum iOS version for the generated SPM package (e.g. `"16"`). Not used when [legacyMode] is `true`.
 * @property swiftToolsVersion Swift tools version declared in the generated `Package.swift` (e.g. `"5.9"`, `"6.0"`). Not used when [legacyMode] is `true`.
 * @property autoExport Auto export generated files to Xcode project on every build. By default, SPM export, this keeps the local Swift Package at
 * `[iosAppFolderName]/[exportFolderName]/` in sync with the KSP output. When [legacyMode] is `true`, it instead copies the files directly and
 * updates `project.pbxproj`. If set to `false`, neither happens, and you will find the generated files under `/build/generated/ksp/`.
 * Warning: avoid deleting `[iosAppFolderName]/[exportFolderName]` without first using Xcode to `Remove references`, when using legacy mode.
 * @property legacyMode When `true`, falls back to the previous xcodeproj-gem-based export (manipulates `project.pbxproj` directly on every build)
 * instead of the default local SPM package at `[iosAppFolderName]/[exportFolderName]/`. Kept for projects that can't yet use SPM. New projects should not need this.
 */
public open class PluginParameters {
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
	 * Name of the folder containing the iosApp in the root's project tree
	 */
	public var iosAppFolderName: String = "iosApp"
		set(value) {
			require(value.isNotBlank()) { "iosAppFolderName cannot be blank" }
			field = value
		}

	/**
	 *  Name of the folder inside the iOS project ([iosAppFolderName]) that holds the generated files when [autoExport] is `true`. By default,
	 *  (SPM export) this is the local Swift Package folder — containing `Package.swift` and `Sources/[exportFolderName]/` — created by
	 *  `createRepresentablesPackage` and kept in sync on every build. When [legacyMode] is `true`, the generated `UIViewControllerRepresentable`
	 *  files are copied directly into this folder instead.
	 */
	public var exportFolderName: String = "Representables"
		set(value) {
			require(value.isNotBlank()) { "exportFolderName cannot be blank" }
			field = value
		}

	/**
	 * Minimum iOS deployment target for the generated SPM package. Not used when [legacyMode] is `true`.
	 */
	public var iosDeploymentTarget: String = "26"
		set(value) {
			require(value.isNotBlank()) { "iosDeploymentTarget cannot be blank" }
			field = value
		}

	/**
	 * Swift tools version declared in the generated `Package.swift`. Not used when [legacyMode] is `true`.
	 */
	public var swiftToolsVersion: String = "6.2"
		set(value) {
			require(value.isNotBlank()) { "swiftToolsVersion cannot be blank" }
			field = value
		}

	/**
	 * Auto export generated files to Xcode project on every build. By default, SPM export, this keeps the local Swift Package at
	 * `[iosAppFolderName]/[exportFolderName]/` in sync with the KSP output. When [legacyMode] is `true`, it instead copies the files directly and
	 * updates `project.pbxproj`. If set to `false`, neither happens, and you will find the generated files under `/build/generated/ksp/`.
	 * Warning: avoid deleting `[iosAppFolderName]/[exportFolderName]` without first using Xcode to `Remove references`, when using legacy mode.
	 */
	public var autoExport: Boolean = true

	/**
	 * When `true`, falls back to the previous xcodeproj-gem-based export instead of the default local SPM package at
	 * `[iosAppFolderName]/[exportFolderName]/`. Kept for projects that can't yet use SPM — new projects should not need this.
	 */
	public var legacyMode: Boolean = false
}
