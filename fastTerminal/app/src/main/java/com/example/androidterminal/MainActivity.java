package com.example.androidterminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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
import androidx.appcompat.app.AppCompatActivity;

import com.example.androidterminal.databinding.ActivityMainBinding;
import com.example.androidterminal.ssh.SshConnectionConfig;
import com.example.androidterminal.ssh.SshConnectionService;
import com.example.androidterminal.ssh.SshSessionRepository;
import com.example.androidterminal.ssh.SshTerminalSession;
import com.example.androidterminal.terminalview.TerminalView;
import com.example.androidterminal.terminalview.TerminalViewClient;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

public final class MainActivity extends AppCompatActivity implements TerminalViewClient, TerminalSessionClient, SshTerminalSession.Listener {

    private static final String INPUT_LOG_TAG = "AndroidTerminalInput";
    private static final String PREFS = "ssh-terminal-prefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final int DEFAULT_TERMINAL_TEXT_SIZE_SP = 15;
    private static final int MIN_TERMINAL_TEXT_SIZE_SP = 11;
    private static final int MAX_TERMINAL_TEXT_SIZE_SP = 28;

    private ActivityMainBinding binding;
    private SharedPreferences preferences;
    private TerminalView terminalView;
    private SshTerminalSession sshTerminalSession;
    private float terminalScaleFactor = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        restoreConnectionDraft();
        setupTerminal();
        bindActions();
        binding.statusText.setText(R.string.status_idle);
    }

    @Override
    protected void onDestroy() {
        if (sshTerminalSession != null) {
            if (isFinishing()) {
                SshSessionRepository.disconnectAndClear(sshTerminalSession, "Disconnected");
            } else {
                SshSessionRepository.detachUi(sshTerminalSession);
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
        attachCurrentSession();
    }

    private void bindActions() {
        binding.showConnectionButton.setFocusable(false);
        binding.showConnectionButton.setFocusableInTouchMode(false);
        binding.connectButton.setOnClickListener(v -> connect());
        binding.disconnectButton.setOnClickListener(v -> {
            if (sshTerminalSession != null) {
                sshTerminalSession.disconnect();
            }
        });
        binding.showConnectionButton.setOnClickListener(v -> setConnectionPanelExpanded(true));
    }

    private void connect() {
        String host = valueOf(binding.hostInput.getText());
        String username = valueOf(binding.usernameInput.getText());
        String password = valueOf(binding.passwordInput.getText());
        String portValue = valueOf(binding.portInput.getText());

        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            toast(getString(R.string.missing_connection_info));
            return;
        }

        int port = 22;
        if (!TextUtils.isEmpty(portValue)) {
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException e) {
                toast(getString(R.string.invalid_port));
                return;
            }
        }

        persistConnectionDraft(host, String.valueOf(port), username);
        binding.statusText.setText(R.string.status_connecting);
        resetSession();
        sshTerminalSession.connect(new SshConnectionConfig(host, port, username, password));
        terminalView.requestFocus();
    }

    private void setConnectionPanelExpanded(boolean expanded) {
        binding.connectionCard.setVisibility(expanded ? View.VISIBLE : View.GONE);
        binding.showConnectionButton.setVisibility(expanded ? View.GONE : View.VISIBLE);
        if (!expanded && terminalView != null) {
            terminalView.post(terminalView::requestFocus);
        }
    }

    private boolean shouldRoutePhysicalKeyboardToTerminal() {
        if (terminalView == null) {
            return false;
        }

        if (binding.connectionCard.getVisibility() != View.VISIBLE) {
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

    private void resetSession() {
        sshTerminalSession = SshSessionRepository.replace(this, this);
        terminalView.attachSession(sshTerminalSession);
        syncUiWithSession();
    }

    private void attachCurrentSession() {
        sshTerminalSession = SshSessionRepository.getOrCreate(this, this);
        terminalView.attachSession(sshTerminalSession);
        syncUiWithSession();
    }

    private void syncUiWithSession() {
        if (sshTerminalSession != null && sshTerminalSession.isConnected()) {
            binding.statusText.setText(R.string.status_connected);
            setConnectionPanelExpanded(false);
            SshConnectionService.start(this);
        } else {
            binding.statusText.setText(R.string.status_idle);
            SshConnectionService.stop(this);
        }
    }

    private void persistConnectionDraft(String host, String port, String username) {
        preferences.edit()
            .putString(KEY_HOST, host)
            .putString(KEY_PORT, port)
            .putString(KEY_USERNAME, username)
            .apply();
    }

    private void restoreConnectionDraft() {
        binding.hostInput.setText(preferences.getString(KEY_HOST, ""));
        binding.portInput.setText(preferences.getString(KEY_PORT, "22"));
        binding.usernameInput.setText(preferences.getString(KEY_USERNAME, ""));
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
    public void onScreenUpdated() {
        terminalView.onScreenUpdated();
    }

    @Override
    public void onSessionTitleChanged(String title) {
        if (!TextUtils.isEmpty(title)) {
            binding.statusText.setText(title);
        }
    }

    @Override
    public void onConnected() {
        binding.statusText.setText(R.string.status_connected);
        setConnectionPanelExpanded(false);
        SshConnectionService.start(this);
        terminalView.post(terminalView::requestFocus);
    }

    @Override
    public void onDisconnected(String message) {
        binding.statusText.setText(message);
        SshConnectionService.stop(this);
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
}
