import SwiftUI
import SharedComposables

public struct GradientScreenRepresentable: UIViewControllerRepresentable {
    @Binding var state: ScreenState
    let randomize: () -> Void
    
    public func makeUIViewController(context: Context) -> UIViewController {
        return GradientScreenUIViewController().make(randomize: randomize)
    }
    
    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        GradientScreenUIViewController().update(state: state)
    }
}