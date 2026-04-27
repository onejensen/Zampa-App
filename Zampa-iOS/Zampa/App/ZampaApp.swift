import SwiftUI
import FirebaseCore
import FirebaseMessaging

#if canImport(GoogleSignIn)
import GoogleSignIn
#endif

@main
struct ZampaApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(appState)
                .tint(.appPrimary)
                .preferredColorScheme(appState.appColorScheme.colorScheme)
                .onAppear {
                    PushManager.shared.registerForNotifications()
                }
                .onOpenURL { url in
                    // Custom scheme: tanto el callback de Google Sign-In como
                    // los deep links propios `zampa://offer/{id}` llegan aquí.
                    #if canImport(GoogleSignIn)
                    if GIDSignIn.sharedInstance.handle(url) { return }
                    #endif
                    if let offerId = DeepLinkRouter.offerId(from: url) {
                        appState.pendingDeepLinkOfferId = offerId
                    }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
                    // Universal Links: el sistema abre la app con la URL https
                    // si el dominio coincide con applinks: en los entitlements.
                    guard let url = activity.webpageURL,
                          let offerId = DeepLinkRouter.offerId(from: url) else { return }
                    appState.pendingDeepLinkOfferId = offerId
                }
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
            diskPath: "zampa_image_cache"
        )
        return true
    }

    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        print("APNS token received (\(deviceToken.count) bytes)")
        Messaging.messaging().apnsToken = deviceToken
        // APNS token is now available — retrieve and register the FCM token
        PushManager.shared.refreshTokenIfNeeded()
    }

    func application(_ application: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        print("APNS registration failed: \(error)")
    }

    #if canImport(GoogleSignIn)
    func application(_ app: UIApplication, open url: URL,
                     options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
        GIDSignIn.sharedInstance.handle(url)
    }
    #endif
}





