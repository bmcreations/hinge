import CoreGraphics
@preconcurrency import Hinge

/// The postures the demo can force, overriding whatever the hardware reports.
///
/// Mirrors `SimulatedPosture` in the Compose sample, and calls the same Kotlin `Postures`
/// factories, so both apps simulate byte-identical states.
enum SimulatedPosture: String, CaseIterable, Identifiable {
    case live, flat, book, tabletop, seamless, occlusion, inactiveFold, cover, unknown

    var id: String { rawValue }

    var label: String {
        switch self {
        case .live: return "Live"
        case .flat: return "Flat"
        case .book: return "Book"
        case .tabletop: return "Tabletop"
        case .seamless: return "Seamless"
        case .occlusion: return "Occlusion"
        case .inactiveFold: return "Inactive"
        case .cover: return "Cover"
        case .unknown: return "Unknown"
        }
    }

    /// The surface the demo should render into, or `nil` to fill the window.
    ///
    /// `cover` is why this exists: a small `windowSize` alone is not enough, because
    /// `FoldAwarePanes` measures itself and overrides it. Simulating a small device means
    /// actually giving the content less room.
    var surfaceOverride: CGSize? {
        self == .cover
            ? CGSize(width: CGFloat(Postures.shared.COVER_WIDTH),
                     height: CGFloat(Postures.shared.COVER_HEIGHT))
            : nil
    }

    func resolve(live: FoldingState, width: Float, height: Float) -> FoldingState {
        switch self {
        case .live:
            return live
        case .flat:
            return Postures.shared.flat(width: width, height: height)
        case .book:
            return Postures.shared.book(
                width: width,
                height: height,
                hingeThickness: Postures.shared.DEFAULT_HINGE_THICKNESS
            )
        case .tabletop:
            return Postures.shared.tabletop(
                width: width,
                height: height,
                hingeThickness: Postures.shared.DEFAULT_HINGE_THICKNESS
            )
        case .seamless:
            return Postures.shared.seamlessBook(width: width, height: height)
        case .occlusion:
            return Postures.shared.occlusionOnly(
                width: width,
                height: height,
                thickness: Postures.shared.DEFAULT_OCCLUSION_THICKNESS
            )
        case .inactiveFold:
            return Postures.shared.inactiveFold(width: width, height: height)
        case .cover:
            return Postures.shared.coverDisplay(width: width, height: height)
        case .unknown:
            return Postures.shared.unknown()
        }
    }
}
