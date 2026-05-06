import SwiftUI

struct ContentView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        NavigationSplitView {
            FileTreeView()
        } detail: {
            if appState.selectedFile != nil {
                HSplitView {
                    TOCView()
                        .frame(minWidth: 180, idealWidth: 220, maxWidth: 280)
                    MarkdownDetailView()
                }
            } else {
                MarkdownDetailView()
            }
        }
        .toolbar {
            EditPreviewToolbar()
        }
        .onDrop(of: [.fileURL], isTargeted: nil) { providers in
            handleDrop(providers: providers)
        }
    }

    private func handleDrop(providers: [NSItemProvider]) -> Bool {
        var urls: [URL] = []
        let group = DispatchGroup()

        for provider in providers {
            group.enter()
            provider.loadItem(forTypeIdentifier: "public.file-url") { item, _ in
                defer { group.leave() }
                guard let data = item as? Data,
                      let url = URL(dataRepresentation: data, relativeTo: nil) else { return }
                urls.append(url)
            }
        }

        group.notify(queue: .main) {
            appState.openFiles(from: urls)
        }

        return true
    }
}
