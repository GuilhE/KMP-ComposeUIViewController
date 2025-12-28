import Composables
import SwiftUI

struct GradientScreenMixed: View {
    @State private var screenState: ScreenState = .init(startColor: Color.red.asInt64, endColor: Color.blue.asInt64)

    var body: some View {
        GradientScreenMixedRepresentable(state: $screenState, controller: UIHostingController(rootView: ButtonView(onClick: {
            let colors = getRandomColors()
            screenState = ScreenState(startColor: colors[0].asInt64, endColor: colors[1].asInt64)
        })))
        .ignoresSafeArea()
    }
}

#Preview {
    GradientScreenMixed()
}
