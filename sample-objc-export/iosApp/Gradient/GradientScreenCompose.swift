import Composables
import SwiftUI

struct GradientScreenCompose: View {
    var body: some View {
        GradientScreenComposeRepresentable()
            .ignoresSafeArea()
    }
}

#Preview {
    GradientScreenCompose()
}
