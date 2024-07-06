import SwiftUI
import Composables

public struct TestScreenRepresentable: UIViewControllerRepresentable {
    let state: Shared_modelsDummyExternal

    public func makeUIViewController(context: Context) -> UIViewController {
        TestScreenUIViewController().make(state: state)
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        //unused
    }
}