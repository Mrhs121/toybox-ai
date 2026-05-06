import SwiftUI

struct MarkdownDetailView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        if appState.selectedFile == nil {
            VStack(spacing: 20) {
                Image(systemName: "doc.text")
                    .font(.system(size: 64))
                    .foregroundColor(.secondary)
                Text("No File Selected")
                    .font(.title2)
                    .fontWeight(.medium)
                Text("Choose a file from the sidebar,\ndrag files onto the window,\nor press Cmd+O to open")
                    .font(.body)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                Button("Open File") {
                    openFile()
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 0) {
                // File name header
                HStack {
                    Text(appState.selectedFile?.lastPathComponent ?? "")
                        .font(.headline)
                        .lineLimit(1)
                    Spacer()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Color(nsColor: .controlBackgroundColor))

                Divider()

                // Content
                if appState.isEditing {
                    MarkdownEditorView()
                } else {
                    MarkdownPreviewView(
                        htmlContent: appState.renderedHTML,
                        scrollToAnchor: appState.scrollToAnchor
                    )
                }
            }
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
}
