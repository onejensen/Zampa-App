import Foundation
import FirebaseMessaging
import UserNotifications
import UIKit

class PushManager: NSObject, UNUserNotificationCenterDelegate, MessagingDelegate {
    static let shared = PushManager()
    
    private override init() {
        super.init()
    }
    
    func registerForNotifications() {
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        let authOptions: UNAuthorizationOptions = [.alert, .badge, .sound]
        UNUserNotificationCenter.current().requestAuthorization(options: authOptions) { granted, error in
            print("Push permission granted: \(granted)")
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    /// Re-register token when user logs in (token may have arrived before auth)
    func refreshTokenIfNeeded() {
        Messaging.messaging().token { token, error in
            guard let token = token else { return }
            Task {
                try? await FirebaseService.shared.registerDeviceToken(token: token)
            }
        }
    }
    
    // MARK: - MessagingDelegate
    
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        print("FCM Token: \(token)")
        
        // Registrar en Firestore
        Task {
            do {
                try await FirebaseService.shared.registerDeviceToken(token: token)
            } catch {
                print("Error registering device token: \(error)")
            }
        }
    }
    
    // MARK: - UNUserNotificationCenterDelegate
    
    /// Recibir notificación en primer plano
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification, withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound, .badge])
    }
    
    /// El usuario tocó una notificación
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse, withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        
        if let menuId = userInfo["menuId"] as? String {
            // Manejar deep link a detalle de menú
            NotificationCenter.default.post(name: .openMenuDetail, object: nil, userInfo: ["menuId": menuId])
        }
        
        completionHandler()
    }
}

extension NSNotification.Name {
    static let openMenuDetail = NSNotification.Name("openMenuDetail")
}
