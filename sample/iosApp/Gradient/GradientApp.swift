import SwiftUI

@main
struct GradientApp: App {
    var body: some Scene {
        WindowGroup {
            TabView {
                GradientScreenCompose()
                    .tabItem {
                        Label("Compose", systemImage: "paintbrush.fill")
                    }

                GradientScreenSwift()
                    .tabItem {
                        Label("Swift", systemImage: "swift")
                    }

                GradientScreenMixed()
                    .tabItem {
                        Label("Mixed", systemImage: "shuffle")
                    }
            }
        }
    }
}
