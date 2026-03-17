import SwiftUI
import Charts

struct DailyStat: Identifiable {
    let id = UUID()
    let date: Date
    let impressions: Int
    let favorites: Int
    let calls: Int
    let directions: Int
    let shares: Int
    
    var totalClicks: Int {
        calls + directions + shares
    }
}

struct StatsView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var appState: AppState
    @State private var stats: [DailyStat] = []
    @State private var isLoading = true
    @State private var selectedTimeRange: Int = 7 // 7, 14, 30 days
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    // Time Range Selector
                    Picker("Periodo", selection: $selectedTimeRange) {
                        Text("7 días").tag(7)
                        Text("14 días").tag(14)
                        Text("30 días").tag(30)
                    }
                    .pickerStyle(SegmentedPickerStyle())
                    .padding(.horizontal)
                    .onChange(of: selectedTimeRange) {
                        loadStats()
                    }
                    
                    if isLoading {
                        ProgressView()
                            .padding(.top, 40)
                    } else if stats.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "chart.bar.xaxis")
                                .font(.system(size: 40))
                                .foregroundColor(.appTextSecondary.opacity(0.3))
                            Text("No hay datos suficientes para mostrar")
                                .font(.appSubheadline)
                                .foregroundColor(.appTextSecondary)
                        }
                        .padding(.top, 40)
                    } else {
                        // Summary Cards
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                            StatSummaryCard(title: "Impresiones", value: "\(stats.reduce(0) { $0 + $1.impressions })", icon: "eye.fill", color: .appPrimary)
                            StatSummaryCard(title: "Favoritos", value: "\(stats.reduce(0) { $0 + $1.favorites })", icon: "heart.fill", color: .red)
                            StatSummaryCard(title: "Interacciones", value: "\(stats.reduce(0) { $0 + $1.totalClicks })", icon: "hand.tap.fill", color: .blue)
                            StatSummaryCard(title: "Llamadas", value: "\(stats.reduce(0) { $0 + $1.calls })", icon: "phone.fill", color: .green)
                        }
                        .padding(.horizontal)
                        
                        // Impressions Chart
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Visibilidad (Impresiones)")
                                .font(.appHeadline)
                            
                            Chart {
                                ForEach(stats) { stat in
                                    AreaMark(
                                        x: .value("Fecha", stat.date),
                                        y: .value("Impresiones", stat.impressions)
                                    )
                                    .foregroundStyle(Color.appPrimary.opacity(0.1).gradient)
                                    
                                    LineMark(
                                        x: .value("Fecha", stat.date),
                                        y: .value("Impresiones", stat.impressions)
                                    )
                                    .foregroundStyle(Color.appPrimary)
                                    .symbol(Circle())
                                }
                            }
                            .frame(height: 200)
                        }
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.appSurface))
                        .shadow(color: Color.black.opacity(0.05), radius: 10)
                        .padding(.horizontal)
                        
                        // Interaction Types Chart
                        VStack(alignment: .leading, spacing: 12) {
                            Text("Tipos de Interacción")
                                .font(.appHeadline)
                            
                            Chart {
                                BarMark(
                                    x: .value("Tipo", "Llamar"),
                                    y: .value("Cantidad", stats.reduce(0) { $0 + $1.calls })
                                )
                                .foregroundStyle(.green)
                                
                                BarMark(
                                    x: .value("Tipo", "Geolocalización"),
                                    y: .value("Cantidad", stats.reduce(0) { $0 + $1.directions })
                                )
                                .foregroundStyle(.blue)
                                
                                BarMark(
                                    x: .value("Tipo", "Compartir"),
                                    y: .value("Cantidad", stats.reduce(0) { $0 + $1.shares })
                                )
                                .foregroundStyle(.purple)
                            }
                            .frame(height: 200)
                        }
                        .padding()
                        .background(RoundedRectangle(cornerRadius: 16).fill(Color.appSurface))
                        .shadow(color: Color.black.opacity(0.05), radius: 10)
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
            }
            .background(Color.appBackground.ignoresSafeArea())
            .navigationTitle("Estadísticas")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cerrar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .onAppear {
                loadStats()
            }
        }
    }
    
    private func loadStats() {
        guard let merchantId = appState.currentUser?.id else { return }
        isLoading = true
        
        Task {
            do {
                let rawStats = try await FirebaseService.shared.getMerchantStats(merchantId: merchantId, days: selectedTimeRange)
                
                let formatter = DateFormatter()
                formatter.dateFormat = "yyyy-MM-dd"
                
                let parsedStats: [DailyStat] = rawStats.compactMap { dict in
                    guard let dateStr = dict["date"] as? String,
                          let date = formatter.date(from: dateStr) else { return nil }
                    
                    let clicks = dict["clicks"] as? [String: Int] ?? [:]
                    
                    return DailyStat(
                        date: date,
                        impressions: dict["impressions"] as? Int ?? 0,
                        favorites: dict["favorites"] as? Int ?? 0,
                        calls: clicks["call"] ?? 0,
                        directions: clicks["directions"] ?? 0,
                        shares: clicks["share"] ?? 0
                    )
                }.sorted { $0.date < $1.date }
                
                await MainActor.run {
                    self.stats = parsedStats
                    self.isLoading = false
                }
            } catch {
                print("Error loading stats: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }
}

struct StatSummaryCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: icon)
                    .foregroundColor(color)
                    .font(.system(size: 18))
                Spacer()
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(value)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.appTextPrimary)
                Text(title)
                    .font(.caption)
                    .foregroundColor(.appTextSecondary)
            }
        }
        .padding()
        .background(RoundedRectangle(cornerRadius: 16).fill(Color.appSurface))
        .shadow(color: Color.black.opacity(0.05), radius: 10)
    }
}

#Preview {
    StatsView()
        .environmentObject(AppState())
}
