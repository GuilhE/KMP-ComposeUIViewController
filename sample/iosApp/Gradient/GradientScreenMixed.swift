import Composables
import SwiftUI

struct GradientScreenMixed: View {
    @State private var screenState: ScreenState = .init(
        startColor: Color.red.asInt64,
        endColor: Color.blue.asInt64
    )

    @State private var viewController: ButtonViewController?

    var body: some View {
        GradientScreenMixedRepresentable(
            state: $screenState,
            controller: viewController ?? createViewController()
        )
        .ignoresSafeArea()
        .onAppear {
            if viewController == nil {
                viewController = createViewController()
            }
        }
    }

    private func createViewController() -> ButtonViewController {
        let controller = ButtonViewController()
        controller.onColorsChanged = { [self] colors in
            if colors.count >= 2 {
                screenState = ScreenState(
                    startColor: colors[0].asInt64,
                    endColor: colors[1].asInt64
                )
            }
        }
        return controller
    }
}

private extension Color {
    var asInt64: Int64 {
        let uiColor = UIColor(self)
        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0

        uiColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha)

        let redValue = Int64(red * 255)
        let greenValue = Int64(green * 255)
        let blueValue = Int64(blue * 255)
        let alphaValue = Int64(alpha * 255)

        return (alphaValue << 24) | (redValue << 16) | (greenValue << 8) | blueValue
    }

    init(argb: Int64) {
        let alpha = Double((argb >> 24) & 0xFF) / 255.0
        let red = Double((argb >> 16) & 0xFF) / 255.0
        let green = Double((argb >> 8) & 0xFF) / 255.0
        let blue = Double(argb & 0xFF) / 255.0

        self.init(.sRGB, red: red, green: green, blue: blue, opacity: alpha)
    }
}

private struct ButtonView: View {
    @State private var colors: [Color] = [.red, .blue]
    let onColorsChanged: (([Color]) -> Void)?

    private let availableColors: [Color] = [.red, .green, .blue, .yellow, .orange, .purple]

    var body: some View {
        Button(action: { shuffle() }) {
            Text("Shuffle")
                .font(.body)
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
        }
        .buttonStyle(PillButtonStyle())
    }

    private func shuffle() {
        let randomColors = availableColors.shuffled().prefix(2)
        onColorsChanged?(Array(randomColors))
    }
    
    struct PillButtonStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(
                    Capsule()
                        .fill(Color.blue)
                )
                .scaleEffect(configuration.isPressed ? 0.96 : 1)
                .opacity(configuration.isPressed ? 0.85 : 1)
        }
    }
}

private class ButtonViewController: UIViewController {
    var onColorsChanged: (([Color]) -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()

        let hostingController = UIHostingController(rootView: ButtonView(onColorsChanged: self.onColorsChanged))

        addChild(hostingController)
        view.addSubview(hostingController.view)

        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])

        hostingController.didMove(toParent: self)
    }
}

#Preview {
    GradientScreenMixed()
}
