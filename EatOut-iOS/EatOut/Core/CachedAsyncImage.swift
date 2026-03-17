import SwiftUI
import UIKit

// MARK: - ImageCache

/// Caché en dos niveles: memoria (NSCache) + disco (Caches/).
/// La memoria se vacía al reiniciar; el disco persiste entre sesiones.
final class ImageCache: @unchecked Sendable {
    static let shared = ImageCache()
    private let memory = NSCache<NSString, UIImage>()
    private let diskDir: URL

    private init() {
        memory.countLimit = 150
        memory.totalCostLimit = 100 * 1024 * 1024

        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
        diskDir = caches.appendingPathComponent("EatOutImageCache", isDirectory: true)
        try? FileManager.default.createDirectory(at: diskDir, withIntermediateDirectories: true)
    }

    // MARK: Disk helpers

    /// Nombre de archivo en disco derivado de la URL (hash DJB2, estable entre sesiones).
    private func diskPath(for url: URL) -> URL {
        var hash: UInt64 = 5381
        for scalar in url.absoluteString.unicodeScalars {
            hash = (hash &* 31) &+ UInt64(scalar.value)
        }
        return diskDir.appendingPathComponent("\(hash).jpg")
    }

    // MARK: Subscript (memoria → disco → nil)

    subscript(url: URL) -> UIImage? {
        get {
            let key = url.absoluteString as NSString
            // 1. Memoria
            if let img = memory.object(forKey: key) { return img }
            // 2. Disco — carga y promueve a memoria
            let path = diskPath(for: url)
            guard let data = try? Data(contentsOf: path),
                  let img = UIImage(data: data) else { return nil }
            let cost = Int(img.size.width * img.size.height * 4)
            memory.setObject(img, forKey: key, cost: cost)
            return img
        }
        set {
            let key = url.absoluteString as NSString
            guard let img = newValue else {
                memory.removeObject(forKey: key)
                try? FileManager.default.removeItem(at: diskPath(for: url))
                return
            }
            let cost = Int(img.size.width * img.size.height * 4)
            memory.setObject(img, forKey: key, cost: cost)
            // Escribir en disco en background
            let path = diskPath(for: url)
            Task.detached(priority: .background) {
                if let data = img.jpegData(compressionQuality: 0.85) {
                    try? data.write(to: path, options: .atomic)
                }
            }
        }
    }
}

// MARK: - CachedAsyncImage (phase API)

/// Reemplazo de AsyncImage que usa ImageCache.shared:
/// la primera descarga guarda la imagen en RAM; las siguientes
/// (p.ej. abrir el modal de detalle) la sirven al instante sin red.
struct CachedAsyncImage<Content: View>: View {
    private let url: URL?
    private let content: (AsyncImagePhase) -> Content

    @State private var phase: AsyncImagePhase = .empty
    @State private var loadTask: Task<Void, Never>?

    /// Init con API de phase (igual que AsyncImage(url:) { phase in ... })
    init(url: URL?, @ViewBuilder content: @escaping (AsyncImagePhase) -> Content) {
        self.url = url
        self.content = content
    }

    var body: some View {
        content(phase)
            .onAppear { load() }
            .onDisappear { loadTask?.cancel() }
            .onChange(of: url?.absoluteString) { _, _ in
                phase = .empty
                loadTask?.cancel()
                load()
            }
    }

    private func load() {
        guard let url else { phase = .empty; return }

        // Caché hit: imagen disponible al instante
        if let cached = ImageCache.shared[url] {
            phase = .success(Image(uiImage: cached))
            return
        }

        // Caché miss: descargar y almacenar
        loadTask = Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                guard !Task.isCancelled, let img = UIImage(data: data) else { return }
                ImageCache.shared[url] = img
                await MainActor.run { phase = .success(Image(uiImage: img)) }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run { phase = .failure(error) }
            }
        }
    }
}

// MARK: - Convenience init (content + placeholder API)

extension CachedAsyncImage where Content == AnyView {
    /// Init con API simple (igual que AsyncImage(url:content:placeholder:))
    init<I: View, P: View>(
        url: URL?,
        @ViewBuilder content: @escaping (Image) -> I,
        @ViewBuilder placeholder: @escaping () -> P
    ) {
        self.init(url: url) { phase in
            AnyView(Group {
                switch phase {
                case .success(let img): content(img)
                default: placeholder()
                }
            })
        }
    }
}
