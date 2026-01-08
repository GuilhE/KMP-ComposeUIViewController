import SwiftUI
import UIKit

struct GradientScreenSwift: View {
    var body: some View {
        GradientScreenSwiftRepresentable(controller: UIHostingController(rootView: GradientView()))
            .ignoresSafeArea()
    }
}

private struct GradientView: View {
    @State private var colors: [Color] = [.red, .blue]

    var body: some View {
        ZStack {
            Rectangle()
                .fill(
                    LinearGradient(
                        gradient: Gradient(colors: colors),
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .animation(.easeInOut(), value: colors)

            ButtonView(onClick: { colors = getRandomColors() })
        }
        .ignoresSafeArea()
    }
}

#Preview {
    GradientScreenSwift()
}
