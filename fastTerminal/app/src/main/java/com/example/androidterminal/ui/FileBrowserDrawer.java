package com.example.androidterminal.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidterminal.R;
import com.example.androidterminal.sftp.FileEntry;
import com.example.androidterminal.sftp.SftpManager;
import com.example.androidterminal.ssh.SshTerminalSession;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileBrowserDrawer {

    public interface UploadCallback {
        void onPickFile();
    }

    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final View panel;
    private final TextView pathView;
    private final RecyclerView listView;
    private final TextView emptyView;
    private final ProgressBar loadingView;
    private final SftpManager sftpManager = new SftpManager();

    private final List<FileEntry> entries = new ArrayList<>();
    private FileListAdapter adapter;
    private String currentPath = "/";
    private SshTerminalSession session;
    private UploadCallback uploadCallback;

    public FileBrowserDrawer(Activity activity, DrawerLayout drawerLayout, View panel) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.panel = panel;
        this.pathView = panel.findViewById(R.id.file_browser_path);
        this.listView = panel.findViewById(R.id.file_browser_list);
        this.emptyView = panel.findViewById(R.id.file_browser_empty);
        this.loadingView = panel.findViewById(R.id.file_browser_loading);

        adapter = new FileListAdapter();
        listView.setLayoutManager(new LinearLayoutManager(activity));
        listView.setAdapter(adapter);

        panel.findViewById(R.id.file_browser_refresh).setOnClickListener(v -> refresh());
        panel.findViewById(R.id.file_browser_upload).setOnClickListener(v -> {
            if (uploadCallback != null) uploadCallback.onPickFile();
        });
        panel.findViewById(R.id.file_browser_new_folder).setOnClickListener(v -> showNewFolderDialog());
    }

    public void setSession(SshTerminalSession session) {
        if (this.session != session) {
            this.session = session;
            currentPath = "/";
        }
    }

    public void setUploadCallback(UploadCallback callback) {
        this.uploadCallback = callback;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void open() {
        drawerLayout.openDrawer(panel);
        if (session != null && session.isConnected()) {
            navigateTo(currentPath);
        }
    }

    public void close() {
        drawerLayout.closeDrawer(panel);
    }

    public boolean isOpen() {
        return drawerLayout.isDrawerOpen(panel);
    }

    public void navigateTo(String path) {
        currentPath = path;
        pathView.setText(path);
        showLoading(true);

        sftpManager.listFiles(session, path, (result, error) -> {
            showLoading(false);
            if (error != null) {
                Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                return;
            }
            entries.clear();
            if (!"/".equals(currentPath)) {
                String parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                if (parentPath.isEmpty()) parentPath = "/";
                entries.add(new FileEntry("..", parentPath, true, 0, "", 0));
            }
            if (result != null) {
                entries.addAll(result);
            }
            adapter.notifyDataSetChanged();
            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            listView.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    public void refresh() {
        navigateTo(currentPath);
    }

    public void uploadFile(File localFile) {
        String remotePath = currentPath.endsWith("/")
            ? currentPath + localFile.getName()
            : currentPath + "/" + localFile.getName();
        Toast.makeText(activity, "Uploading " + localFile.getName() + "...", Toast.LENGTH_SHORT).show();
        sftpManager.upload(session, localFile, remotePath, (result, error) -> {
            if (error != null) {
                Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, R.string.uploaded_success, Toast.LENGTH_SHORT).show();
                refresh();
            }
        });
    }

    private void showLoading(boolean show) {
        loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        listView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void onItemClick(FileEntry entry) {
        if (entry.isDirectory) {
            navigateTo(entry.path);
        } else {
            showFileActionMenu(entry);
        }
    }

    private void onItemLongClick(FileEntry entry) {
        showContextMenu(entry);
    }

    private void showFileActionMenu(FileEntry entry) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(entry.name)
            .setItems(new String[]{
                activity.getString(R.string.download),
                activity.getString(R.string.rename_title),
                activity.getString(R.string.delete_confirm_title)
            }, (dialog, which) -> {
                switch (which) {
                    case 0: downloadFile(entry); break;
                    case 1: showRenameDialog(entry); break;
                    case 2: showDeleteConfirm(entry); break;
                }
            })
            .show();
    }

    private void showContextMenu(FileEntry entry) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(entry.name)
            .setItems(new String[]{
                activity.getString(R.string.rename_title),
                activity.getString(R.string.delete_confirm_title)
            }, (dialog, which) -> {
                if (which == 0) showRenameDialog(entry);
                else showDeleteConfirm(entry);
            })
            .show();
    }

    private void downloadFile(FileEntry entry) {
        File downloadDir = new File(activity.getExternalFilesDir(null), "downloads");
        File localFile = new File(downloadDir, entry.name);
        Toast.makeText(activity, "Downloading " + entry.name + "...", Toast.LENGTH_SHORT).show();
        sftpManager.download(session, entry.path, localFile, (result, error) -> {
            if (error != null) {
                Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, activity.getString(R.string.downloaded_to, result.getAbsolutePath()), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showRenameDialog(FileEntry entry) {
        EditText input = new EditText(activity);
        input.setText(entry.name);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSelectAllOnFocus(true);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.rename_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String newName = input.getText().toString().trim();
                if (!newName.isEmpty() && !newName.equals(entry.name)) {
                    String parentPath = entry.path.substring(0, entry.path.lastIndexOf('/'));
                    String newPath = parentPath + "/" + newName;
                    sftpManager.rename(session, entry.path, newPath, (result, error) -> {
                        if (error != null) {
                            Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                        } else {
                            refresh();
                        }
                    });
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showDeleteConfirm(FileEntry entry) {
        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(activity.getString(R.string.delete_confirm_message, entry.name))
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                sftpManager.delete(session, entry.path, entry.isDirectory, (result, error) -> {
                    if (error != null) {
                        Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                    } else {
                        refresh();
                    }
                });
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showNewFolderDialog() {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(R.string.new_folder_hint);
        int pad = (int) (16 * activity.getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.new_folder_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                String name = input.getText().toString().trim();
                if (!name.isEmpty()) {
                    String path = currentPath.endsWith("/")
                        ? currentPath + name : currentPath + "/" + name;
                    sftpManager.mkdir(session, path, (result, error) -> {
                        if (error != null) {
                            Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                        } else {
                            refresh();
                        }
                    });
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private class FileListAdapter extends RecyclerView.Adapter<FileListAdapter.VH> {

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(activity).inflate(R.layout.item_file_entry, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            FileEntry entry = entries.get(position);
            holder.nameView.setText(entry.name);
            holder.sizeView.setText(entry.getDisplaySize());
            if ("..".equals(entry.name)) {
                holder.iconView.setImageResource(R.drawable.ic_folder_up);
            } else {
                holder.iconView.setImageResource(entry.isDirectory ? R.drawable.ic_folder : R.drawable.ic_file);
            }
            holder.itemView.setOnClickListener(v -> onItemClick(entry));
            holder.itemView.setOnLongClickListener("..".equals(entry.name) ? null : v -> {
                onItemLongClick(entry);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView iconView;
            TextView nameView;
            TextView sizeView;

            VH(View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.file_icon);
                nameView = itemView.findViewById(R.id.file_name);
                sizeView = itemView.findViewById(R.id.file_size);
            }
        }
    }
}
