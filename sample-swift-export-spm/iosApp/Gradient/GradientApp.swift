import SwiftUI

@main
struct GradientApp: App {
    var body: some Scene {
        WindowGroup {
            GradientAppView()
        }
    }
}

private struct GradientAppView : View {
    var body: some View {
        TabView {
            GradientScreenCompose()
                .tabItem {
                    Label("Compose", systemImage: "paintbrush.fill")
                }
            
            GradientScreenMixedA()
                .tabItem {
                    Label("Mixed A", systemImage: "shuffle")
                }
            
            GradientScreenMixedB()
                .tabItem {
                    Label("Mixed B", systemImage: "shuffle")
                }
            
            GradientScreenSwift()
                .tabItem {
                    Label("Swift", systemImage: "swift")
                }
        }
    }
}

#Preview {
    GradientAppView()
}
