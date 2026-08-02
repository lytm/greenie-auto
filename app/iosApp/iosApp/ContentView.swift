import SwiftUI
import UIKit
import shared

private let appBackground = Color(red: 18.0 / 255.0, green: 23.0 / 255.0, blue: 43.0 / 255.0)

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
        .background(appBackground)
    }
}

/// Bọc GreenieApp() (Compose Multiplatform) vào UIViewControllerRepresentable
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        // MainViewController được generate từ shared/src/iosMain/kotlin/.../MainViewController.kt
        let controller = MainViewControllerKt.MainViewController()
        controller.view.backgroundColor = UIColor(red: 18.0 / 255.0, green: 23.0 / 255.0, blue: 43.0 / 255.0, alpha: 1.0)
        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
