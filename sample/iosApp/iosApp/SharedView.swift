import SwiftUI
import SharedComposables

struct SharedView: View {
    @State private var screenState: ScreenState = ScreenState(colors: [UIColor.red, UIColor.blue])
    private let colors: NSArray = [
        UIColor.red,
        UIColor.green,
        UIColor.blue,
        UIColor.yellow
    ]
    
    var body: some View {
        GradientScreenRepresentable(state: $screenState, action: {
            let randomIndexes = (0..<colors.count).shuffled().prefix(2)
            let randomColors = randomIndexes.map { colors[$0] }
            screenState = ScreenState(colors: randomColors)
        })
        .ignoresSafeArea()
    }
}

private struct GradientScreenRepresentable: UIViewControllerRepresentable {
    
    @Binding var state: ScreenState
    let action: () -> Void
    
    func makeUIViewController(context: Context) -> UIViewController {
        return GradientScreenUIViewController().make(randomize: action)
    }
    
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        GradientScreenUIViewController().update(state: state)
    }
}

struct SharedView_Previews: PreviewProvider {
    static var previews: some View {
        SharedView()
    }
}
