import Composables
import SwiftUI

struct GradientScreenMixedA: View {
    @State private var screenState: ScreenState = .init(startColor: Color.red.asInt64, endColor: Color.blue.asInt64)

    var body: some View {
        GradientScreenMixedARepresentable(state: $screenState, randomize: { milis in
            let randomColors = getRandomColors()
            screenState = ScreenState(startColor: randomColors.first!.asInt64, endColor: randomColors.last!.asInt64)
            printMilis(milis, "MixedA")
        })
        .ignoresSafeArea()
    }
}

#Preview {
    GradientScreenMixedA()
}
