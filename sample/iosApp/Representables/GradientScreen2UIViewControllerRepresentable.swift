import SwiftUI
import Composables

public struct GradientScreen2Representable: UIViewControllerRepresentable {
    
    public func makeUIViewController(context: Context) -> UIViewController {
        GradientScreen2UIViewController().make()
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        //unused
    }
}