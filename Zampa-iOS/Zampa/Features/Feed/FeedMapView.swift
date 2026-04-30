import SwiftUI
import MapKit
import CoreLocation

/// Vista de mapa para el feed: pines por restaurante con al menos una oferta,
/// sheet de preview al tocar y CTA para abrir el detalle.
struct FeedMapView: View {
    let menus: [Menu]
    let merchantMap: [String: Merchant]
    let userLocation: CLLocation?
    let onNavigateToDetail: (String) -> Void
    var onNavigateToMerchant: ((String) -> Void)? = nil

    @ObservedObject var localization = LocalizationManager.shared
    @State private var cameraPosition: MapCameraPosition = .automatic
    @State private var selectedPin: MerchantPin?

    /// Agrupa ofertas por merchant. Un merchant sin address se excluye.
    private var pins: [MerchantPin] {
        let byMerchant = Dictionary(grouping: menus, by: { $0.businessId })
        return byMerchant.compactMap { (businessId, offers) -> MerchantPin? in
            guard let merchant = merchantMap[businessId],
                  let addr = merchant.address,
                  !(addr.lat == 0 && addr.lng == 0) else { return nil }
            return MerchantPin(
                id: businessId,
                merchant: merchant,
                offers: offers,
                coordinate: CLLocationCoordinate2D(latitude: addr.lat, longitude: addr.lng)
            )
        }
    }

    var body: some View {
        ZStack {
            Map(position: $cameraPosition, selection: $selectedPin) {
                ForEach(pins) { pin in
                    Annotation(pin.merchant.name, coordinate: pin.coordinate) {
                        BrandMarker()
                    }
                    .tag(pin)
                }
                if userLocation != nil {
                    UserAnnotation()
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .mapControls {
                MapUserLocationButton()
                MapCompass()
            }
            .onAppear {
                fitCamera()
            }
            .onChange(of: pins.count) { _, _ in fitCamera() }

            if pins.isEmpty {
                emptyOverlay
            }
        }
        .sheet(item: $selectedPin) { pin in
            MerchantPinSheet(
                pin: pin,
                userLocation: userLocation,
                onClose: { selectedPin = nil },
                onViewOffer: { offerId in
                    selectedPin = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        onNavigateToDetail(offerId)
                    }
                },
                onViewRestaurant: { merchantId in
                    // Cerrar el sheet primero y presentar el otro con un pequeño delay:
                    // SwiftUI no puede encadenar dismiss→present en el mismo frame.
                    selectedPin = nil
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
                        onNavigateToMerchant?(merchantId)
                    }
                }
            )
            .presentationDetents([.fraction(0.45), .medium])
            .presentationDragIndicator(.visible)
        }
    }

    private var emptyOverlay: some View {
        VStack(spacing: 12) {
            Image(systemName: "mappin.slash")
                .font(.custom("Sora-Regular", size: 40))
                .foregroundColor(.appTextSecondary)
            Text(localization.t("map_no_locations"))
                .font(.appBody)
                .foregroundColor(.appTextPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.appSurface.opacity(0.92))
    }

    /// Encuadra la cámara para incluir todos los pines (+ usuario si aplica).
    private func fitCamera() {
        guard !pins.isEmpty else { return }
        if pins.count == 1, let only = pins.first {
            cameraPosition = .region(
                MKCoordinateRegion(
                    center: only.coordinate,
                    span: MKCoordinateSpan(latitudeDelta: 0.01, longitudeDelta: 0.01)
                )
            )
            return
        }
        let coords = pins.map(\.coordinate) + (userLocation.map { [$0.coordinate] } ?? [])
        let lats = coords.map(\.latitude)
        let lngs = coords.map(\.longitude)
        let center = CLLocationCoordinate2D(
            latitude: (lats.min()! + lats.max()!) / 2,
            longitude: (lngs.min()! + lngs.max()!) / 2
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max((lats.max()! - lats.min()!) * 1.4, 0.02),
            longitudeDelta: max((lngs.max()! - lngs.min()!) * 1.4, 0.02)
        )
        cameraPosition = .region(MKCoordinateRegion(center: center, span: span))
    }
}

/// Datos de un pin: merchant + sus ofertas activas.
struct MerchantPin: Identifiable, Hashable {
    let id: String
    let merchant: Merchant
    let offers: [Menu]
    let coordinate: CLLocationCoordinate2D

    static func == (lhs: MerchantPin, rhs: MerchantPin) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

/// Sheet que aparece al seleccionar un pin: foto + título + precio + CTA.
private struct MerchantPinSheet: View {
    let pin: MerchantPin
    let userLocation: CLLocation?
    let onClose: () -> Void
    let onViewOffer: (String) -> Void
    let onViewRestaurant: (String) -> Void

    @ObservedObject var localization = LocalizationManager.shared

    private var primary: Menu { pin.offers[0] }
    private var extraCount: Int { pin.offers.count - 1 }
    private var distanceKm: Double? {
        guard let user = userLocation else { return nil }
        let merchant = CLLocation(latitude: pin.coordinate.latitude, longitude: pin.coordinate.longitude)
        return user.distance(from: merchant) / 1000
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.custom("Sora-Regular", size: 28))
                        .foregroundColor(.appTextSecondary)
                }
            }
            HStack(alignment: .top, spacing: 12) {
                AsyncImage(url: primary.photoUrls.first.flatMap(URL.init(string:))) { image in
                    image.resizable().scaledToFill()
                } placeholder: {
                    Color.appSurface
                }
                .frame(width: 80, height: 80)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    Text(pin.merchant.name)
                        .font(.custom("Sora-Bold", size: 17))
                        .foregroundColor(.appTextPrimary)
                    Text(primary.title)
                        .font(.appBody)
                        .foregroundColor(.appTextSecondary)
                        .lineLimit(2)
                    HStack(spacing: 8) {
                        Text("\(String(format: "%.2f", primary.priceTotal)) €")
                            .font(.custom("Sora-Bold", size: 14))
                            .foregroundColor(.appPrimary)
                        if let km = distanceKm {
                            Text("· \(String(format: "%.1f", km)) km")
                                .font(.appCaption)
                                .foregroundColor(.appTextSecondary)
                        }
                    }
                }
                Spacer()
            }

            if extraCount > 0 {
                Text(String(format: localization.t("map_more_offers"), extraCount))
                    .font(.appCaption)
                    .foregroundColor(.appTextSecondary)
            }

            Button(action: { onViewOffer(primary.id) }) {
                Text(localization.t("map_view_offer"))
                    .font(.appHeadline)
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Color.appPrimary))
            }

            Button(action: { onViewRestaurant(pin.id) }) {
                Text(localization.t("map_view_restaurant"))
                    .font(.appHeadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.appPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.appPrimary, lineWidth: 1.5)
                    )
            }
        }
        .padding(24)
    }
}

/// Marker custom con el logo de Zampa sobre círculo naranja con borde blanco.
private struct BrandMarker: View {
    var body: some View {
        Image("Logo")
            .resizable()
            .scaledToFit()
            .frame(width: 28, height: 28)
            .foregroundColor(.white)
            .padding(8)
            .background(Circle().fill(Color.appPrimary))
            .overlay(Circle().stroke(Color.white, lineWidth: 2))
            .shadow(color: Color.black.opacity(0.25), radius: 3, x: 0, y: 2)
    }
}
