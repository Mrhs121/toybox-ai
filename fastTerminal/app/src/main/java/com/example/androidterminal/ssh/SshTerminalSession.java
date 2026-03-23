package com.example.androidterminal.ssh;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalOutput;
import com.termux.terminal.TerminalSessionClient;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class SshTerminalSession extends TerminalOutput {

    private static final String SSH_LOG_TAG = "AndroidTerminalSSH";

    public interface Listener {
        void onScreenUpdated();

        void onSessionTitleChanged(String title);

        void onConnected();

        void onDisconnected(String message);

        void copyToClipboard(String text);

        void pasteFromClipboard();
    }

    private static final int DEFAULT_COLUMNS = 80;
    private static final int DEFAULT_ROWS = 24;
    private static final int TRANSCRIPT_ROWS = 10_000;

    private final TerminalSessionClientProxy terminalSessionClientProxy = new TerminalSessionClientProxy();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ssh-terminal-connect");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "ssh-terminal-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final Object ioLock = new Object();
    private final byte[] utf8InputBuffer = new byte[5];

    private volatile SSHClient sshClient;
    private volatile Session sshSession;
    private volatile Session.Shell shellChannel;
    private volatile InputStream remoteInput;
    private volatile OutputStream remoteOutput;
    private volatile boolean disconnectNotified;
    private volatile Listener listener;

    private TerminalEmulator emulator;
    private Thread readerThread;
    private int columns = DEFAULT_COLUMNS;
    private int rows = DEFAULT_ROWS;

    public SshTerminalSession(@NonNull TerminalSessionClient terminalSessionClient, @NonNull Listener listener) {
        attachUi(terminalSessionClient, listener);
        emulator = new TerminalEmulator(this, DEFAULT_COLUMNS, DEFAULT_ROWS, TRANSCRIPT_ROWS, terminalSessionClientProxy);
        appendLocalMessage("Ready. Fill in SSH info above and connect.\r\n");
    }

    public void attachUi(@NonNull TerminalSessionClient terminalSessionClient, @NonNull Listener listener) {
        terminalSessionClientProxy.setDelegate(terminalSessionClient);
        this.listener = listener;
    }

    public void detachUi() {
        listener = null;
        terminalSessionClientProxy.setDelegate(null);
    }

    public synchronized void updateSize(int columns, int rows) {
        this.columns = columns;
        this.rows = rows;
        if (emulator.mColumns != columns || emulator.mRows != rows) {
            emulator.resize(columns, rows);
        }

        Session.Shell channel = shellChannel;
        if (channel != null && channel.isOpen()) {
            queueWindowResize(columns, rows);
        }
    }

    public synchronized TerminalEmulator getEmulator() {
        return emulator;
    }

    public synchronized boolean isConnected() {
        SSHClient client = sshClient;
        Session.Shell channel = shellChannel;
        return client != null && client.isConnected() && channel != null && channel.isOpen();
    }

    public void connect(@NonNull SshConnectionConfig config) {
        Log.d(SSH_LOG_TAG, "connect() host=" + config.getHost() + " port=" + config.getPort() + " user=" + config.getUsername());
        disconnect("Reconnecting");
        disconnectNotified = false;
        connectionExecutor.execute(() -> connectInternal(config));
    }

    public void disconnect() {
        disconnect("Disconnected");
    }

    public void disconnect(String message) {
        Log.d(SSH_LOG_TAG, "disconnect() message=" + message + " callerThread=" + Thread.currentThread().getName());
        closeRemoteState();
        notifyDisconnected(message);
    }

    public void sendEscape() {
        Log.d(SSH_LOG_TAG, "sendEscape()");
        writeCodePoint(false, 27);
    }

    public void pasteText(String text) {
        TerminalEmulator terminalEmulator = emulator;
        if (terminalEmulator != null && text != null && !text.isEmpty()) {
            terminalEmulator.paste(text);
        }
    }

    private void connectInternal(SshConnectionConfig config) {
        appendLocalMessage(String.format("Connecting to %s@%s:%d ...\r\n", config.getUsername(), config.getHost(), config.getPort()));
        try {
            ensureBouncyCastleProvider();
            SSHClient client = new SSHClient();
            client.addHostKeyVerifier(new PromiscuousVerifier());
            client.connect(config.getHost(), config.getPort());
            client.authPassword(config.getUsername(), config.getPassword());

            Session session = client.startSession();
            session.allocatePTY("xterm-256color", columns, rows, 0, 0, Collections.emptyMap());
            Session.Shell shell = session.startShell();
            InputStream inputStream = shell.getInputStream();
            OutputStream outputStream = shell.getOutputStream();

            synchronized (ioLock) {
                sshClient = client;
                sshSession = session;
                shellChannel = shell;
                remoteInput = inputStream;
                remoteOutput = outputStream;
            }

            Log.d(SSH_LOG_TAG, "SSH shell connected: outputStream=" + (outputStream != null) + " inputStream=" + (inputStream != null));

            mainHandler.post(() -> {
                Listener currentListener = listener;
                if (currentListener != null) {
                    currentListener.onConnected();
                }
            });
            startReaderLoop(inputStream);
        } catch (Exception e) {
            Log.e(SSH_LOG_TAG, "SSH connect failed", e);
            appendLocalMessage(String.format("SSH error: %s\r\n", e.getMessage()));
            notifyDisconnected("Connection failed");
        }
    }

    private void startReaderLoop(InputStream inputStream) {
        readerThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            try {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    byte[] chunk = new byte[read];
                    System.arraycopy(buffer, 0, chunk, 0, read);
                    Log.d(SSH_LOG_TAG, "read remote bytes=" + read + " preview=" + previewBytes(chunk, read));
                    mainHandler.post(() -> {
                        TerminalEmulator terminalEmulator = emulator;
                        if (terminalEmulator != null) {
                            terminalEmulator.append(chunk, chunk.length);
                            Listener currentListener = listener;
                            if (currentListener != null) {
                                currentListener.onScreenUpdated();
                            }
                        }
                    });
                }
                Log.d(SSH_LOG_TAG, "reader loop reached EOF");
            } catch (Exception e) {
                Log.e(SSH_LOG_TAG, "reader loop failed", e);
            } finally {
                Session.Shell channel = shellChannel;
                SSHClient client = sshClient;
                Log.d(
                    SSH_LOG_TAG,
                    "reader loop state open=" + (channel != null && channel.isOpen())
                        + " eof=" + (channel != null && channel.isEOF())
                        + " sessionConnected=" + (client != null && client.isConnected())
                );
                Log.d(SSH_LOG_TAG, "reader loop closing session");
                closeRemoteState();
                notifyDisconnected("Session closed");
            }
        }, "ssh-terminal-reader");
        readerThread.start();
    }

    private void queueWindowResize(int columns, int rows) {
        writerExecutor.execute(() -> {
            synchronized (ioLock) {
                Session.Shell channel = shellChannel;
                if (channel == null || !channel.isOpen()) {
                    return;
                }
                try {
                    Log.d(SSH_LOG_TAG, "changeWindowDimensions cols=" + columns + " rows=" + rows
                        + " workerThread=" + Thread.currentThread().getName());
                    channel.changeWindowDimensions(columns, rows, 0, 0);
                } catch (Exception e) {
                    Log.w(SSH_LOG_TAG, "changeWindowDimensions failed", e);
                }
            }
        });
    }

    private static synchronized void ensureBouncyCastleProvider() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (!(provider instanceof BouncyCastleProvider)) {
            if (provider != null) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
            }
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
        }
        Provider installed = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        Log.d(SSH_LOG_TAG, "using provider BC=" + (installed != null ? installed.getClass().getName() : "null"));
    }

    private static String previewBytes(byte[] data, int count) {
        int limit = Math.min(count, 64);
        StringBuilder builder = new StringBuilder(limit + 16);
        for (int i = 0; i < limit; i++) {
            int value = data[i] & 0xFF;
            if (value == '\r') {
                builder.append("\\r");
            } else if (value == '\n') {
                builder.append("\\n");
            } else if (value >= 32 && value < 127) {
                builder.append((char) value);
            } else {
                builder.append(String.format("\\x%02X", value));
            }
        }
        if (count > limit) {
            builder.append("...");
        }
        return builder.toString();
    }

    private void appendLocalMessage(String message) {
        mainHandler.post(() -> {
            TerminalEmulator terminalEmulator = emulator;
            if (terminalEmulator != null) {
                byte[] data = message.getBytes(StandardCharsets.UTF_8);
                terminalEmulator.append(data, data.length);
                Listener currentListener = listener;
                if (currentListener != null) {
                    currentListener.onScreenUpdated();
                }
            }
        });
    }

    private void notifyDisconnected(String message) {
        if (disconnectNotified) {
            return;
        }
        disconnectNotified = true;
        Log.d(SSH_LOG_TAG, "notifyDisconnected message=" + message);
        mainHandler.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onDisconnected(message);
            }
        });
    }

    private void closeRemoteState() {
        synchronized (ioLock) {
            Log.d(SSH_LOG_TAG,
                "closeRemoteState input=" + (remoteInput != null)
                    + " output=" + (remoteOutput != null)
                    + " channel=" + (shellChannel != null)
                    + " session=" + (sshSession != null)
                    + " client=" + (sshClient != null)
                    + " callerThread=" + Thread.currentThread().getName());
            try {
                if (remoteInput != null) {
                    remoteInput.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (remoteOutput != null) {
                    remoteOutput.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (shellChannel != null) {
                    shellChannel.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (sshSession != null) {
                    sshSession.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (sshClient != null) {
                    sshClient.disconnect();
                    sshClient.close();
                }
            } catch (Exception ignored) {
            }

            remoteInput = null;
            remoteOutput = null;
            shellChannel = null;
            sshSession = null;
            sshClient = null;
        }
    }

    @Override
    public void write(byte[] data, int offset, int count) {
        byte[] payload = new byte[count];
        System.arraycopy(data, offset, payload, 0, count);
        Log.d(SSH_LOG_TAG, "queue write bytes=" + count + " callerThread=" + Thread.currentThread().getName());
        writerExecutor.execute(() -> {
            Log.d(SSH_LOG_TAG, "run write bytes=" + count + " workerThread=" + Thread.currentThread().getName());
            synchronized (ioLock) {
                try {
                    if (remoteOutput != null) {
                        Log.d(SSH_LOG_TAG, "write remote bytes=" + count + " connected=" + isConnected());
                        remoteOutput.write(payload, 0, count);
                        remoteOutput.flush();
                    } else {
                        Log.w(SSH_LOG_TAG, "write skipped because remoteOutput is null");
                    }
                } catch (Exception e) {
                    Log.e(SSH_LOG_TAG, "write failed", e);
                    notifyDisconnected("Write failed");
                }
            }
        });
    }

    public void writeCodePoint(boolean prependEscape, int codePoint) {
        Log.d(SSH_LOG_TAG, "writeCodePoint prependEscape=" + prependEscape + " codePoint=" + codePoint);
        if (codePoint > 1114111 || (codePoint >= 0xD800 && codePoint <= 0xDFFF)) {
            throw new IllegalArgumentException("Invalid code point: " + codePoint);
        }

        int bufferPosition = 0;
        if (prependEscape) {
            utf8InputBuffer[bufferPosition++] = 27;
        }

        if (codePoint <= 0b1111111) {
            utf8InputBuffer[bufferPosition++] = (byte) codePoint;
        } else if (codePoint <= 0b11111111111) {
            utf8InputBuffer[bufferPosition++] = (byte) (0b11000000 | (codePoint >> 6));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else if (codePoint <= 0b1111111111111111) {
            utf8InputBuffer[bufferPosition++] = (byte) (0b11100000 | (codePoint >> 12));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        } else {
            utf8InputBuffer[bufferPosition++] = (byte) (0b11110000 | (codePoint >> 18));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b111111));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b111111));
            utf8InputBuffer[bufferPosition++] = (byte) (0b10000000 | (codePoint & 0b111111));
        }
        write(utf8InputBuffer, 0, bufferPosition);
    }

    @Override
    public void titleChanged(String oldTitle, String newTitle) {
        mainHandler.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onSessionTitleChanged(newTitle);
            }
        });
    }

    @Override
    public void onCopyTextToClipboard(String text) {
        mainHandler.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.copyToClipboard(text);
            }
        });
    }

    @Override
    public void onPasteTextFromClipboard() {
        mainHandler.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.pasteFromClipboard();
            }
        });
    }

    @Override
    public void onBell() {
        // No-op for now.
    }

    @Override
    public void onColorsChanged() {
        mainHandler.post(() -> {
            Listener currentListener = listener;
            if (currentListener != null) {
                currentListener.onScreenUpdated();
            }
        });
    }

    private static final class TerminalSessionClientProxy implements TerminalSessionClient {

        private volatile TerminalSessionClient delegate;

        void setDelegate(TerminalSessionClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onTextChanged(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onTextChanged(session);
            }
        }

        @Override
        public void onTitleChanged(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onTitleChanged(session);
            }
        }

        @Override
        public void onSessionFinished(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onSessionFinished(session);
            }
        }

        @Override
        public void onCopyTextToClipboard(com.termux.terminal.TerminalSession session, String text) {
            if (delegate != null) {
                delegate.onCopyTextToClipboard(session, text);
            }
        }

        @Override
        public void onPasteTextFromClipboard(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onPasteTextFromClipboard(session);
            }
        }

        @Override
        public void onBell(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onBell(session);
            }
        }

        @Override
        public void onColorsChanged(com.termux.terminal.TerminalSession session) {
            if (delegate != null) {
                delegate.onColorsChanged(session);
            }
        }

        @Override
        public void onTerminalCursorStateChange(boolean state) {
            if (delegate != null) {
                delegate.onTerminalCursorStateChange(state);
            }
        }

        @Override
        public Integer getTerminalCursorStyle() {
            if (delegate != null) {
                return delegate.getTerminalCursorStyle();
            }
            return TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK;
        }

        @Override
        public void logError(String tag, String message) {
            if (delegate != null) {
                delegate.logError(tag, message);
            }
        }

        @Override
        public void logWarn(String tag, String message) {
            if (delegate != null) {
                delegate.logWarn(tag, message);
            }
        }

        @Override
        public void logInfo(String tag, String message) {
            if (delegate != null) {
                delegate.logInfo(tag, message);
            }
        }

        @Override
        public void logDebug(String tag, String message) {
            if (delegate != null) {
                delegate.logDebug(tag, message);
            }
        }

        @Override
        public void logVerbose(String tag, String message) {
            if (delegate != null) {
                delegate.logVerbose(tag, message);
            }
        }

        @Override
        public void logStackTraceWithMessage(String tag, String message, Exception e) {
            if (delegate != null) {
                delegate.logStackTraceWithMessage(tag, message, e);
            }
        }

        @Override
        public void logStackTrace(String tag, Exception e) {
            if (delegate != null) {
                delegate.logStackTrace(tag, e);
            }
        }
    }
}
