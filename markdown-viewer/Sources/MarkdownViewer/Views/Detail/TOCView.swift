import SwiftUI

struct TOCView: View {
    @Environment(AppState.self) private var appState
    @State private var selectedAnchor: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if appState.tocItems.isEmpty {
                ContentUnavailableView(
                    "No Headings",
                    systemImage: "list.bullet",
                    description: Text("Headings will appear here")
                )
            } else {
                List(appState.tocItems, id: \.anchor, selection: $selectedAnchor) { item in
                    Text(item.text)
                        .font(.system(size: tocFontSize(for: item.level)))
                        .lineLimit(1)
                        .padding(.leading, CGFloat((item.level - 1) * 12))
                        .tag(item.anchor)
                }
                .listStyle(.sidebar)
                .onChange(of: selectedAnchor) { _, anchor in
                    if let anchor {
                        appState.scrollToTOCItem(anchor)
                    }
                }
            }
        }
    }

    private func tocFontSize(for level: Int) -> CGFloat {
        switch level {
        case 1: return 14
        case 2: return 13
        case 3: return 12
        default: return 11
        }
    }
}
