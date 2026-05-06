import SwiftUI

struct FileTreeView: View {
    @Environment(AppState.self) private var appState
    @State private var selectedFile: URL?

    var body: some View {
        VStack(spacing: 0) {
            if appState.openFiles.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "doc.text")
                        .font(.system(size: 48))
                        .foregroundColor(.secondary)
                    Text("No Files Open")
                        .font(.headline)
                    Text("Drag files here or press Cmd+O")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                    Button("Open File") {
                        openFile()
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                List(selection: $selectedFile) {
                    Section {
                        Button {
                            openFile()
                        } label: {
                            HStack {
                                Image(systemName: "plus.circle.fill")
                                    .foregroundColor(.accentColor)
                                Text("Open File")
                            }
                        }
                    }

                    Section {
                        ForEach(appState.openFiles, id: \.self) { url in
                            FileRow(url: url)
                                .tag(url)
                        }
                    }
                }
                .listStyle(.sidebar)
            }
        }
        .navigationTitle("Files")
        .onChange(of: selectedFile) { _, newURL in
            if let url = newURL {
                appState.loadFile(at: url)
            }
        }
        .onDrop(of: [.fileURL], isTargeted: nil) { providers in
            handleDrop(providers: providers)
        }
    }

    private func openFile() {
        let panel = NSOpenPanel()
        panel.title = "Choose a Markdown file"
        panel.allowsMultipleSelection = true
        panel.canChooseDirectories = false
        panel.canChooseFiles = true

        guard panel.runModal() == .OK else { return }
        appState.openFiles(from: panel.urls)
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

struct FileRow: View {
    let url: URL
    @Environment(AppState.self) private var appState

    var body: some View {
        HStack {
            Label {
                Text(url.lastPathComponent)
                    .font(.body)
            } icon: {
                Image(systemName: "doc.text")
                    .foregroundColor(.accentColor)
            }

            Spacer()

            Button {
                appState.removeFile(url)
            } label: {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.secondary)
            }
            .buttonStyle(.plain)
            .opacity(0.6)
        }
    }
}
