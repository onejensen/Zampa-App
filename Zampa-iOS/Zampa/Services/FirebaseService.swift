import Foundation
import UIKit
import FirebaseAuth
import FirebaseFirestore
import FirebaseStorage

/// Servicio centralizado para todas las operaciones con Firebase
class FirebaseService {
    static let shared = FirebaseService()
    
    let db = Firestore.firestore()
    private let storage = Storage.storage()
    
    private init() {}
    
    // MARK: - Authentication
    
    /// Obtiene el usuario actual autenticado leyendo su perfil de Firestore
    var currentFirebaseUser: FirebaseAuth.User? {
        FirebaseAuth.Auth.auth().currentUser
    }
    
    /// Lee el perfil completo del usuario desde Firestore
    func getCurrentUser() async throws -> AppUser? {
        guard let fbUser = currentFirebaseUser else { return nil }

        let doc = try await db.collection("users").document(fbUser.uid).getDocument()
        guard let data = doc.data() else { return nil }

        let roleString = data["role"] as? String ?? "CLIENTE"
        let role = AppUser.UserRole(rawValue: roleString) ?? .cliente

        return AppUser(
            id: fbUser.uid,
            email: data["email"] as? String ?? fbUser.email ?? "",
            name: data["name"] as? String ?? fbUser.displayName ?? "Usuario",
            role: role,
            phone: data["phone"] as? String,
            photoUrl: data["photoUrl"] as? String,
            deletedAt: (data["deletedAt"] as? Timestamp)?.dateValue(),
            scheduledPurgeAt: (data["scheduledPurgeAt"] as? Timestamp)?.dateValue(),
            currencyPreference: data["currencyPreference"] as? String ?? "EUR",
            languagePreference: data["languagePreference"] as? String ?? "auto"
        )
    }

