import SwiftUI
@preconcurrency import Hinge

/// Lays out two views as one or two panes, avoiding the hinge.
///
/// The SwiftUI counterpart to the Compose composable of the same name, resolving its geometry
/// through the same shared Kotlin core so both platforms agree on what "two panes" means.
///
/// ```swift
/// FoldAwarePanes {
///     ConversationList(selection: $selected)
/// } secondary: {
///     ConversationDetail(selected)
/// }
/// ```
///
/// Requires an ancestor to have applied ``SwiftUICore/View/foldAware()`` to react to a fold.
/// Without it the posture stays unknown — but note that under the default
/// ``SplitStrategy/foldAware`` a window wide enough for two panes still splits on size alone,
/// so an iPad or a landscape phone shows two panes with no `foldAware()` anywhere. That is
/// the intended behaviour; pass `SplitStrategy.neverSplit` if you want one pane regardless.
///
/// Geometry is translated into this view's own coordinate space, so a `FoldAwarePanes` that
/// occupies part of the screen splits only if the fold actually crosses it.
public struct FoldAwarePanes<Primary: View, Secondary: View>: View {

    @Environment(\.foldingState) private var foldingState

    private let spec: SplitSpec
    private let primary: () -> Primary
    private let secondary: () -> Secondary

    public init(
        spec: SplitSpec = .standard,
        @ViewBuilder primary: @escaping () -> Primary,
        @ViewBuilder secondary: @escaping () -> Secondary
    ) {
        self.spec = spec
        self.primary = primary
        self.secondary = secondary
    }

    public var body: some View {
        GeometryReader { proxy in
            let frame = proxy.frame(in: .global)
            let local = foldingState.inLocalSpace(
                originX: Float(frame.minX),
                originY: Float(frame.minY),
                size: FoldSize(
                    width: Float(proxy.size.width),
                    height: Float(proxy.size.height)
                )
            )

            if let split = local.resolvePaneLayout(spec: spec).splitOrNull() {
                ZStack(alignment: .topLeading) {
                    primary()
                        .frame(
                            width: CGFloat(split.primary.width),
                            height: CGFloat(split.primary.height)
                        )
                        .offset(
                            x: CGFloat(split.primary.left),
                            y: CGFloat(split.primary.top)
                        )
                    secondary()
                        .frame(
                            width: CGFloat(split.secondary.width),
                            height: CGFloat(split.secondary.height)
                        )
                        .offset(
                            x: CGFloat(split.secondary.left),
                            y: CGFloat(split.secondary.top)
                        )
                }
                .frame(
                    width: proxy.size.width,
                    height: proxy.size.height,
                    alignment: .topLeading
                )
            } else {
                primary()
                    .frame(width: proxy.size.width, height: proxy.size.height)
            }
        }
    }
}

public extension View {
    /// Insets content away from occluding fold regions that reach a window edge.
    ///
    /// The counterpart to Compose's `rememberOcclusionPadding`. A region in the middle of the
    /// window cannot be padded away — that is a split, and ``FoldAwarePanes`` handles it.
    /// Apply this to full-screen content, since the geometry it reads is window-relative.
    func occlusionPadding(_ state: FoldingState) -> some View {
        let insets = state.occlusionInsets()
        return padding(.leading, insets.leading)
            .padding(.top, insets.top)
            .padding(.trailing, insets.trailing)
            .padding(.bottom, insets.bottom)
    }
}

extension FoldingState {
    struct Insets {
        var leading: CGFloat = 0
        var top: CGFloat = 0
        var trailing: CGFloat = 0
        var bottom: CGFloat = 0
    }

    func occlusionInsets(tolerance: Float = 0.5) -> Insets {
        var insets = Insets()
        for region in occludingRegions where region.isSubstantial {
            let b = region.bounds
            if b.left <= tolerance && b.right < windowSize.width {
                insets.leading = max(insets.leading, CGFloat(b.right))
            } else if b.right >= windowSize.width - tolerance && b.left > 0 {
                insets.trailing = max(insets.trailing, CGFloat(windowSize.width - b.left))
            } else if b.top <= tolerance && b.bottom < windowSize.height {
                insets.top = max(insets.top, CGFloat(b.bottom))
            } else if b.bottom >= windowSize.height - tolerance && b.top > 0 {
                insets.bottom = max(insets.bottom, CGFloat(windowSize.height - b.top))
            }
        }
        return insets
    }
}
