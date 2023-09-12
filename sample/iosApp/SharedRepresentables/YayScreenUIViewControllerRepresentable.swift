import SwiftUI
import SharedComposables

public struct YayScreenRepresentable: UIViewControllerRepresentable {
    @Binding var state: ScreenState
    let randomize: () -> Void
    
    public func makeUIViewController(context: Context) -> UIViewController {
        return YayScreenUIViewController().make(randomize: randomize)
    }
    
    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        YayScreenUIViewController().update(state: state)
    }
}