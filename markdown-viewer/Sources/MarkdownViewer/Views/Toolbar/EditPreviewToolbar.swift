import SwiftUI

struct EditPreviewToolbar: ToolbarContent {
    @Environment(AppState.self) private var appState

    var body: some ToolbarContent {
        ToolbarItemGroup(placement: .automatic) {
            if appState.selectedFile != nil {
                Picker("Mode", selection: Binding(
                    get: { appState.isEditing },
                    set: { _ in appState.toggleEdit() }
                )) {
                    Label("Preview", systemImage: "eye").tag(false)
                    Label("Edit", systemImage: "pencil").tag(true)
                }
                .pickerStyle(.segmented)
                .frame(width: 160)
            }
        }
    }
}
