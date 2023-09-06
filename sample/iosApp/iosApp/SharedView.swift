import SwiftUI
import SharedComposables

struct SharedView: View {
    @State private var screenState: ScreenState = ScreenState(
        startColor: convertUIColorToKotlinLong(UIColor.red),
        endColor: convertUIColorToKotlinLong(UIColor.blue)
    )
    
    private let colors: [UIColor] = [.red, .green, .blue, .yellow]
    
    var body: some View {
        GradientScreenRepresentable(state: $screenState, action: {
            let randomIndexes = (0..<colors.count).shuffled().prefix(2)
            let randomColors = randomIndexes.map { colors[$0] }
            screenState = ScreenState(
                startColor: convertUIColorToKotlinLong(randomColors[0]),
                endColor: convertUIColorToKotlinLong(randomColors[1])
            )
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

private func convertUIColorToKotlinLong(_ color: UIColor) -> Int64 {
    var red: CGFloat = 0
    var green: CGFloat = 0
    var blue: CGFloat = 0
    var alpha: CGFloat = 0
    
    color.getRed(&red, green: &green, blue: &blue, alpha: &alpha)
    
    let redValue = Int64(red * 255)
    let greenValue = Int64(green * 255)
    let blueValue = Int64(blue * 255)
    let alphaValue = Int64(alpha * 255)
    
    let hexValue = (alphaValue << 24) | (redValue << 16) | (greenValue << 8) | blueValue
    return hexValue
}

struct SharedView_Previews: PreviewProvider {
    static var previews: some View {
        SharedView()
    }
}
