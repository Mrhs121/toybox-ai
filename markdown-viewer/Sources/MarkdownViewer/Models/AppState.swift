import Foundation

struct TOCItem: Identifiable {
    let id = UUID()
    let text: String
    let level: Int
    let anchor: String
}

@Observable
class AppState {
    var openFiles: [URL] = []
    var selectedFile: URL?
    var isEditing: Bool = false
    var editorContent: String = ""
    var renderedHTML: String = ""
    var tocItems: [TOCItem] = []
    var scrollToAnchor: String?
    var isLoading: Bool = false

    func loadFile(at url: URL) {
        selectedFile = url
        isEditing = false
        scrollToAnchor = nil
        guard url.startAccessingSecurityScopedResource() else {
            editorContent = ""
            renderedHTML = "<p>Permission denied</p>"
            return
        }
        defer { url.stopAccessingSecurityScopedResource() }

        guard let content = try? String(contentsOf: url, encoding: .utf8) else {
            editorContent = ""
            renderedHTML = "<p>Failed to load file</p>"
            return
        }
        editorContent = content
        renderedHTML = MarkdownParser.renderHTML(from: content)
        tocItems = MarkdownParser.extractTOC(from: content)

        if !openFiles.contains(url) {
            openFiles.append(url)
        }
    }

    func openFiles(from urls: [URL]) {
        for url in urls {
            if url.pathExtension.lowercased() == "md" || url.pathExtension.lowercased() == "markdown" {
                loadFile(at: url)
            }
        }
    }

    func saveFile() {
        guard let url = selectedFile else { return }
        try? editorContent.write(to: url, atomically: true, encoding: .utf8)
    }

    func toggleEdit() {
        isEditing.toggle()
        if !isEditing {
            renderedHTML = MarkdownParser.renderHTML(from: editorContent)
            tocItems = MarkdownParser.extractTOC(from: editorContent)
        }
    }

    func removeFile(_ url: URL) {
        openFiles.removeAll { $0 == url }
        if selectedFile == url {
            selectedFile = openFiles.last
            if let url = selectedFile {
                loadFile(at: url)
            }
        }
    }

    func scrollToTOCItem(_ anchor: String) {
        scrollToAnchor = anchor
    }
}
