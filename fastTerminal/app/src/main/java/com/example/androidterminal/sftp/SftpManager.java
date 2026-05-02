package com.example.androidterminal.sftp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.androidterminal.ssh.SshTerminalSession;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SftpManager {

    private static final String TAG = "SftpManager";

    public interface Callback<T> {
        void onResult(T result, Exception error);
    }

    public interface ProgressCallback {
        void onProgress(long bytesTransferred, long totalBytes);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "sftp-ops");
        t.setDaemon(true);
        return t;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void listFiles(SshTerminalSession session, String path, Callback<List<FileEntry>> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                List<RemoteResourceInfo> entries = sftp.ls(path);
                List<FileEntry> result = new ArrayList<>();
                for (RemoteResourceInfo info : entries) {
                    String name = info.getName();
                    if (".".equals(name)) continue;
                    boolean isDir = info.isDirectory();
                    long size = isDir ? 0 : info.getAttributes().getSize();
                    String perms = info.getAttributes().getMode() != null
                        ? info.getAttributes().getMode().toString() : "";
                    long modTime = info.getAttributes().getMtime() * 1000L;
                    String fullPath = path.endsWith("/") ? path + name : path + "/" + name;
                    result.add(new FileEntry(name, fullPath, isDir, size, perms, modTime));
                }
                Collections.sort(result, (a, b) -> {
                    if (a.isDirectory != b.isDirectory) return a.isDirectory ? -1 : 1;
                    return a.name.compareToIgnoreCase(b.name);
                });
                postResult(callback, result, null);
            } catch (Exception e) {
                Log.e(TAG, "listFiles failed", e);
                postResult(callback, null, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    public void mkdir(SshTerminalSession session, String path, Callback<Boolean> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                sftp.mkdir(path);
                postResult(callback, true, null);
            } catch (Exception e) {
                Log.e(TAG, "mkdir failed", e);
                postResult(callback, false, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    public void delete(SshTerminalSession session, String path, boolean isDirectory, Callback<Boolean> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                if (isDirectory) {
                    sftp.rmdir(path);
                } else {
                    sftp.rm(path);
                }
                postResult(callback, true, null);
            } catch (Exception e) {
                Log.e(TAG, "delete failed", e);
                postResult(callback, false, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    public void rename(SshTerminalSession session, String oldPath, String newPath, Callback<Boolean> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                sftp.rename(oldPath, newPath);
                postResult(callback, true, null);
            } catch (Exception e) {
                Log.e(TAG, "rename failed", e);
                postResult(callback, false, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    public void download(SshTerminalSession session, String remotePath, File localFile, ProgressCallback progress, Callback<File> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                localFile.getParentFile().mkdirs();
                FileAttributes attrs = sftp.stat(remotePath);
                long totalSize = attrs.getSize();
                net.schmizz.sshj.sftp.RemoteFile rf = sftp.open(remotePath);
                try (InputStream in = rf.new ReadAheadRemoteFileInputStream(16);
                     FileOutputStream out = new FileOutputStream(localFile)) {
                    byte[] buf = new byte[32768];
                    long transferred = 0;
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        out.write(buf, 0, n);
                        transferred += n;
                        final long t = transferred;
                        if (progress != null) {
                            mainHandler.post(() -> progress.onProgress(t, totalSize));
                        }
                    }
                }
                postResult(callback, localFile, null);
            } catch (Exception e) {
                Log.e(TAG, "download failed", e);
                postResult(callback, null, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    public void upload(SshTerminalSession session, File localFile, String remotePath, Callback<Boolean> callback) {
        executor.execute(() -> {
            SFTPClient sftp = null;
            try {
                sftp = openSftp(session);
                sftp.put(localFile.getAbsolutePath(), remotePath);
                postResult(callback, true, null);
            } catch (Exception e) {
                Log.e(TAG, "upload failed", e);
                postResult(callback, false, e);
            } finally {
                closeSftp(sftp);
            }
        });
    }

    private SFTPClient openSftp(SshTerminalSession session) throws IOException {
        SSHClient client = session.getSshClient();
        if (client == null || !client.isConnected()) {
            throw new IOException("SSH not connected");
        }
        return client.newSFTPClient();
    }

    private void closeSftp(SFTPClient sftp) {
        if (sftp != null) {
            try {
                sftp.close();
            } catch (IOException ignored) {
            }
        }
    }

    private <T> void postResult(Callback<T> callback, T result, Exception error) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResult(result, error));
        }
    }
}
