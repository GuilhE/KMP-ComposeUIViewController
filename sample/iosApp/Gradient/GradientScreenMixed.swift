import Composables
import SwiftUI

struct GradientScreenMixed: View {
    @State private var screenState: ScreenState = .init(startColor: Color.red.asInt64, endColor: Color.blue.asInt64)

    var body: some View {
        GradientScreenMixedRepresentable(state: self.$screenState, controller: self.createViewController())
            .ignoresSafeArea()
    }

    private func createViewController() -> ButtonViewController {
        let controller = ButtonViewController()
        controller.onClick = {
            let colors = getRandomColors()
            self.screenState = ScreenState(startColor: colors[0].asInt64, endColor: colors[1].asInt64)
        }
        return controller
    }
}

private class ButtonViewController: UIViewController {
    var onClick: (() -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()

        let hostingController = UIHostingController(rootView: ButtonView(onClick: self.onClick))
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
    GradientScreenMixed()
}
