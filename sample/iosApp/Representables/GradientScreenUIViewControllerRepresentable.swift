import SwiftUI
import Composables

public struct GradientScreenRepresentable: UIViewControllerRepresentable {
    @Binding var state: ScreenState
    let randomize: (KotlinLong) -> Void
    let tp: TestParameter2

    public func makeUIViewController(context: Context) -> UIViewController {
        GradientScreenUIViewController().make(randomize: randomize, tp: tp)
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        GradientScreenUIViewController().update(state: state)
    }
}