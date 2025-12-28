import SwiftUI

struct ButtonView: View {
    let onClick: (() -> Void)?

    var body: some View {
        Button(action: {
            onClick?()
            printMilis(nil, "SwiftUI")
        }) {
            Text("Shuffle")
                .font(.system(size: 14))
                .bold()
                .foregroundColor(.white)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
        }
        .buttonStyle(PillButtonStyle())
    }

    private struct PillButtonStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration
                .label
                .background(Capsule().fill(Color.blue))
                .scaleEffect(configuration.isPressed ? 0.96 : 1)
                .opacity(configuration.isPressed ? 0.85 : 1)
        }
    }
}

#Preview {
    ButtonView {}
}
