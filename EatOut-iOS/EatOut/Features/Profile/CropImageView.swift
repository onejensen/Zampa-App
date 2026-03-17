import SwiftUI

struct CropImageView: View {
    let sourceImage: UIImage
    let onConfirm: (UIImage) -> Void
    let onCancel: () -> Void

    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private let cropDiameter: CGFloat = 280

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            GeometryReader { geo in
                let cx = geo.size.width / 2
                let cy = geo.size.height / 2

                // MARK: Image
                Image(uiImage: sourceImage)
                    .resizable()
                    .scaledToFill()
                    .frame(width: cropDiameter * scale, height: cropDiameter * scale)
                    .clipped()
                    .position(x: cx + offset.width, y: cy + offset.height)
                    .gesture(
                        SimultaneousGesture(
                            MagnificationGesture()
                                .onChanged { v in scale = max(1.0, lastScale * v) }
                                .onEnded { _ in lastScale = scale },
                            DragGesture()
                                .onChanged { v in
                                    offset = CGSize(
                                        width: lastOffset.width + v.translation.width,
                                        height: lastOffset.height + v.translation.height
                                    )
                                }
                                .onEnded { _ in lastOffset = offset }
                        )
                    )

                // MARK: Dark overlay with circular hole (even-odd fill)
                Canvas { ctx, size in
                    var path = Path()
                    path.addRect(CGRect(origin: .zero, size: size))
                    path.addEllipse(in: CGRect(
                        x: cx - cropDiameter / 2,
                        y: cy - cropDiameter / 2,
                        width: cropDiameter,
                        height: cropDiameter
                    ))
                    ctx.fill(path, with: .color(.black.opacity(0.65)),
                             style: FillStyle(eoFill: true))
                }
                .allowsHitTesting(false)

                // MARK: Circle border
                Circle()
                    .stroke(Color.white.opacity(0.8), lineWidth: 1.5)
                    .frame(width: cropDiameter, height: cropDiameter)
                    .position(x: cx, y: cy)
                    .allowsHitTesting(false)

                // MARK: Hint
                Text("Arrastra y pellizca para encuadrar")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.6))
                    .position(x: cx, y: cy + cropDiameter / 2 + 24)
                    .allowsHitTesting(false)

                // MARK: Buttons
                VStack {
                    Spacer()
                    HStack {
                        Button("Cancelar", action: onCancel)
                            .foregroundColor(.white)
                            .padding(.horizontal, 28)
                            .padding(.bottom, 44)

                        Spacer()

                        Button("Confirmar") {
                            onConfirm(renderCrop(cx: cx, cy: cy))
                        }
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.horizontal, 28)
                        .padding(.bottom, 44)
                    }
                }
            }
        }
    }

    // MARK: - Render cropped UIImage

    private func renderCrop(cx: CGFloat, cy: CGFloat) -> UIImage {
        // SwiftUI displays the image with .scaledToFill in a square frame of side W = cropDiameter*scale.
        // scaledToFill: scale so the *shorter* source dimension fills W, then center.
        // We replicate this exactly so the render matches what the user sees.

        let W = cropDiameter * scale
        let srcW = sourceImage.size.width
        let srcH = sourceImage.size.height

        // scaledToFill scale factor
        let fillScale = W / min(srcW, srcH)
        let renderedW = srcW * fillScale   // actual rendered image width (may exceed W)
        let renderedH = srcH * fillScale   // actual rendered image height (may exceed W)

        // Image frame center in view space; frame top-left:
        let frameCX = cx + offset.width
        let frameCY = cy + offset.height
        // scaledToFill centers the rendered image within the frame:
        let imgX = frameCX - renderedW / 2
        let imgY = frameCY - renderedH / 2

        // Crop circle top-left in view space:
        let cropLeft = cx - cropDiameter / 2
        let cropTop  = cy - cropDiameter / 2

        let outputSize = CGSize(width: cropDiameter, height: cropDiameter)
        let renderer = UIGraphicsImageRenderer(size: outputSize)
        return renderer.image { _ in
            UIBezierPath(ovalIn: CGRect(origin: .zero, size: outputSize)).addClip()
            // Draw image at its natural aspect-preserving size, relative to crop circle origin:
            sourceImage.draw(in: CGRect(
                x: imgX - cropLeft,
                y: imgY - cropTop,
                width: renderedW,
                height: renderedH
            ))
        }
    }
}
