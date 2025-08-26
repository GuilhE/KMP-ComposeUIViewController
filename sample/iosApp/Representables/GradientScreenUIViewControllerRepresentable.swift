import SwiftUI
import Models
import Composables

import ExportedKotlinPackages
typealias ScreenStateExternal = ExportedKotlinPackages.com.sample.models.ScreenStateExternal
typealias GradientScreenUIViewController = ExportedKotlinPackages.com.sample.shared.GradientScreenUIViewController

public struct GradientScreenRepresentable: UIViewControllerRepresentable {
    @Binding var state: ScreenStateExternal
    let randomize: (Int64) -> Void

    public func makeUIViewController(context: Context) -> UIViewController {
        GradientScreenUIViewController.shared.make(randomize: randomize)
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        GradientScreenUIViewController.shared.update(state: state)
    }
}
