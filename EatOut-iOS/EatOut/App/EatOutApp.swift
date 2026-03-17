import SwiftUI
import FirebaseCore
import FirebaseMessaging

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@main
struct EatOutApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .preferredColorScheme(appState.appColorScheme.colorScheme)
                .onAppear {
                    PushManager.shared.registerForNotifications()
                }
                #if canImport(GoogleSignIn)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
                #endif
        }
    }
}

class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        FirebaseApp.configure()
        URLCache.shared = URLCache(
            memoryCapacity: 100 * 1024 * 1024,
            diskCapacity:   500 * 1024 * 1024,
            diskPath: "eatout_image_cache"
        )
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    #if canImport(GoogleSignIn)
    func application(_ app: UIApplication, open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
    #endif
}





