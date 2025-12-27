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

            ButtonView(onClick: { colors = getRandomColors() })
        }
    }
}

private class GradientViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()

        let hostingController = UIHostingController(rootView: GradientView())
        hostingController.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(hostingController.view)

        NSLayoutConstraint.activate([
            hostingController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostingController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            hostingController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostingController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }
}

#Preview {
    GradientScreenSwift()
}
