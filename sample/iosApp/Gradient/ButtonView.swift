import SwiftUI

struct ButtonView: View {
    let onClick: (() -> Void)?

    var body: some View {
        Button(action: {
            onClick?()
            printMilis()
        }) {
            Text("Shuffle")
                .font(.body)
                .foregroundColor(.white)
                .padding(.horizontal, 20)
                .padding(.vertical, 10)
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
