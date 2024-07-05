import SwiftUI
import Composables

public struct GradientScreenRepresentable: UIViewControllerRepresentable {
    @Binding var state: Shared_modelsScreenStateExternal
    let randomize: (KotlinLong) -> Void

    public func makeUIViewController(context: Context) -> UIViewController {
        GradientScreenUIViewController().make(randomize: randomize)
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        GradientScreenUIViewController().update(state: state)
    }
}