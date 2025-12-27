import Composables
import SwiftUI

struct GradientScreenCompose: View {
    @State private var screenState: ScreenState = .init(startColor: Color.red.asInt64, endColor: Color.blue.asInt64)

    var body: some View {
        GradientScreenComposeRepresentable(state: $screenState, randomize: { _ in
            let randomColors = getRandomColors()
            screenState = ScreenState(startColor: randomColors.first!.asInt64, endColor: randomColors.last!.asInt64)
            printMilis()
        })
        .ignoresSafeArea()
    }
}

#Preview {
    GradientScreenCompose()
}
