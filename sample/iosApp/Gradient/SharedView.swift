import SwiftUI
import Composables

struct SharedView: View {
    @State private var screenState: Shared_modelsScreenStateExternal = Shared_modelsScreenStateExternal (
        startColor: convertUIColorToKotlinLong(UIColor.red),
        endColor: convertUIColorToKotlinLong(UIColor.blue)
    )
    
    private let colors: [UIColor] = [.red, .green, .blue, .yellow]
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd-MM-yyyy HH:mm:ss"
        return formatter
    }()
    
    var body: some View {
        GradientScreenRepresentable(state: $screenState, randomize: { millis in
            print("Shuffled at \(dateFormatter.string(from: dateFromMilliseconds(milliseconds: millis)))", terminator: "\n")
            let randomIndexes = (0..<colors.count).shuffled().prefix(2)
            let randomColors = randomIndexes.map { colors[$0] }
            screenState = Shared_modelsScreenStateExternal(
                startColor: convertUIColorToKotlinLong(randomColors[0]),
                endColor: convertUIColorToKotlinLong(randomColors[1])
            )
        })
        .ignoresSafeArea()
    }
}

private func dateFromMilliseconds(milliseconds: KotlinLong) -> Date {
    let timeInterval = TimeInterval(truncating: milliseconds) / 1000.0
    return Date(timeIntervalSince1970: timeInterval)
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

#Preview {
    SharedView()
}
