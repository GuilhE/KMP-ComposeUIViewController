import SwiftUI
import SharedData 

public struct ScreenWithExternalDependenciesRepresentable: UIViewControllerRepresentable {
    let param: ExternalClass2

    public func makeUIViewController(context: Context) -> UIViewController {
        ScreenWithExternalDependenciesUIViewController().make(param: param)
    }

    public func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        //unused
    }
}