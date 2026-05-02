package com.example.androidterminal.sftp;

public class FileEntry {
    public final String name;
    public final String path;
    public final boolean isDirectory;
    public final long size;
    public final String permissions;
    public final long modTime;

    public FileEntry(String name, String path, boolean isDirectory, long size, String permissions, long modTime) {
        this.name = name;
        this.path = path;
        this.isDirectory = isDirectory;
        this.size = size;
        this.permissions = permissions;
        this.modTime = modTime;
    }

    public String getDisplaySize() {
        if (isDirectory) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024L * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
