package com.example.androidterminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.PointerIcon;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.androidterminal.databinding.ActivityMainBinding;
import com.example.androidterminal.ssh.SshConnectionConfig;
import com.example.androidterminal.ssh.SshConnectionService;
import com.example.androidterminal.ssh.SshSessionRepository;
import com.example.androidterminal.ssh.SshTerminalSession;
import com.example.androidterminal.terminalview.TerminalView;
import com.example.androidterminal.terminalview.TerminalViewClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MainActivity extends AppCompatActivity implements TerminalViewClient, TerminalSessionClient, SshTerminalSession.Listener {

    private static final String INPUT_LOG_TAG = "AndroidTerminalInput";
    private static final String PREFS = "ssh-terminal-prefs";
    private static final String KEY_SAVED_CONNECTIONS = "saved_connections";
    private static final String KEY_SELECTED_CONNECTION_ID = "selected_connection_id";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final int DEFAULT_TERMINAL_TEXT_SIZE_SP = 30;
    private static final int MIN_TERMINAL_TEXT_SIZE_SP = 12;
    private static final int MAX_TERMINAL_TEXT_SIZE_SP = 60;

    private ActivityMainBinding binding;
    private SharedPreferences preferences;
    private TerminalView terminalView;
    private SshTerminalSession sshTerminalSession;
    private float terminalScaleFactor = 1.0f;
    private final List<SavedConnection> savedConnections = new ArrayList<>();
    private final List<String> tabSessionIds = new ArrayList<>();
    private String selectedConnectionId;
    private String activeSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoreSavedConnections();
        setupTerminal();
        bindActions();
        if (savedConnections.isEmpty()) {
            setConnectionPanelExpanded(true);
        }
    }

    @Override
    protected void onDestroy() {
        List<SshTerminalSession> sessions = SshSessionRepository.listSessions();
        for (SshTerminalSession session : sessions) {
            if (isFinishing()) {
                SshSessionRepository.disconnectAndRemove(session, "Disconnected");
            } else {
                SshSessionRepository.detachUi(session);
            }
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerRate(600);
            terminalView.setTerminalCursorBlinkerState(true, true);
        }
    }

    @Override
    protected void onPause() {
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(false, false);
        }
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (sshTerminalSession != null && shouldRoutePhysicalKeyboardToTerminal() && shouldTreatAsTerminalHardwareKey(event)) {
            Log.d(INPUT_LOG_TAG, "dispatchKeyEvent action=" + event.getAction() + " keyCode=" + event.getKeyCode()
                + " source=" + event.getSource() + " deviceId=" + event.getDeviceId());
            if (event.getAction() == KeyEvent.ACTION_DOWN && handleTerminalZoomShortcut(event)) {
                Log.d(INPUT_LOG_TAG, "handled by zoom shortcut");
                return true;
            }

            if (event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE || event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    sshTerminalSession.sendEscape();
                }
                return true;
            }

            if (terminalView != null) {
                terminalView.requestFocus();
                switch (event.getAction()) {
                    case KeyEvent.ACTION_DOWN:
                        boolean handledDown = terminalView.onKeyDown(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyDown handled=" + handledDown);
                        return handledDown;
                    case KeyEvent.ACTION_UP:
                        boolean handledUp = terminalView.onKeyUp(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyUp handled=" + handledUp);
                        return handledUp;
                    case KeyEvent.ACTION_MULTIPLE:
                        boolean handledMultiple = terminalView.onKeyDown(event.getKeyCode(), event);
                        Log.d(INPUT_LOG_TAG, "terminalView.onKeyMultiple handled=" + handledMultiple);
                        return handledMultiple;
                    default:
                        break;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean shouldTreatAsTerminalHardwareKey(KeyEvent event) {
        if (event == null) {
            return false;
        }

        if (event.getDeviceId() == KeyCharacterMap.VIRTUAL_KEYBOARD) {
            return false;
        }

        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
            case KeyEvent.KEYCODE_POWER:
            case KeyEvent.KEYCODE_SLEEP:
            case KeyEvent.KEYCODE_WAKEUP:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_STOP:
            case KeyEvent.KEYCODE_MUTE:
                return false;
            default:
                return true;
        }
    }

    private void setupTerminal() {
        terminalView = new TerminalView(this, null);
        terminalView.setTerminalViewClient(this);
        terminalView.setFocusable(true);
        terminalView.setFocusableInTouchMode(true);
        terminalView.setKeepScreenOn(true);
        terminalView.setTextSize(DEFAULT_TERMINAL_TEXT_SIZE_SP);
        applyCompactPointerIcon();

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        binding.terminalContainer.addView(terminalView, layoutParams);
        restoreOrCreateTabs();
    }

    private void bindActions() {
        binding.showConnectionButton.setFocusable(false);
        binding.showConnectionButton.setFocusableInTouchMode(false);
        binding.drawerDisconnectButton.setOnClickListener(v -> {
            if (sshTerminalSession != null) {
                sshTerminalSession.disconnect();
            }
        });
        binding.showConnectionButton.setOnClickListener(v -> setConnectionPanelExpanded(true));
        binding.newConnectionButton.setOnClickListener(v -> showConnectionEditor(null));
        binding.newTabButton.setOnClickListener(v -> createNewTab(true));
        binding.drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                if (terminalView != null) {
                    terminalView.post(terminalView::requestFocus);
                }
            }
        });
    }

    private void connect(@Nullable SavedConnection savedConnection) {
        if (savedConnection == null) {
            toast(getString(R.string.saved_connections_empty));
            return;
        }
        connect(savedConnection.toConfig(), savedConnection.id);
    }

    private void connect(SshConnectionConfig config, @Nullable String selectedId) {
        setConnectionPanelExpanded(false);
        selectedConnectionId = selectedId;
        persistSelectedConnectionId();
        renderSavedConnections();
        SshTerminalSession targetSession = getReusableSessionForConnect();
        if (targetSession == null) {
            targetSession = createNewTab(true);
        } else {
            switchToTab(targetSession.getSessionId());
        }
        binding.statusText.setText(R.string.status_connecting);
        targetSession.connect(config);
        if (terminalView != null) {
            terminalView.requestFocus();
        }
    }

    private void setConnectionPanelExpanded(boolean expanded) {
        if (expanded) {
            binding.drawerLayout.openDrawer(binding.connectionPanel);
        } else {
            binding.drawerLayout.closeDrawer(binding.connectionPanel);
        }
    }

    private boolean shouldRoutePhysicalKeyboardToTerminal() {
        if (terminalView == null) {
            return false;
        }

        if (!binding.drawerLayout.isDrawerOpen(binding.connectionPanel)) {
            return true;
        }

        View currentFocus = getCurrentFocus();
        return currentFocus == terminalView;
    }

    private boolean handleTerminalZoomShortcut(KeyEvent event) {
        if (!event.isCtrlPressed()) {
            return false;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_EQUALS:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_NUMPAD_ADD:
                stepTerminalFontSize(1);
                return true;
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT:
                stepTerminalFontSize(-1);
                return true;
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_NUMPAD_0:
                setTerminalFontSize(DEFAULT_TERMINAL_TEXT_SIZE_SP);
                return true;
            default:
                return false;
        }
    }

    private void stepTerminalFontSize(int delta) {
        int currentSize = Math.round(DEFAULT_TERMINAL_TEXT_SIZE_SP * terminalScaleFactor);
        setTerminalFontSize(currentSize + delta);
    }

    private void setTerminalFontSize(int textSizeSp) {
        int clampedTextSize = Math.max(MIN_TERMINAL_TEXT_SIZE_SP, Math.min(textSizeSp, MAX_TERMINAL_TEXT_SIZE_SP));
        terminalScaleFactor = clampedTextSize / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP;
        if (terminalView != null) {
            terminalView.setTextSize(clampedTextSize);
        }
    }

    private void applyCompactPointerIcon() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            return;
        }

        PointerIcon pointerIcon = PointerIcon.getSystemIcon(this, PointerIcon.TYPE_TEXT);
        terminalView.setPointerIcon(pointerIcon);
        binding.terminalContainer.setPointerIcon(pointerIcon);
    }

    private void restoreOrCreateTabs() {
        tabSessionIds.clear();
        for (SshTerminalSession session : SshSessionRepository.listSessions()) {
            tabSessionIds.add(session.getSessionId());
            SshSessionRepository.attachUi(session, this, this);
        }

        if (tabSessionIds.isEmpty()) {
            createNewTab(true);
        } else {
            activeSessionId = tabSessionIds.get(0);
            switchToTab(activeSessionId);
            renderTabs();
        }
    }

    @NonNull
    private SshTerminalSession createNewTab(boolean switchToNewTab) {
        SshTerminalSession session = SshSessionRepository.create(this, this);
        tabSessionIds.add(session.getSessionId());
        if (switchToNewTab) {
            switchToTab(session.getSessionId());
        } else {
            renderTabs();
        }
        return session;
    }

    private void switchToTab(@Nullable String sessionId) {
        SshTerminalSession session = SshSessionRepository.findById(sessionId);
        if (session == null) {
            return;
        }

        activeSessionId = sessionId;
        sshTerminalSession = session;
        SshSessionRepository.attachUi(session, this, this);
        terminalView.attachSession(session);
        syncUiWithActiveSession();
        renderTabs();
        terminalView.requestFocus();
    }

    @Nullable
    private SshTerminalSession getReusableSessionForConnect() {
        SshTerminalSession activeSession = getActiveSession();
        if (activeSession == null) {
            return null;
        }
        return activeSession.isConnected() ? null : activeSession;
    }

    @Nullable
    private SshTerminalSession getActiveSession() {
        return SshSessionRepository.findById(activeSessionId);
    }

    private void closeTab(@NonNull String sessionId) {
        SshTerminalSession session = SshSessionRepository.findById(sessionId);
        if (session == null) {
            return;
        }

        SshSessionRepository.disconnectAndRemove(session, "Closed");
        tabSessionIds.remove(sessionId);

        if (tabSessionIds.isEmpty()) {
            createNewTab(true);
            return;
        }

        if (TextUtils.equals(activeSessionId, sessionId)) {
            switchToTab(tabSessionIds.get(Math.max(0, tabSessionIds.size() - 1)));
        } else {
            renderTabs();
            syncUiWithActiveSession();
        }
    }

    private void renderTabs() {
        binding.tabsContainer.removeAllViews();
        for (String sessionId : tabSessionIds) {
            SshTerminalSession session = SshSessionRepository.findById(sessionId);
            if (session == null) {
                continue;
            }

            View tabView = getLayoutInflater().inflate(R.layout.item_terminal_tab, binding.tabsContainer, false);
            android.widget.LinearLayout cardView = tabView.findViewById(R.id.terminal_tab_card);
            android.widget.TextView titleView = tabView.findViewById(R.id.terminal_tab_title);
            AppCompatImageButton closeButton = tabView.findViewById(R.id.close_tab_button);

            boolean active = TextUtils.equals(activeSessionId, sessionId);
            cardView.setBackgroundColor(ContextCompat.getColor(this, active ? R.color.tab_surface_active : R.color.tab_surface));
            titleView.setTextColor(ContextCompat.getColor(this, active ? R.color.tab_text_active : R.color.text_on_dark));
            ImageViewCompat.setImageTintList(
                closeButton,
                android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, active ? R.color.tab_text_active : R.color.text_muted_on_dark))
            );
            titleView.setText(session.getDisplayTitle());

            cardView.setOnClickListener(v -> switchToTab(sessionId));
            closeButton.setOnClickListener(v -> closeTab(sessionId));
            binding.tabsContainer.addView(tabView);
        }
    }

    private void syncUiWithActiveSession() {
        SshTerminalSession activeSession = getActiveSession();
        boolean connected = activeSession != null && activeSession.isConnected();
        updateConnectionActions(connected);
        if (activeSession == null) {
            binding.statusText.setText(R.string.status_idle);
            SshConnectionService.stop(this);
            return;
        }

        binding.statusText.setText(connected ? activeSession.getDisplayTitle() : getString(R.string.status_idle));
        if (connected) {
            SshConnectionService.start(this);
        } else if (!SshSessionRepository.hasConnectedSessions()) {
            SshConnectionService.stop(this);
        }
    }

    private void updateConnectionActions(boolean connected) {
        binding.drawerDisconnectButton.setEnabled(connected);
    }

    @Nullable
    private SshConnectionConfig buildConnectionConfig(String host, String portValue, String username, String password) {
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            toast(getString(R.string.missing_connection_info));
            return null;
        }

        int port = 22;
        if (!TextUtils.isEmpty(portValue)) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                toast(getString(R.string.invalid_port));
                return null;
            }
        }

        return new SshConnectionConfig(host, port, username, password);
    }

    private void showConnectionEditor(@Nullable SavedConnection existingConnection) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_connection_editor, binding.getRoot(), false);
        android.widget.TextView titleView = dialogView.findViewById(R.id.dialog_title);
        TextInputEditText hostInput = dialogView.findViewById(R.id.dialog_host_input);
        TextInputEditText portInput = dialogView.findViewById(R.id.dialog_port_input);
        TextInputEditText usernameInput = dialogView.findViewById(R.id.dialog_username_input);
        TextInputEditText passwordInput = dialogView.findViewById(R.id.dialog_password_input);
        MaterialButton connectButton = dialogView.findViewById(R.id.dialog_connect_button);
        MaterialButton cancelButton = dialogView.findViewById(R.id.dialog_cancel_button);
        MaterialButton saveButton = dialogView.findViewById(R.id.dialog_save_button);

        titleView.setText(existingConnection == null ? R.string.new_connection_title : R.string.edit_connection_title);
        saveButton.setText(existingConnection == null ? R.string.save_connection_action : R.string.update_connection_action);

        if (existingConnection != null) {
            hostInput.setText(existingConnection.host);
            portInput.setText(String.valueOf(existingConnection.port));
            usernameInput.setText(existingConnection.username);
            passwordInput.setText(existingConnection.password);
        } else {
            portInput.setText("22");
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            }
        });
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        saveButton.setOnClickListener(v -> {
            SshConnectionConfig config = buildConnectionConfig(
                valueOf(hostInput.getText()),
                valueOf(portInput.getText()),
                valueOf(usernameInput.getText()),
                valueOf(passwordInput.getText())
            );
            if (config == null) {
                return;
            }

            upsertSavedConnection(config, existingConnection != null ? existingConnection.id : null, true);
            dialog.dismiss();
        });
        connectButton.setOnClickListener(v -> {
            SshConnectionConfig config = buildConnectionConfig(
                valueOf(hostInput.getText()),
                valueOf(portInput.getText()),
                valueOf(usernameInput.getText()),
                valueOf(passwordInput.getText())
            );
            if (config == null) {
                return;
            }

            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.save_before_connect_title)
                .setMessage(R.string.save_before_connect_message)
                .setNegativeButton(R.string.connect_without_saving_action, (confirmDialog, which) -> {
                    dialog.dismiss();
                    connect(config, existingConnection != null ? existingConnection.id : selectedConnectionId);
                })
                .setNeutralButton(R.string.cancel_action, null)
                .setPositiveButton(R.string.save_and_connect_action, (confirmDialog, which) -> {
                    SavedConnection savedConnection = upsertSavedConnection(
                        config,
                        existingConnection != null ? existingConnection.id : null,
                        true
                    );
                    dialog.dismiss();
                    connect(savedConnection);
                })
                .show();
        });
        dialog.show();
    }

    private SavedConnection upsertSavedConnection(SshConnectionConfig config, @Nullable String preferredConnectionId, boolean showToast) {
        SavedConnection existingConnection = findMatchingConnection(config.getHost(), config.getPort(), config.getUsername());
        String connectionId = !TextUtils.isEmpty(preferredConnectionId)
            ? preferredConnectionId
            : existingConnection != null ? existingConnection.id : UUID.randomUUID().toString();
        SavedConnection savedConnection = new SavedConnection(
            connectionId,
            config.getHost(),
            config.getPort(),
            config.getUsername(),
            config.getPassword()
        );

        boolean updated = false;
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, connectionId)) {
                savedConnections.set(i, savedConnection);
                updated = true;
                break;
            }
        }

        if (!updated) {
            savedConnections.add(0, savedConnection);
        }

        selectedConnectionId = connectionId;
        persistSavedConnections();
        renderSavedConnections();
        if (showToast) {
            toast(getString(updated ? R.string.connection_updated : R.string.connection_saved));
        }
        return savedConnection;
    }

    private void restoreSavedConnections() {
        loadSavedConnections();
        migrateLegacyConnectionDraftIfNeeded();

        SavedConnection selectedConnection = findSavedConnectionById(preferences.getString(KEY_SELECTED_CONNECTION_ID, null));
        if (selectedConnection == null && !savedConnections.isEmpty()) {
            selectedConnection = savedConnections.get(0);
        }

        if (selectedConnection != null) {
            selectedConnectionId = selectedConnection.id;
        } else {
            selectedConnectionId = null;
        }

        persistSelectedConnectionId();
        renderSavedConnections();
    }

    private void loadSavedConnections() {
        savedConnections.clear();
        String savedConnectionsJson = preferences.getString(KEY_SAVED_CONNECTIONS, "[]");
        if (TextUtils.isEmpty(savedConnectionsJson)) {
            return;
        }

        try {
            JSONArray jsonArray = new JSONArray(savedConnectionsJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                SavedConnection savedConnection = SavedConnection.fromJson(jsonObject);
                if (savedConnection != null) {
                    savedConnections.add(savedConnection);
                }
            }
        } catch (JSONException e) {
            Log.w(INPUT_LOG_TAG, "Failed to load saved connections", e);
        }
    }

    private void migrateLegacyConnectionDraftIfNeeded() {
        if (!savedConnections.isEmpty()) {
            return;
        }

        String host = preferences.getString(KEY_HOST, "");
        String username = preferences.getString(KEY_USERNAME, "");
        String password = preferences.getString(KEY_PASSWORD, "");
        String portValue = preferences.getString(KEY_PORT, "22");
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            return;
        }

        int port = 22;
        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException ignored) {
        }

        SavedConnection legacyConnection = new SavedConnection(
            UUID.randomUUID().toString(),
            host,
            port,
            username,
            password
        );
        savedConnections.add(legacyConnection);
        selectedConnectionId = legacyConnection.id;
        persistSavedConnections();
    }

    private void persistSavedConnections() {
        JSONArray jsonArray = new JSONArray();
        for (SavedConnection savedConnection : savedConnections) {
            jsonArray.put(savedConnection.toJson());
        }

        preferences.edit()
            .putString(KEY_SAVED_CONNECTIONS, jsonArray.toString())
            .putString(KEY_SELECTED_CONNECTION_ID, selectedConnectionId)
            .apply();
    }

    private void persistSelectedConnectionId() {
        preferences.edit()
            .putString(KEY_SELECTED_CONNECTION_ID, selectedConnectionId)
            .apply();
    }

    private void renderSavedConnections() {
        binding.savedConnectionsContainer.removeAllViews();
        boolean isEmpty = savedConnections.isEmpty();
        binding.savedConnectionsEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.savedConnectionsContainer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (isEmpty) {
            return;
        }

        for (SavedConnection savedConnection : savedConnections) {
            View itemView = getLayoutInflater().inflate(R.layout.item_saved_connection, binding.savedConnectionsContainer, false);
            MaterialCardView cardView = itemView.findViewById(R.id.saved_connection_card);
            android.widget.TextView nameView = itemView.findViewById(R.id.saved_connection_name);
            android.widget.TextView detailsView = itemView.findViewById(R.id.saved_connection_details);
            MaterialButton editButton = itemView.findViewById(R.id.edit_saved_connection_button);
            MaterialButton connectButton = itemView.findViewById(R.id.connect_saved_connection_button);
            AppCompatImageButton deleteButton = itemView.findViewById(R.id.delete_saved_connection_button);

            boolean selected = TextUtils.equals(savedConnection.id, selectedConnectionId);
            cardView.setStrokeColor(ContextCompat.getColor(this, selected ? R.color.saved_connection_selected_stroke : R.color.drawer_field_stroke));
            cardView.setCardBackgroundColor(ContextCompat.getColor(this, selected ? R.color.saved_connection_selected_surface : R.color.drawer_field_surface));
            cardView.setStrokeWidth(dp(1));
            nameView.setText(savedConnection.getDisplayName());
            detailsView.setText(savedConnection.getDisplayDetails());

            cardView.setOnClickListener(v -> selectSavedConnection(savedConnection));
            editButton.setOnClickListener(v -> showConnectionEditor(savedConnection));
            connectButton.setOnClickListener(v -> connect(savedConnection));
            deleteButton.setOnClickListener(v -> deleteSavedConnection(savedConnection));
            binding.savedConnectionsContainer.addView(itemView);
        }
    }

    private void selectSavedConnection(SavedConnection savedConnection) {
        selectedConnectionId = savedConnection.id;
        persistSelectedConnectionId();
        renderSavedConnections();
    }

    private void deleteSavedConnection(SavedConnection savedConnection) {
        for (int i = 0; i < savedConnections.size(); i++) {
            if (TextUtils.equals(savedConnections.get(i).id, savedConnection.id)) {
                savedConnections.remove(i);
                break;
            }
        }

        if (TextUtils.equals(selectedConnectionId, savedConnection.id)) {
            if (savedConnections.isEmpty()) {
                selectedConnectionId = null;
            } else {
                selectedConnectionId = savedConnections.get(0).id;
            }
        }

        persistSavedConnections();
        renderSavedConnections();
        toast(getString(R.string.connection_deleted));
    }

    private SavedConnection findMatchingConnection(String host, int port, String username) {
        for (SavedConnection savedConnection : savedConnections) {
            if (savedConnection.port == port
                && TextUtils.equals(savedConnection.host, host)
                && TextUtils.equals(savedConnection.username, username)) {
                return savedConnection;
            }
        }
        return null;
    }

    private SavedConnection findSavedConnectionById(String connectionId) {
        if (TextUtils.isEmpty(connectionId)) {
            return null;
        }

        for (SavedConnection savedConnection : savedConnections) {
            if (TextUtils.equals(savedConnection.id, connectionId)) {
                return savedConnection;
            }
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void showPasteMenu(MotionEvent event) {
        View anchor = new View(this);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(1, 1);
        layoutParams.leftMargin = Math.max(0, Math.min(Math.round(event.getX()), Math.max(0, binding.terminalContainer.getWidth() - 1)));
        layoutParams.topMargin = Math.max(0, Math.min(Math.round(event.getY()), Math.max(0, binding.terminalContainer.getHeight() - 1)));
        binding.terminalContainer.addView(anchor, layoutParams);

        PopupMenu popupMenu = new PopupMenu(this, anchor, Gravity.NO_GRAVITY);
        popupMenu.getMenu().add(getString(R.string.paste_text)).setEnabled(hasClipboardText());
        popupMenu.setOnMenuItemClickListener(item -> {
            pasteFromClipboard();
            return true;
        });
        popupMenu.setOnDismissListener(menu -> binding.terminalContainer.removeView(anchor));
        popupMenu.show();
    }

    private boolean hasClipboardText() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        return clipboardManager != null && clipboardManager.hasPrimaryClip()
            && clipboardManager.getPrimaryClip() != null
            && clipboardManager.getPrimaryClip().getItemCount() > 0
            && !TextUtils.isEmpty(clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(this));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String valueOf(CharSequence text) {
        return text == null ? "" : text.toString().trim();
    }

    @Override
    public void onScreenUpdated(@NonNull SshTerminalSession session) {
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            terminalView.onScreenUpdated();
        }
    }

    @Override
    public void onSessionTitleChanged(@NonNull SshTerminalSession session, String title) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId()) && !TextUtils.isEmpty(title)) {
            binding.statusText.setText(title);
        }
    }

    @Override
    public void onConnected(@NonNull SshTerminalSession session) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            binding.statusText.setText(session.getDisplayTitle());
            updateConnectionActions(true);
            terminalView.post(terminalView::requestFocus);
        }
        setConnectionPanelExpanded(false);
        SshConnectionService.start(this);
    }

    @Override
    public void onDisconnected(@NonNull SshTerminalSession session, String message) {
        renderTabs();
        if (TextUtils.equals(activeSessionId, session.getSessionId())) {
            binding.statusText.setText(message);
            updateConnectionActions(false);
        }
        if (!SshSessionRepository.hasConnectedSessions()) {
            SshConnectionService.stop(this);
        }
    }

    @Override
    public void copyToClipboard(String text) {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("terminal-copy", text));
            toast(getString(R.string.clipboard_copied));
        }
    }

    @Override
    public void pasteFromClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        CharSequence pasteText = clipData.getItemAt(0).coerceToText(this);
        if (TextUtils.isEmpty(pasteText)) {
            toast(getString(R.string.clipboard_empty));
            return;
        }

        if (sshTerminalSession != null) {
            sshTerminalSession.pasteText(pasteText.toString());
        }
    }

    @Override
    public float onScale(float scale) {
        float clampedScale = Math.max(
            MIN_TERMINAL_TEXT_SIZE_SP / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP,
            Math.min(scale, MAX_TERMINAL_TEXT_SIZE_SP / (float) DEFAULT_TERMINAL_TEXT_SIZE_SP)
        );
        terminalScaleFactor = clampedScale;
        setTerminalFontSize(Math.round(DEFAULT_TERMINAL_TEXT_SIZE_SP * clampedScale));
        return terminalScaleFactor;
    }

    @Override
    public void onSingleTapUp(MotionEvent e) {
        terminalView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    @Override
    public boolean shouldBackButtonBeMappedToEscape() {
        return false;
    }

    @Override
    public boolean shouldEnforceCharBasedInput() {
        return false;
    }

    @Override
    public boolean shouldUseCtrlSpaceWorkaround() {
        return false;
    }

    @Override
    public boolean isTerminalViewSelected() {
        return terminalView != null && terminalView.hasFocus();
    }

    @Override
    public void copyModeChanged(boolean copyMode) {
        // No-op.
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent e, com.example.androidterminal.ssh.SshTerminalSession session) {
        if (session == null) {
            return false;
        }
        Log.d(INPUT_LOG_TAG, "TerminalViewClient.onKeyDown keyCode=" + keyCode + " ctrl=" + e.isCtrlPressed() + " alt=" + e.isAltPressed());
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            session.sendEscape();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent e) {
        return keyCode == KeyEvent.KEYCODE_ESCAPE;
    }

    @Override
    public boolean onLongPress(MotionEvent event) {
        return false;
    }

    @Override
    public boolean readControlKey() {
        return false;
    }

    @Override
    public boolean readAltKey() {
        return false;
    }

    @Override
    public boolean readShiftKey() {
        return false;
    }

    @Override
    public boolean readFnKey() {
        return false;
    }

    @Override
    public boolean onCodePoint(int codePoint, boolean ctrlDown, com.example.androidterminal.ssh.SshTerminalSession session) {
        Log.d(INPUT_LOG_TAG, "TerminalViewClient.onCodePoint codePoint=" + codePoint + " ctrl=" + ctrlDown);
        return false;
    }

    @Override
    public void onEmulatorSet() {
        terminalView.setTerminalCursorBlinkerState(true, true);
        terminalView.onScreenUpdated();
    }

    @Override
    public void onMouseSecondaryClick(MotionEvent event, com.example.androidterminal.ssh.SshTerminalSession session) {
        if (terminalView != null && terminalView.isSelectingText()) {
            terminalView.stopTextSelectionMode(true);
        }
        showPasteMenu(event);
    }

    @Override
    public void logError(String tag, String message) {
    }

    @Override
    public void logWarn(String tag, String message) {
    }

    @Override
    public void logInfo(String tag, String message) {
    }

    @Override
    public void logDebug(String tag, String message) {
    }

    @Override
    public void logVerbose(String tag, String message) {
    }

    @Override
    public void logStackTraceWithMessage(String tag, String message, Exception e) {
    }

    @Override
    public void logStackTrace(String tag, Exception e) {
    }

    @Override
    public void onTextChanged(@NonNull TerminalSession changedSession) {
    }

    @Override
    public void onTitleChanged(@NonNull TerminalSession changedSession) {
    }

    @Override
    public void onSessionFinished(@NonNull TerminalSession finishedSession) {
    }

    @Override
    public void onCopyTextToClipboard(@NonNull TerminalSession session, String text) {
    }

    @Override
    public void onPasteTextFromClipboard(TerminalSession session) {
    }

    @Override
    public void onBell(@NonNull TerminalSession session) {
    }

    @Override
    public void onColorsChanged(@NonNull TerminalSession session) {
    }

    @Override
    public void onTerminalCursorStateChange(boolean state) {
        if (terminalView != null) {
            terminalView.setTerminalCursorBlinkerState(state, true);
        }
    }

    public void setTerminalShellPid(@NonNull TerminalSession session, int pid) {
    }

    @Override
    public Integer getTerminalCursorStyle() {
        return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
    }

    private static final class SavedConnection {
        private static final String JSON_ID = "id";
        private static final String JSON_HOST = "host";
        private static final String JSON_PORT = "port";
        private static final String JSON_USERNAME = "username";
        private static final String JSON_PASSWORD = "password";

        private final String id;
        private final String host;
        private final int port;
        private final String username;
        private final String password;

        private SavedConnection(String id, String host, int port, String username, String password) {
            this.id = id;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        private JSONObject toJson() {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put(JSON_ID, id);
                jsonObject.put(JSON_HOST, host);
                jsonObject.put(JSON_PORT, port);
                jsonObject.put(JSON_USERNAME, username);
                jsonObject.put(JSON_PASSWORD, password);
            } catch (JSONException ignored) {
            }
            return jsonObject;
        }

        private static SavedConnection fromJson(JSONObject jsonObject) {
            if (jsonObject == null) {
                return null;
            }

            String id = jsonObject.optString(JSON_ID, "");
            String host = jsonObject.optString(JSON_HOST, "");
            String username = jsonObject.optString(JSON_USERNAME, "");
            String password = jsonObject.optString(JSON_PASSWORD, "");
            int port = jsonObject.optInt(JSON_PORT, 22);
            if (TextUtils.isEmpty(id) || TextUtils.isEmpty(host) || TextUtils.isEmpty(username)) {
                return null;
            }
            return new SavedConnection(id, host, port, username, password);
        }

        private String getDisplayName() {
            return username + "@" + host;
        }

        private String getDisplayDetails() {
            return host + ":" + port;
        }

        private SshConnectionConfig toConfig() {
            return new SshConnectionConfig(host, port, username, password);
        }
    }
}
