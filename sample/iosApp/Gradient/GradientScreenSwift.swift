import SwiftUI
import UIKit

struct GradientScreenSwift: View {
    var body: some View {
        GradientScreenSwiftRepresentable(controller: GradientViewController())
            .ignoresSafeArea()
    }
}

private struct GradientView: View {
    @State private var colors: [Color] = [.red, .blue]
    private let availableColors: [Color] = [.red, .green, .blue, .yellow, .orange, .purple]

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
                .ignoresSafeArea()
                .animation(.easeInOut(duration: 0.5), value: colors)
            
            Button(action: { shuffle() }) {
                Text("Shuffle")
                    .font(.body)
                    .foregroundColor(.white)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
            }
            .background(Color.blue)
            .clipShape(Capsule())
            .buttonStyle(.plain)
            
        }
    }

    private func shuffle() {
        let randomColors = availableColors.shuffled().prefix(2)
        colors = Array(randomColors)
    }
}

private class GradientViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()

        let swiftUIView = GradientView()
        let hostingController = UIHostingController(rootView: swiftUIView)

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
    GradientView()
}