    func uploadProfilePhoto(_ image: UIImage) async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        guard let data = image.jpegData(compressionQuality: 0.85) else { return }
        let timestamp = Int(Date().timeIntervalSince1970 * 1000)
        let url = try await uploadImage(data: data, path: "users/\(uid)/profile_\(timestamp).jpg")
        try await db.collection("users").document(uid).updateData(["photoUrl": url])
    }
    
    /// Propiedad síncrona simple para acceso rápido (usa datos de Auth cache)
    var currentUser: AppUser? {
        guard let fbUser = currentFirebaseUser else { return nil }
        return AppUser(
            id: fbUser.uid,
            email: fbUser.email ?? "",
            name: fbUser.displayName ?? "Usuario",
            role: .cliente, // Se actualiza async al cargar perfil
            phone: nil
        )
    }
    
    /// Registra un nuevo usuario en Firebase Auth + Firestore
    func register(email: String, password: String, name: String, role: AppUser.UserRole, phone: String? = nil) async throws -> AppUser {
        let result = try await FirebaseAuth.Auth.auth().createUser(withEmail: email, password: password)
        let fbUser = result.user
        
        // Actualizar displayName en Auth
        let changeRequest = fbUser.createProfileChangeRequest()
        changeRequest.displayName = name
        try await changeRequest.commitChanges()
        
        // Guardar perfil en Firestore
        var userData: [String: Any] = [
            "id": fbUser.uid,
            "email": email,
            "name": name,
            "role": role.rawValue,
            "createdAt": FieldValue.serverTimestamp()
        ]
        if let phone = phone, !phone.isEmpty {
            userData["phone"] = phone
        }
        
        try await db.collection("users").document(fbUser.uid).setData(userData)
        
        // Si es comercio, crear documento base en businesses/
        if role == .comercio {
            // Trial de 90 días desde la creación (en ms epoch para ser idéntico a Android).
            let trialEndMs = Int64(Date().addingTimeInterval(90 * 24 * 60 * 60).timeIntervalSince1970 * 1000)
            try await db.collection("businesses").document(fbUser.uid).setData([
                "id": fbUser.uid,
                "userId": fbUser.uid,
                "name": name,
                "acceptsReservations": false,
                "planTier": "free",
                "isHighlighted": false,
                "subscriptionStatus": SubscriptionStatus.trial.rawValue,
                "trialEndsAt": trialEndMs,
                "createdAt": FieldValue.serverTimestamp()
            ])
        }
        
        // Si es cliente, crear documento base en customers/
        if role == .cliente {
            try await db.collection("customers").document(fbUser.uid).setData([
                "id": fbUser.uid,
                "userId": fbUser.uid,
                "displayName": name,
                "createdAt": FieldValue.serverTimestamp(),
                "updatedAt": FieldValue.serverTimestamp()
            ])
        }
        
        return AppUser(
            id: fbUser.uid,
            email: email,
            name: name,
            role: role,
            phone: phone
        )
    }
    
    /// Inicia sesión y lee perfil de Firestore
    func login(email: String, password: String) async throws -> AppUser {
        let result = try await FirebaseAuth.Auth.auth().signIn(withEmail: email, password: password)
        let fbUser = result.user
        
        let doc = try await db.collection("users").document(fbUser.uid).getDocument()
        let data = doc.data() ?? [:]
        let roleString = data["role"] as? String ?? "CLIENTE"
        let role = AppUser.UserRole(rawValue: roleString) ?? .cliente
        let name = data["name"] as? String ?? fbUser.displayName ?? "Usuario"
        
        return AppUser(
            id: fbUser.uid,
            email: fbUser.email ?? "",
            name: name,
            role: role,
            phone: data["phone"] as? String,
            photoUrl: data["photoUrl"] as? String
        )
    }

    /// Cierra sesión
    func logout() throws {
        try FirebaseAuth.Auth.auth().signOut()
    }

    var isAuthenticated: Bool {
        currentFirebaseUser != nil
    }

    // MARK: - Social Auth

    /// Inicia sesión con una credencial social (Apple / Google).
    /// - Returns: el usuario y si es la primera vez que se autentica.
    func loginWithSocialCredential(
        _ credential: AuthCredential,
        name: String? = nil,
        email: String? = nil
    ) async throws -> (user: AppUser, isNewUser: Bool) {
        let result = try await FirebaseAuth.Auth.auth().signIn(with: credential)
        let fbUser = result.user

        let doc = try await db.collection("users").document(fbUser.uid).getDocument()

        if doc.exists, let data = doc.data() {
            // Usuario existente — leer perfil
            let roleString = data["role"] as? String ?? "CLIENTE"
            let role = AppUser.UserRole(rawValue: roleString) ?? .cliente
            let storedName = data["name"] as? String ?? fbUser.displayName ?? name ?? "Usuario"
            let storedEmail = data["email"] as? String ?? fbUser.email ?? email ?? ""
            let user = AppUser(id: fbUser.uid, email: storedEmail, name: storedName,
                                   role: role, phone: data["phone"] as? String,
                                   photoUrl: data["photoUrl"] as? String)
            return (user, false)
        } else {
            // Usuario nuevo — crear documento base (rol pendiente de elegir)
            let userName  = name  ?? fbUser.displayName ?? "Usuario"
            let userEmail = email ?? fbUser.email ?? ""

            if (fbUser.displayName ?? "").isEmpty, let n = name {
                let req = fbUser.createProfileChangeRequest()
                req.displayName = n
                try? await req.commitChanges()
            }

            try await db.collection("users").document(fbUser.uid).setData([
                "id": fbUser.uid,
                "email": userEmail,
                "name": userName,
                "role": AppUser.UserRole.cliente.rawValue, // provisional
                "createdAt": FieldValue.serverTimestamp()
            ])

            let user = AppUser(id: fbUser.uid, email: userEmail, name: userName,
                                   role: .cliente, phone: nil)
            return (user, true)
        }
    }

    /// Finaliza el registro social actualizando el rol elegido y creando
    /// el documento de businesses/customers si corresponde.
    func finalizeSocialRegistration(
        userId: String,
        role: AppUser.UserRole,
        name: String,
        email: String
    ) async throws -> AppUser {
        try await db.collection("users").document(userId).updateData([
            "role": role.rawValue,
            "name": name
        ])

        if role == .comercio {
            let biz = try await db.collection("businesses").document(userId).getDocument()
            if !biz.exists {
                let trialEndMs = Int64(Date().addingTimeInterval(90 * 24 * 60 * 60).timeIntervalSince1970 * 1000)
                try await db.collection("businesses").document(userId).setData([
                    "id": userId, "userId": userId, "name": name,
                    "acceptsReservations": false, "planTier": "free",
                    "isHighlighted": false,
                    "subscriptionStatus": SubscriptionStatus.trial.rawValue,
                    "trialEndsAt": trialEndMs,
                    "createdAt": FieldValue.serverTimestamp()
                ])
            }
        } else {
            let cust = try await db.collection("customers").document(userId).getDocument()
            if !cust.exists {
                try await db.collection("customers").document(userId).setData([
                    "id": userId, "userId": userId, "displayName": name,
                    "createdAt": FieldValue.serverTimestamp(),
                    "updatedAt": FieldValue.serverTimestamp()
                ])
            }
        }

        return AppUser(id: userId, email: email, name: name, role: role, phone: nil)
    }
    
    // MARK: - Business Profile
    
    /// Obtiene el perfil de comercio
    func getMerchantProfile(merchantId: String) async throws -> Merchant? {
        let doc = try await db.collection("businesses").document(merchantId).getDocument()
        guard let data = doc.data() else { return nil }
        return parseMerchant(data: data, id: doc.documentID)
    }
    
    /// Actualiza el nombre visible del usuario en Firestore
    func updateUserName(_ name: String) async throws {
        guard let uid = currentFirebaseUser?.uid else { return }
        try await db.collection("users").document(uid).updateData(["name": name])
    }

    // MARK: - Account deletion (soft delete + 30-day grace period)

    /// Marca la cuenta del usuario actual como pendiente de eliminación.
    /// Setea `deletedAt` y `scheduledPurgeAt` en `users/{uid}` y borra todos los
    /// documentos de `deviceTokens` del usuario para cortar notificaciones push
    /// inmediatamente. NO borra el Auth user ni datos de usuario: eso lo hace
    /// la Cloud Function `purgeDeletedAccounts` pasados los 30 días.
    func requestAccountDeletion() async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }

        // 1. Borrar device tokens del usuario (query + delete por cada doc)
        let tokensSnap = try await db.collection("deviceTokens")
            .whereField("userId", isEqualTo: uid)
            .getDocuments()
        for doc in tokensSnap.documents {
            try await doc.reference.delete()
        }

        // 2. Marcar cuenta como pendiente de eliminación
        let now = Date()
        let purge = now.addingTimeInterval(30 * 24 * 60 * 60)
        try await db.collection("users").document(uid).updateData([
            "deletedAt": Timestamp(date: now),
            "scheduledPurgeAt": Timestamp(date: purge),
        ])
    }

    /// Cancela una eliminación pendiente limpiando `deletedAt` y `scheduledPurgeAt`.
    /// Sólo tiene sentido llamarla durante el periodo de gracia.
    func cancelAccountDeletion() async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        try await db.collection("users").document(uid).updateData([
            "deletedAt": FieldValue.delete(),
            "scheduledPurgeAt": FieldValue.delete(),
        ])
    }

    // MARK: - Currency preference

    /// Actualiza la moneda preferida del usuario en Firestore.
    /// El caller debe refrescar `appState.currentUser` tras el éxito para
    /// que la UI observe el cambio.
    func updateCurrencyPreference(_ code: String) async throws {
        guard let uid = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        let supported = ["EUR", "USD", "GBP", "JPY", "CHF", "SEK", "NOK", "DKK", "CAD", "AUD"]
        guard supported.contains(code) else {
            throw NSError(
                domain: "FirebaseService",
                code: -1,
                userInfo: [NSLocalizedDescriptionKey: "Código de moneda no soportado: \(code)"]
            )
        }
        try await db.collection("users").document(uid).updateData([
            "currencyPreference": code
        ])
    }

    /// Actualiza el perfil de comercio
    func updateMerchantProfile(_ merchant: Merchant) async throws {
        var data: [String: Any] = [
            "name": merchant.name,
            "acceptsReservations": merchant.acceptsReservations,
            "updatedAt": FieldValue.serverTimestamp()
        ]
        
        if let phone = merchant.phone { data["phone"] = phone }
        if let desc = merchant.shortDescription { data["shortDescription"] = desc }
        if let cuisineTypes = merchant.cuisineTypes { data["cuisineTypes"] = cuisineTypes }
        if let coverUrl = merchant.coverPhotoUrl { data["coverPhotoUrl"] = coverUrl }
        if let profileUrl = merchant.profilePhotoUrl { data["profilePhotoUrl"] = profileUrl }
        if let plan = merchant.planTier { data["planTier"] = plan }
        if let addressText = merchant.addressText { data["addressText"] = addressText }
        if let isHighlighted = merchant.isHighlighted { data["isHighlighted"] = isHighlighted }
        if let taxId = merchant.taxId?.trimmingCharacters(in: .whitespaces), !taxId.isEmpty {
            data["taxId"] = taxId.uppercased()
        }
        
        if let address = merchant.address {
            data["address"] = [
                "formatted": address.formatted,
                "lat": address.lat,
                "lng": address.lng,
                "placeId": address.placeId ?? ""
            ]
        }
        
        if let schedule = merchant.schedule {
            data["schedule"] = schedule.map { entry in
                ["day": entry.day, "open": entry.open, "close": entry.close]
            }
        }
        
        try await db.collection("businesses").document(merchant.id).setData(data, merge: true)
    }
    
    /// Verifica si el comercio ha completado su perfil (tiene dirección)
    /// Devuelve el timestamp (ms epoch) hasta el cual la app es gratis por
    /// promo global (config/promo.freeUntil). Nil si no hay promo configurada.
    /// El admin controla esto desde Firebase Console.
    func getPromoFreeUntilMs() async throws -> Int64? {
        let doc = try await db.collection("config").document("promo").getDocument()
        guard let data = doc.data() else { return nil }
        if let v = data["freeUntil"] as? Int64 { return v }
        if let v = data["freeUntil"] as? Int { return Int64(v) }
        if let v = data["freeUntil"] as? Double { return Int64(v) }
        return nil
    }

    func isMerchantProfileComplete(merchantId: String) async throws -> Bool {
        let doc = try await db.collection("businesses").document(merchantId).getDocument()
        guard let data = doc.data() else { return false }
        let taxId = (data["taxId"] as? String)?.trimmingCharacters(in: .whitespaces) ?? ""
        return data["address"] != nil
            && data["phone"] != nil
            && !taxId.isEmpty
    }
    
    // MARK: - Daily Offers
    
    /// Obtiene ofertas diarias para el feed público
    func getActiveMenus(
        limit: Int = 20,
        lastDocument: DocumentSnapshot? = nil,
        cuisineFilter: String? = nil,
        maxPrice: Double? = nil
    ) async throws -> (menus: [Menu], lastDoc: DocumentSnapshot?) {
        var query: Query = db.collection("dailyOffers")
            .whereField("isActive", isEqualTo: true)
        
        if let cuisine = cuisineFilter {
            query = query.whereField("tags", arrayContains: cuisine)
        }
        
        if let price = maxPrice {
            query = query.whereField("priceTotal", isLessThanOrEqualTo: price)
        }
        
        query = query.order(by: "createdAt", descending: true)
            .limit(to: limit)
        
        if let lastDoc = lastDocument {
            query = query.start(afterDocument: lastDoc)
        }
        
        let snapshot = try await query.getDocuments()
        let menus = snapshot.documents.compactMap { parseMenu(doc: $0) }
        // Filtrar menús expirados (>24h) excepto permanentes
        let activeMenus = menus.filter { $0.isToday }
        return (activeMenus, snapshot.documents.last)
    }
    
    /// Obtiene una oferta por su ID
    func getMenuById(menuId: String) async throws -> Menu? {
        let doc = try await db.collection("dailyOffers").document(menuId).getDocument()
        guard doc.exists, let data = doc.data() else { return nil }
        let id = doc.documentID
        let businessId = data["businessId"] as? String ?? ""
        let date = data["date"] as? String ?? ""
        let title = data["title"] as? String ?? ""
        let description = data["description"] as? String ?? ""
        let priceTotal = data["priceTotal"] as? Double ?? 0
        let currency = data["currency"] as? String ?? "EUR"
        let photoUrls = data["photoUrls"] as? [String] ?? []
        let tags = data["tags"] as? [String] ?? []
        let createdAt: String = {
            if let ts = data["createdAt"] as? Timestamp {
                return ISO8601DateFormatter().string(from: ts.dateValue())
            }
            return data["createdAt"] as? String ?? ""
        }()
        let updatedAt: String = {
            if let ts = data["updatedAt"] as? Timestamp {
                return ISO8601DateFormatter().string(from: ts.dateValue())
            }
            return data["updatedAt"] as? String ?? ""
        }()
        let isActive = data["isActive"] as? Bool ?? true
        let isMerchantPro = data["isMerchantPro"] as? Bool ?? false
        let dietaryInfo = DietaryInfo.from(data["dietaryInfo"] as? [String: Any] ?? [:])
        return Menu(id: id, businessId: businessId, date: date, title: title, description: description, priceTotal: priceTotal, currency: currency, photoUrls: photoUrls, tags: tags, createdAt: createdAt, updatedAt: updatedAt, isActive: isActive, isMerchantPro: isMerchantPro, dietaryInfo: dietaryInfo, offerType: data["offerType"] as? String, includesDrink: data["includesDrink"] as? Bool ?? false, includesDessert: data["includesDessert"] as? Bool ?? false, includesCoffee: data["includesCoffee"] as? Bool ?? false, serviceTime: data["serviceTime"] as? String ?? "both", isPermanent: data["isPermanent"] as? Bool ?? false)
    }
    
    /// Obtiene ofertas de un comercio específico
    func getMenusByMerchant(merchantId: String) async throws -> [Menu] {
        let snapshot = try await db.collection("dailyOffers")
            .whereField("businessId", isEqualTo: merchantId)
            .order(by: "createdAt", descending: true)
            .getDocuments()
        
        return snapshot.documents.compactMap { parseMenu(doc: $0) }
    }
    
    /// Crea una nueva oferta diaria
    func createMenu(title: String, description: String, price: Double, currency: String = "EUR", photoData: Data, tags: [String]? = nil, dietaryInfo: DietaryInfo = DietaryInfo(), offerType: String? = nil, includesDrink: Bool = false, includesDessert: Bool = false, includesCoffee: Bool = false, serviceTime: String = "both", isPermanent: Bool = false) async throws -> Menu {
        guard let businessId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }

        // SECURITY: derivar isMerchantPro del documento del comercio en el servidor.
        // Nunca confiar en un flag pasado desde la UI / AppState (podría manipularse).
        // Las Firestore rules además validan que el valor enviado coincida con el server-side.
        let businessDoc = try await db.collection("businesses").document(businessId).getDocument()
        let planTier = (businessDoc.data()?["planTier"] as? String) ?? "free"
        let isPro = (planTier == "pro")

        // Guard client-side: suscripción vigente (trial o pago) o promo global.
        // Las rules bloquean server-side de todas formas, pero esto evita subir la foto
        // y permite mostrar un mensaje claro.
        let status = (businessDoc.data()?["subscriptionStatus"] as? String) ?? SubscriptionStatus.trial.rawValue
        let trialEnd = (businessDoc.data()?["trialEndsAt"] as? Int64)
            ?? Int64(businessDoc.data()?["trialEndsAt"] as? Int ?? 0)
        let activeUntil = (businessDoc.data()?["subscriptionActiveUntil"] as? Int64)
            ?? Int64(businessDoc.data()?["subscriptionActiveUntil"] as? Int ?? 0)
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let promoActive = (try? await getPromoFreeUntilMs()).map { $0 > nowMs } ?? false
        let canPublish: Bool = {
            if promoActive { return true }
            switch status {
            case SubscriptionStatus.trial.rawValue: return trialEnd > nowMs
            case SubscriptionStatus.active.rawValue: return activeUntil > nowMs
            default: return false
            }
        }()
        guard canPublish else { throw FirebaseServiceError.subscriptionExpired }

        // Upload image
        let imagePath = "dailyOffers/\(UUID().uuidString).jpg"
        let photoUrl = try await uploadImage(data: photoData, path: imagePath)

        let id = UUID().uuidString
        let now = Date()
        let formatter = ISO8601DateFormatter()
        let createdAtStr = formatter.string(from: now)

        let menuData: [String: Any] = [
            "id": id,
            "businessId": businessId,
            "date": formatter.string(from: now),
            "title": title,
            "description": description,
            "priceTotal": price,
            "currency": currency,
            "photoUrls": [photoUrl],
            "tags": tags ?? [],
            "createdAt": createdAtStr,
            "updatedAt": createdAtStr,
            "isActive": true,
            "isMerchantPro": isPro,
            "dietaryInfo": dietaryInfo.firestoreMap,
            "offerType": offerType as Any,
            "includesDrink": includesDrink,
            "includesDessert": includesDessert,
            "includesCoffee": includesCoffee,
            "serviceTime": serviceTime,
            "isPermanent": isPermanent
        ]

        try await db.collection("dailyOffers").document(id).setData(menuData)

        return Menu(
            id: id,
            businessId: businessId,
            date: formatter.string(from: now),
            title: title,
            description: description,
            priceTotal: price,
            currency: currency,
            photoUrls: [photoUrl],
            tags: tags,
            createdAt: createdAtStr,
            updatedAt: createdAtStr,
            isActive: true,
            isMerchantPro: isPro,
            dietaryInfo: dietaryInfo,
            offerType: offerType,
            includesDrink: includesDrink,
            includesDessert: includesDessert,
            includesCoffee: includesCoffee,
            serviceTime: serviceTime,
            isPermanent: isPermanent
        )
    }
    
    /// Actualiza una oferta existente
    func updateMenu(menuId: String, data: [String: Any]) async throws {
        var updatedData = data
        updatedData["updatedAt"] = FieldValue.serverTimestamp()
        try await db.collection("dailyOffers").document(menuId).updateData(updatedData)
    }
    
    /// Elimina una oferta
    func deleteMenu(menuId: String) async throws {
        try await db.collection("dailyOffers").document(menuId).delete()
    }
    
    // MARK: - Favorites
    
    /// Añade un comercio a favoritos
    func addFavorite(merchantId: String) async throws -> Favorite {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let fav = Favorite(customerId: userId, businessId: merchantId)
        let data: [String: Any] = [
            "id": fav.id,
            "customerId": fav.customerId,
            "businessId": fav.businessId,
            "createdAt": fav.createdAt,
            "notificationsEnabled": fav.notificationsEnabled
        ]
        try await db.collection("favorites").document(fav.id).setData(data)
        return fav
    }
    
    /// Elimina un favorito
    func removeFavorite(merchantId: String) async throws {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let snapshot = try await db.collection("favorites")
            .whereField("customerId", isEqualTo: userId)
            .whereField("businessId", isEqualTo: merchantId)
            .getDocuments()
        
        for doc in snapshot.documents {
            try await doc.reference.delete()
        }
    }
    
    /// Alterna el estado de favorito de un comercio
    func toggleFavorite(merchantId: String) async throws -> Bool {
        let isFav = try await isFavorite(merchantId: merchantId)
        if isFav {
            try await removeFavorite(merchantId: merchantId)
            return false
        } else {
            _ = try await addFavorite(merchantId: merchantId)
            return true
        }
    }
    
    /// Obtiene favoritos del usuario actual
    func getFavorites() async throws -> [Favorite] {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let snapshot = try await db.collection("favorites")
            .whereField("customerId", isEqualTo: userId)
            .getDocuments()

        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let businessId = data["businessId"] as? String else { return nil }
            return Favorite(
                id: doc.documentID,
                customerId: userId,
                businessId: businessId,
                createdAt: data["createdAt"] as? String ?? "",
                notificationsEnabled: data["notificationsEnabled"] as? Bool ?? true
            )
        }
        .sorted { $0.createdAt > $1.createdAt }
    }
    
    /// Verifica si un comercio es favorito del usuario actual
    func isFavorite(merchantId: String) async throws -> Bool {
        guard let userId = currentFirebaseUser?.uid else { return false }
        
        let snapshot = try await db.collection("favorites")
            .whereField("customerId", isEqualTo: userId)
            .whereField("businessId", isEqualTo: merchantId)
            .limit(to: 1)
            .getDocuments()
        
        return !snapshot.documents.isEmpty
    }
    
    /// Actualiza notificaciones de un favorito
    func toggleFavoriteNotifications(merchantId: String, enabled: Bool) async throws {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let snapshot = try await db.collection("favorites")
            .whereField("customerId", isEqualTo: userId)
            .whereField("businessId", isEqualTo: merchantId)
            .limit(to: 1)
            .getDocuments()
        
        if let doc = snapshot.documents.first {
            try await doc.reference.updateData(["notificationsEnabled": enabled])
        }
    }
    
    // MARK: - Metrics / Tracking
    
    /// Registra una acción (call, directions, share) para estadísticas
    func trackAction(menuId: String, merchantId: String, action: String) async {
        let dateStr = {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            return formatter.string(from: Date())
        }()
        
        let docRef = db.collection("metrics").document(merchantId)
            .collection("daily").document(dateStr)
        
        do {
            _ = try await db.runTransaction { transaction, errorPointer in
                let doc: DocumentSnapshot
                do {
                    doc = try transaction.getDocument(docRef)
                } catch {
                    errorPointer?.pointee = error as NSError
                    return nil
                }
                
                if doc.exists {
                    let clicks = doc.data()?["clicks"] as? [String: Int] ?? [:]
                    let currentCount = clicks[action] ?? 0
                    transaction.updateData([
                        "clicks.\(action)": currentCount + 1
                    ], forDocument: docRef)
                } else {
                    transaction.setData([
                        "impressions": 0,
                        "favorites": 0,
                        "clicks": [action: 1]
                    ], forDocument: docRef)
                }
                return nil
            }
        } catch {
            // Silently fail tracking — no debe bloquear la acción del usuario
            print("Error tracking action: \(error)")
        }
    }
    
    /// Registra una impresión de oferta
    func trackImpression(merchantId: String) async {
        let dateStr = {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            return formatter.string(from: Date())
        }()
        
        let docRef = db.collection("metrics").document(merchantId)
            .collection("daily").document(dateStr)
        
        try? await docRef.setData(["impressions": FieldValue.increment(Int64(1))], merge: true)
    }
    
    /// Obtiene estadísticas de un comercio
    func getMerchantStats(merchantId: String, days: Int = 7) async throws -> [[String: Any]] {
        let calendar = Calendar.current
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        
        var results: [[String: Any]] = []
        
        for i in 0..<days {
            let date = calendar.date(byAdding: .day, value: -i, to: Date())!
            let dateStr = formatter.string(from: date)
            
            let doc = try await db.collection("metrics").document(merchantId)
                .collection("daily").document(dateStr).getDocument()
            
            if let data = doc.data() {
                var entry = data
                entry["date"] = dateStr
                results.append(entry)
            }
        }
        
        return results
    }
    
    // MARK: - Storage
    
    /// Sube una imagen a Firebase Storage y devuelve la URL de descarga
    func uploadImage(data: Data, path: String) async throws -> String {
        let storageRef = storage.reference().child(path)
        let metadata = StorageMetadata()
        metadata.contentType = "image/jpeg"
        
        _ = try await storageRef.putDataAsync(data, metadata: metadata)
        let url = try await storageRef.downloadURL()
        return url.absoluteString
    }
    
    // MARK: - Device Tokens
    
    /// Registra un device token para push notifications
    func registerDeviceToken(token: String, platform: String = "IOS") async throws {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let tokenId = "\(userId)_\(platform)"
        try await db.collection("deviceTokens").document(tokenId).setData([
            "userId": userId,
            "token": token,
            "platform": platform,
            "language": Locale.current.language.languageCode?.identifier ?? "es",
            "createdAt": FieldValue.serverTimestamp(),
            "lastUsedAt": FieldValue.serverTimestamp()
        ])
    }
    
    // MARK: - Cuisine Types
    
    /// Obtiene el catálogo de tipos de cocina desde Firestore
    func fetchCuisineTypes() async throws -> [CuisineType] {
        let snapshot = try await db.collection("cuisineTypes")
            .order(by: "name")
            .getDocuments()
        
        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let name = data["name"] as? String else { return nil }
            return CuisineType(id: doc.documentID, name: name)
        }
    }
    
    // MARK: - Subscriptions
    
    /// Crea una suscripción para un comercio
    func createSubscription(businessId: String, type: Subscription.SubscriptionType = .monthly) async throws -> Subscription {
        let id = UUID().uuidString
        let now = ISO8601DateFormatter().string(from: Date())
        
        // Calcular fecha de fin (1 mes o 1 año)
        let calendar = Calendar.current
        let endDate: Date
        switch type {
        case .monthly:
            endDate = calendar.date(byAdding: .month, value: 1, to: Date()) ?? Date()
        case .yearly:
            endDate = calendar.date(byAdding: .year, value: 1, to: Date()) ?? Date()
        }
        let endDateStr = ISO8601DateFormatter().string(from: endDate)
        
        let data: [String: Any] = [
            "id": id,
            "businessId": businessId,
            "type": type.rawValue,
            "status": Subscription.SubscriptionStatus.active.rawValue,
            "startDate": now,
            "endDate": endDateStr,
            "createdAt": FieldValue.serverTimestamp(),
            "updatedAt": FieldValue.serverTimestamp()
        ]
        
        try await db.collection("subscriptions").document(id).setData(data)
        
        return Subscription(
            id: id,
            businessId: businessId,
            type: type,
            status: .active,
            startDate: now,
            endDate: endDateStr
        )
    }
    
    /// Obtiene la suscripción activa de un comercio
    func getActiveSubscription(businessId: String) async throws -> Subscription? {
        let snapshot = try await db.collection("subscriptions")
            .whereField("businessId", isEqualTo: businessId)
            .whereField("status", isEqualTo: "ACTIVE")
            .limit(to: 1)
            .getDocuments()
        
        guard let doc = snapshot.documents.first else { return nil }
        let data = doc.data()
        
        return Subscription(
            id: doc.documentID,
            businessId: data["businessId"] as? String ?? "",
            type: Subscription.SubscriptionType(rawValue: data["type"] as? String ?? "MONTHLY") ?? .monthly,
            status: .active,
            startDate: data["startDate"] as? String ?? "",
            endDate: data["endDate"] as? String ?? ""
        )
    }
    
    // MARK: - Notifications
    
    /// Obtiene las notificaciones del usuario actual
    func getNotifications() async throws -> [AppNotification] {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let snapshot = try await db.collection("notifications")
            .whereField("userId", isEqualTo: userId)
            .order(by: "createdAt", descending: true)
            .limit(to: 50)
            .getDocuments()
        
        return snapshot.documents.compactMap { doc in
            let data = doc.data()
            guard let title = data["title"] as? String,
                  let body = data["body"] as? String else { return nil }
            
            return AppNotification(
                id: doc.documentID,
                userId: userId,
                businessId: data["businessId"] as? String,
                offerId: data["offerId"] as? String,
                type: data["type"] as? String ?? "",
                title: title,
                body: body,
                read: data["read"] as? Bool ?? false,
                createdAt: data["createdAt"] as? String ?? ""
            )
        }
    }
    
    /// Marca una notificación como leída
    func markNotificationRead(notificationId: String) async throws {
        try await db.collection("notifications").document(notificationId).updateData([
            "read": true
        ])
    }
    
    /// Obtiene el contador de notificaciones sin leer
    func getUnreadNotificationCount() async throws -> Int {
        guard let userId = currentFirebaseUser?.uid else { return 0 }
        
        let snapshot = try await db.collection("notifications")
            .whereField("userId", isEqualTo: userId)
            .whereField("read", isEqualTo: false)
            .getDocuments()
        
        return snapshot.documents.count
    }
    
    // MARK: - Notification Preferences

    func getNotificationPreferences() async throws -> NotificationPreferences {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        let doc = try await db.collection("users").document(userId).getDocument()
        guard let data = doc.data(),
              let prefs = data["notificationPreferences"] as? [String: Any] else {
            return NotificationPreferences()
        }
        return NotificationPreferences(
            newMenuFromFavorites: prefs["newMenuFromFavorites"] as? Bool ?? true,
            promotions: prefs["promotions"] as? Bool ?? true,
            general: prefs["general"] as? Bool ?? true
        )
    }

    func updateNotificationPreferences(_ prefs: NotificationPreferences) async throws {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        try await db.collection("users").document(userId).updateData([
            "notificationPreferences": [
                "newMenuFromFavorites": prefs.newMenuFromFavorites,
                "promotions": prefs.promotions,
                "general": prefs.general,
            ]
        ])
    }

    // MARK: - Reports
    
    /// Reporta una oferta
    func reportMenu(menuId: String, reason: String) async throws {
        guard let userId = currentFirebaseUser?.uid else {
            throw FirebaseServiceError.notAuthenticated
        }
        
        let reportId = UUID().uuidString
        try await db.collection("reports").document(reportId).setData([
            "id": reportId,
            "offerId": menuId,
            "reporterId": userId,
            "reason": reason,
            "status": "pending",
            "createdAt": FieldValue.serverTimestamp()
        ])
    }
    
    // MARK: - Private Helpers
    
    private func parseMenu(doc: QueryDocumentSnapshot) -> Menu? {
        let data = doc.data()
        guard let businessId = data["businessId"] as? String,
              let title = data["title"] as? String,
              let priceTotal = data["priceTotal"] as? Double else {
            return nil
        }
        
        let dietaryInfo = DietaryInfo.from(data["dietaryInfo"] as? [String: Any] ?? [:])
        return Menu(
            id: doc.documentID,
            businessId: businessId,
            date: data["date"] as? String ?? "",
            title: title,
            description: data["description"] as? String,
            priceTotal: priceTotal,
            currency: data["currency"] as? String ?? "EUR",
            photoUrls: data["photoUrls"] as? [String] ?? [],
            tags: data["tags"] as? [String],
            createdAt: data["createdAt"] as? String ?? "",
            updatedAt: data["updatedAt"] as? String ?? "",
            isActive: data["isActive"] as? Bool ?? true,
            isMerchantPro: data["isMerchantPro"] as? Bool ?? false,
            dietaryInfo: dietaryInfo,
            offerType: data["offerType"] as? String,
            includesDrink: data["includesDrink"] as? Bool ?? false,
            includesDessert: data["includesDessert"] as? Bool ?? false,
            includesCoffee: data["includesCoffee"] as? Bool ?? false,
            serviceTime: data["serviceTime"] as? String ?? "both",
            isPermanent: data["isPermanent"] as? Bool ?? false
        )
    }

    private func parseMerchant(data: [String: Any], id: String) -> Merchant {
        var address: MerchantAddress? = nil
        if let addrData = data["address"] as? [String: Any],
           let formatted = addrData["formatted"] as? String,
           let lat = addrData["lat"] as? Double,
           let lng = addrData["lng"] as? Double {
            address = MerchantAddress(formatted: formatted, lat: lat, lng: lng, placeId: addrData["placeId"] as? String)
        }
        
        var schedule: [ScheduleEntry]? = nil
        if let schedData = data["schedule"] as? [[String: String]] {
            schedule = schedData.compactMap { entry in
                guard let day = entry["day"], let open = entry["open"], let close = entry["close"] else { return nil }
                return ScheduleEntry(day: day, open: open, close: close)
            }
        }
        
        // Campos de suscripción. `trialEndsAt` / `subscriptionActiveUntil` se guardan
        // como ms epoch (Int64) para ser interoperables con Android.
        let status = (data["subscriptionStatus"] as? String).flatMap(SubscriptionStatus.init(rawValue:))
        let trialEndsAt: Date? = {
            if let ms = data["trialEndsAt"] as? Int64 { return Date(timeIntervalSince1970: Double(ms) / 1000) }
            if let ms = data["trialEndsAt"] as? Int { return Date(timeIntervalSince1970: Double(ms) / 1000) }
            if let ms = data["trialEndsAt"] as? Double { return Date(timeIntervalSince1970: ms / 1000) }
            return nil
        }()
        let activeUntil: Date? = {
            if let ms = data["subscriptionActiveUntil"] as? Int64 { return Date(timeIntervalSince1970: Double(ms) / 1000) }
            if let ms = data["subscriptionActiveUntil"] as? Int { return Date(timeIntervalSince1970: Double(ms) / 1000) }
            if let ms = data["subscriptionActiveUntil"] as? Double { return Date(timeIntervalSince1970: ms / 1000) }
            return nil
        }()

        return Merchant(
            id: id,
            userId: data["userId"] as? String,
            name: data["name"] as? String ?? "",
            phone: data["phone"] as? String,
            address: address,
            addressText: data["addressText"] as? String,
            schedule: schedule,
            cuisineTypes: data["cuisineTypes"] as? [String],
            acceptsReservations: data["acceptsReservations"] as? Bool ?? false,
            shortDescription: data["shortDescription"] as? String,
            coverPhotoUrl: data["coverPhotoUrl"] as? String,
            profilePhotoUrl: data["profilePhotoUrl"] as? String,
            planTier: data["planTier"] as? String,
            isHighlighted: data["isHighlighted"] as? Bool,
            taxId: data["taxId"] as? String,
            subscriptionStatus: status,
            trialEndsAt: trialEndsAt,
            subscriptionActiveUntil: activeUntil
        )
    }

    // MARK: - User History

    func saveUserHistoryEntry(businessId: String, businessName: String, action: String) async {
        guard let userId = currentFirebaseUser?.uid else { return }
        let docId = "\(userId)_\(businessId)_\(action)"
        try? await db.collection("userHistory").document(docId).setData([
            "userId": userId,
            "businessId": businessId,
            "businessName": businessName,
            "action": action,
            "timestamp": FieldValue.serverTimestamp()
        ], merge: true)
    }

    func getUserHistory() async throws -> [[String: Any]] {
        guard let userId = currentFirebaseUser?.uid else { throw FirebaseServiceError.notAuthenticated }
        let snapshot = try await db.collection("userHistory")
            .whereField("userId", isEqualTo: userId)
            .order(by: "timestamp", descending: true)
            .limit(to: 100)
            .getDocuments()
        return snapshot.documents.map { $0.data() }
    }
}

// MARK: - Errors

enum FirebaseServiceError: LocalizedError {
    case notAuthenticated
    case profileNotFound
    case invalidData
    case subscriptionExpired

    var errorDescription: String? {
        switch self {
        case .notAuthenticated:
            return "No has iniciado sesión"
        case .profileNotFound:
            return "Perfil no encontrado"
        case .invalidData:
            return "Datos inválidos"
        case .subscriptionExpired:
            return LocalizationManager.shared.t("subscription_required_error")
        }
    }
}
