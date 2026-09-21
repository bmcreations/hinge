import SwiftUI
@preconcurrency import Hinge

/// List/detail, the canonical two-pane case. Mirrors `NotesScreen.kt` in the Compose sample.
///
/// Note the division of labour. `FoldAwarePanes` owns the geometry. The app owns navigation,
/// which is why it reads `panes()` separately: whether tapping a row replaces the screen or
/// just updates the neighbouring pane is a product decision the library refuses to make.
///
/// `panes()` resolves *window* geometry while `FoldAwarePanes` resolves its own local
/// geometry. They agree here only because this screen fills the window. If you paste this into
/// a screen that does not, drive the two-pane decision from the same geometry the panes use,
/// or the detail pane can become unreachable: the list renders, the secondary slot is never
/// built, and the tap goes nowhere.
struct NotesScreen: View {

    @Environment(\.foldingState) private var foldingState
    @State private var selected: Note?

    let contentInsets: EdgeInsets

    private var isTwoPane: Bool {
        foldingState.panes().splitOrNull() != nil
    }

    var body: some View {
        FoldAwarePanes {
            // In one-pane mode the primary slot carries the whole navigation stack.
            if !isTwoPane, let selected {
                NoteDetail(
                    note: selected,
                    onBack: { self.selected = nil },
                    contentInsets: contentInsets
                )
            } else {
                NoteList(
                    selected: isTwoPane ? selected : nil,
                    contentInsets: contentInsets
                ) { note in
                    selected = note
                }
            }
        } secondary: {
            NoteDetail(note: selected, onBack: nil, contentInsets: contentInsets)
        }
        // Insets the panes away from any occluding region that reaches a window edge. With
        // the Occlusion posture selected, watch the whole layout shift clear of the strip.
        .occlusionPadding(foldingState)
    }
}

private struct NoteList: View {
    let selected: Note?
    let contentInsets: EdgeInsets
    let onSelect: (Note) -> Void

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(sampleNotes) { note in
                    Button {
                        onSelect(note)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(note.title)
                                .font(.body)
                                .fontWeight(note.id == selected?.id ? .semibold : .regular)
                                .foregroundStyle(
                                    note.id == selected?.id ? Color.accentColor : Color.primary
                                )
                            Text(note.subtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .buttonStyle(.plain)

                    Divider()
                }
            }
            .padding(.top, contentInsets.top)
            .padding(.bottom, contentInsets.bottom)
        }
        .background(Color(.systemBackground))
    }
}

private struct NoteDetail: View {
    let note: Note?
    let onBack: (() -> Void)?
    let contentInsets: EdgeInsets

    var body: some View {
        Group {
            if let note {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if let onBack {
                            Button("Back", action: onBack).font(.subheadline)
                        }
                        Text(note.subtitle.uppercased())
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(note.title).font(.title2.weight(.semibold))
                        Text(note.body).font(.callout)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .padding(.top, contentInsets.top)
                    .padding(.bottom, contentInsets.bottom)
                }
            } else {
                VStack {
                    Text("Select a note")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(Color(.secondarySystemGroupedBackground))
    }
}
