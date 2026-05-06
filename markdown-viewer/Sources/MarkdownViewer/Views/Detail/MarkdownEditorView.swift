import SwiftUI

struct MarkdownEditorView: View {
    @Environment(AppState.self) private var appState

    var body: some View {
        @Bindable var state = appState

        TextEditor(text: $state.editorContent)
            .font(.system(.body, design: .monospaced))
            .scrollContentBackground(.visible)
            .padding(4)
            .onChange(of: appState.editorContent) { _, _ in
                appState.tocItems = MarkdownParser.extractTOC(from: appState.editorContent)
            }
    }
}
