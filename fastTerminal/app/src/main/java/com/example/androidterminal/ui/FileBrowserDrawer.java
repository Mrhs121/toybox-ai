package com.example.androidterminal.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
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
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.androidterminal.R;
import com.example.androidterminal.sftp.FileEntry;
import com.example.androidterminal.sftp.SftpManager;
import com.example.androidterminal.ssh.SshTerminalSession;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FileBrowserDrawer {

    public interface UploadCallback {
        void onPickFile();
    }

    private static final int SORT_NAME_ASC = 0;
    private static final int SORT_NAME_DESC = 1;
    private static final int SORT_SIZE = 2;
    private static final int SORT_DATE = 3;

    private final Activity activity;
    private final DrawerLayout drawerLayout;
    private final View panel;
    private final LinearLayout breadcrumbView;
    private final RecyclerView listView;
    private final View emptyView;
    private final TextView emptyTextView;
    private final ProgressBar loadingView;
    private final ProgressBar progressView;
    private final EditText searchView;
    private final ImageButton searchClearView;
    private final ImageButton uploadButton;
    private final TextView bottomPathView;
    private final SftpManager sftpManager = new SftpManager();

    private final List<FileEntry> rawEntries = new ArrayList<>();
    private final List<FileEntry> entries = new ArrayList<>();
    private FileListAdapter adapter;
    private String currentPath = "/";
    private SshTerminalSession session;
    private UploadCallback uploadCallback;
    private int sortMode = SORT_NAME_ASC;
    private boolean panelShownAsChild = false;

    public FileBrowserDrawer(Activity activity, DrawerLayout drawerLayout, View panel) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.panel = panel;
        this.breadcrumbView = panel.findViewById(R.id.file_browser_breadcrumb);
        this.listView = panel.findViewById(R.id.file_browser_list);
        this.emptyView = panel.findViewById(R.id.file_browser_empty);
        this.emptyTextView = panel.findViewById(R.id.file_browser_empty_text);
        this.loadingView = panel.findViewById(R.id.file_browser_loading);
        this.progressView = panel.findViewById(R.id.file_browser_progress);
        this.searchView = panel.findViewById(R.id.file_browser_search);
        this.searchClearView = panel.findViewById(R.id.file_browser_search_clear);
        this.uploadButton = panel.findViewById(R.id.file_browser_upload);
        this.bottomPathView = panel.findViewById(R.id.file_browser_bottom_path);

        adapter = new FileListAdapter();
        listView.setLayoutManager(new LinearLayoutManager(activity));
        listView.addItemDecoration(new RowDividerDecoration());
        listView.setAdapter(adapter);

        panel.findViewById(R.id.file_browser_home).setOnClickListener(v -> navigateTo("/"));
        panel.findViewById(R.id.file_browser_edit_path).setOnClickListener(v -> showPathDialog());
        panel.findViewById(R.id.file_browser_refresh).setOnClickListener(v -> refresh());
        panel.findViewById(R.id.file_browser_back).setOnClickListener(v -> goUp());
        panel.findViewById(R.id.file_browser_sort).setOnClickListener(this::showSortMenu);
        uploadButton.setOnClickListener(v -> {
            if (session == null || !session.isConnected()) {
                Toast.makeText(activity, activity.getString(R.string.error_connect_ssh_first), Toast.LENGTH_SHORT).show();
                return;
            }
            Snackbar.make(panel, activity.getString(R.string.upload_destination_format, currentPath), Snackbar.LENGTH_LONG).show();
            if (uploadCallback != null) uploadCallback.onPickFile();
        });
        panel.findViewById(R.id.file_browser_new_folder).setOnClickListener(v -> showNewFolderDialog());
        searchClearView.setOnClickListener(v -> searchView.setText(""));

        searchView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                searchClearView.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
                applyFilterAndSort();
            }
        });
    }

    public void setSession(SshTerminalSession session) {
        if (this.session != session) {
            this.session = session;
            currentPath = "/";
            searchView.setText("");
        }
    }

    public void setUploadCallback(UploadCallback callback) {
        this.uploadCallback = callback;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public void open() {
        if (isHostedInDrawer()) {
            drawerLayout.openDrawer(panel);
        } else {
            panel.setVisibility(View.VISIBLE);
            panelShownAsChild = true;
        }
        if (session != null && session.isConnected()) {
            navigateTo(currentPath);
        }
    }

    public void close() {
        if (isHostedInDrawer()) {
            drawerLayout.closeDrawer(panel);
        } else {
            panel.setVisibility(View.GONE);
            panelShownAsChild = false;
        }
    }

    public boolean isOpen() {
        if (isHostedInDrawer()) {
            return drawerLayout.isDrawerOpen(panel);
        }
        return panelShownAsChild;
    }

    public void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    private boolean isHostedInDrawer() {
        return panel.getParent() instanceof DrawerLayout;
    }

    public void navigateTo(String path) {
        currentPath = path;
        renderBreadcrumbs(path);
        bottomPathView.setText(path);
        showLoading(true);

        sftpManager.listFiles(session, path, (result, error) -> {
            showLoading(false);
            if (error != null) {
                showEmpty(activity.getString(R.string.sftp_error, error.getMessage()), false);
                return;
            }
            rawEntries.clear();
            if (result != null) {
                rawEntries.addAll(result);
            }
            applyFilterAndSort();
            emptyTextView.setText(activity.getString(R.string.file_browser_empty_folder));
            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
            listView.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        });
    }

    public void refresh() {
        navigateTo(currentPath);
    }

    private void goUp() {
        if ("/".equals(currentPath)) {
            return;
        }
        String parentPath = currentPath.substring(0, currentPath.lastIndexOf('/'));
        if (parentPath.isEmpty()) parentPath = "/";
        navigateTo(parentPath);
    }

    private void renderBreadcrumbs(String path) {
        breadcrumbView.removeAllViews();
        if ("/".equals(path)) {
            TextView root = createBreadcrumbSegment("/", "/");
            breadcrumbView.addView(root);
            return;
        }

        String[] segments = path.split("/");
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            if (seg.isEmpty()) continue;
            if (prefix.length() == 0) {
                prefix.append("/");
            } else if (!prefix.toString().endsWith("/")) {
                prefix.append("/");
            }
            prefix.append(seg);
            String segmentPath = prefix.toString();
            // For the last segment, render as plain text (no click-through needed).
            TextView segView = createBreadcrumbSegment(seg, segmentPath);
            breadcrumbView.addView(segView);
            if (i < segments.length - 1) {
                TextView sep = new TextView(activity);
                sep.setText("›");
                sep.setTextColor(ContextCompat.getColor(activity, R.color.text_muted_on_dark));
                sep.setTextSize(13f);
                breadcrumbView.addView(sep);
            }
        }
    }

    private TextView createBreadcrumbSegment(String label, String path) {
        TextView tv = new TextView(activity);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tv.setTextColor(ContextCompat.getColor(activity, R.color.text_on_dark));
        tv.setPadding(dp(6), 0, dp(6), 0);
        tv.setOnClickListener(v -> navigateTo(path));
        return tv;
    }

    private void applyFilterAndSort() {
        String query = searchView.getText().toString().trim().toLowerCase();
        entries.clear();
        for (FileEntry e : rawEntries) {
            if (query.isEmpty() || e.name.toLowerCase().contains(query)) {
                entries.add(e);
            }
        }
        Comparator<FileEntry> comparator = (a, b) -> {
            if (a.isDirectory != b.isDirectory) {
                return a.isDirectory ? -1 : 1;
            }
            switch (sortMode) {
                case SORT_NAME_DESC:
                    return b.name.compareToIgnoreCase(a.name);
                case SORT_SIZE:
                    return Long.compare(b.size, a.size);
                case SORT_DATE:
                    return Long.compare(b.modTime, a.modTime);
                case SORT_NAME_ASC:
                default:
                    return a.name.compareToIgnoreCase(b.name);
            }
        };
        entries.sort(comparator);
        adapter.notifyDataSetChanged();
    }

    private void showSortMenu(View anchor) {
        PopupMenu popup = new PopupMenu(activity, anchor);
        popup.getMenu().add(0, SORT_NAME_ASC, 0, R.string.sort_by_name_asc);
        popup.getMenu().add(0, SORT_NAME_DESC, 1, R.string.sort_by_name_desc);
        popup.getMenu().add(0, SORT_SIZE, 2, R.string.sort_by_size);
        popup.getMenu().add(0, SORT_DATE, 3, R.string.sort_by_date);
        popup.setOnMenuItemClickListener(item -> {
            sortMode = item.getItemId();
            applyFilterAndSort();
            return true;
        });
        popup.show();
    }

    private void showPathDialog() {
        showInputSheet(activity.getString(R.string.file_browser_edit_path_title), null, currentPath, value -> {
            String path = value;
            if (!path.startsWith("/")) path = "/" + path;
            navigateTo(path);
        });
    }

    private void showLoading(boolean show) {
        loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
        listView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyView.setVisibility(View.GONE);
    }

    private void showEmpty(String message, boolean keepList) {
        emptyTextView.setText(message);
        if (keepList) {
            emptyView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        }
    }

    public void uploadFile(File localFile) {
        if (session == null || !session.isConnected()) {
            Toast.makeText(activity, activity.getString(R.string.error_connect_ssh_first), Toast.LENGTH_SHORT).show();
            return;
        }
        String remotePath = currentPath.endsWith("/")
            ? currentPath + localFile.getName()
            : currentPath + "/" + localFile.getName();
        progressView.setVisibility(View.VISIBLE);
        uploadButton.setEnabled(false);
        Toast.makeText(activity, activity.getString(R.string.uploading_format, localFile.getName(), remotePath), Toast.LENGTH_LONG).show();

        sftpManager.upload(session, localFile, remotePath, (result, error) -> {
            progressView.setVisibility(View.GONE);
            uploadButton.setEnabled(true);
            if (error != null) {
                Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, activity.getString(R.string.uploaded_to_path, localFile.getName(), remotePath), Toast.LENGTH_LONG).show();
                refresh();
            }
        });
    }

    private void onItemClick(FileEntry entry) {
        if (entry.isDirectory) {
            navigateTo(entry.path);
        } else {
            showItemMenu(entry);
        }
    }

    private void onItemLongClick(FileEntry entry) {
        showItemMenu(entry);
    }

    private void showItemMenu(FileEntry entry) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(activity);
        View sheetView = LayoutInflater.from(activity).inflate(R.layout.view_bottom_sheet_actions, null, false);
        ((TextView) sheetView.findViewById(R.id.sheet_title)).setText(entry.name);
        ViewGroup actions = sheetView.findViewById(R.id.sheet_actions_container);

        if (!entry.isDirectory) {
            actions.addView(createSheetActionRow(R.drawable.ic_download, activity.getString(R.string.download), false,
                v -> { sheet.dismiss(); downloadFile(entry); }));
        }
        actions.addView(createSheetActionRow(R.drawable.ic_edit, activity.getString(R.string.rename_title), false,
            v -> { sheet.dismiss(); showRenameDialog(entry); }));
        actions.addView(createSheetActionRow(R.drawable.ic_delete_small, activity.getString(R.string.delete_confirm_title), true,
            v -> { sheet.dismiss(); showDeleteConfirm(entry); }));

        sheet.setContentView(sheetView);
        sheet.show();
    }

    private View createSheetActionRow(int iconRes, String label, boolean destructive, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(13), dp(16), dp(13));
        android.util.TypedValue tv = new android.util.TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setOnClickListener(listener);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        int iconSize = dp(22);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        int tint = ContextCompat.getColor(activity, destructive ? R.color.status_error : R.color.text_muted_on_dark);
        androidx.core.widget.ImageViewCompat.setImageTintList(icon, android.content.res.ColorStateList.valueOf(tint));

        TextView labelView = new TextView(activity);
        labelView.setText(label);
        labelView.setTextColor(ContextCompat.getColor(activity, destructive ? R.color.status_error : R.color.text_on_dark));
        labelView.setTextSize(15f);
        labelView.setSingleLine(true);
        labelView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        labelView.setPadding(dp(14), 0, 0, 0);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(icon);
        row.addView(labelView);
        return row;
    }

    private void downloadFile(FileEntry entry) {
        if (android.os.Build.VERSION.SDK_INT >= 30 && !android.os.Environment.isExternalStorageManager()) {
            new android.app.AlertDialog.Builder(activity)
                .setTitle("需要存储权限")
                .setMessage("请在应用详情页开启「读写手机存储」权限，以保存文件到 Downloads/fastterminal 目录。")
                .setPositiveButton("去设置", (d, w) -> {
                    try {
                        activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + activity.getPackageName())));
                    } catch (Exception e) {
                        activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
            return;
        }
        File downloadDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "fastterminal");
        if (!downloadDir.exists()) downloadDir.mkdirs();
        File localFile = new File(downloadDir, entry.name);

        // Modern dark progress dialog (theme now resolves dark surfaces automatically).
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(20));
        TextView titleView = new TextView(activity);
        titleView.setText(R.string.download);
        titleView.setTextColor(ContextCompat.getColor(activity, R.color.text_on_dark));
        titleView.setTextSize(18f);
        titleView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        content.addView(titleView);
        TextView msgView = new TextView(activity);
        msgView.setText(entry.name);
        msgView.setTextColor(ContextCompat.getColor(activity, R.color.text_muted_on_dark));
        msgView.setTextSize(13f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(8);
        msgView.setLayoutParams(msgParams);
        content.addView(msgView);
        android.widget.ProgressBar progressBar = new android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.brand)));
        LinearLayout.LayoutParams pbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(24));
        pbParams.topMargin = dp(16);
        progressBar.setLayoutParams(pbParams);
        content.addView(progressBar);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity);
        builder.setView(content);
        builder.setCancelable(false);
        androidx.appcompat.app.AlertDialog progressDialog = builder.create();
        progressDialog.show();

        sftpManager.download(session, entry.path, localFile,
            (bytesTransferred, totalBytes) -> {
                if (totalBytes > 0) {
                    int pct = (int) (bytesTransferred * 100 / totalBytes);
                    progressBar.setProgress(pct);
                    msgView.setText(entry.name + "\n" + formatSize(bytesTransferred) + " / " + formatSize(totalBytes));
                }
            },
            (result, error) -> {
                progressDialog.dismiss();
                if (error != null) {
                    Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, activity.getString(R.string.downloaded_to, result.getAbsolutePath()), Toast.LENGTH_LONG).show();
                }
            });
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void showRenameDialog(FileEntry entry) {
        showInputSheet(activity.getString(R.string.rename_title), null, entry.name, newName -> {
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
        });
    }

    private void showDeleteConfirm(FileEntry entry) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.delete_confirm_title)
            .setMessage(activity.getString(R.string.delete_confirm_message, entry.name))
            .setPositiveButton(R.string.delete_action, (dialog, which) -> {
                sftpManager.delete(session, entry.path, entry.isDirectory, (result, error) -> {
                    if (error != null) {
                        Toast.makeText(activity, activity.getString(R.string.sftp_error, error.getMessage()), Toast.LENGTH_SHORT).show();
                    } else {
                        refresh();
                    }
                });
            })
            .setNegativeButton(android.R.string.cancel, null);
        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
            .setTextColor(ContextCompat.getColor(activity, R.color.status_error));
    }

    private void showNewFolderDialog() {
        showInputSheet(activity.getString(R.string.new_folder_title), activity.getString(R.string.new_folder_hint), "", name -> {
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
        });
    }

    private void showInputSheet(String title, String hint, String initialText, java.util.function.Consumer<String> onSubmit) {
        com.google.android.material.bottomsheet.BottomSheetDialog sheet =
            new com.google.android.material.bottomsheet.BottomSheetDialog(activity);
        View sheetView = LayoutInflater.from(activity).inflate(R.layout.view_input_bottom_sheet, null, false);
        ((TextView) sheetView.findViewById(R.id.input_sheet_title)).setText(title);
        com.google.android.material.textfield.TextInputLayout field = sheetView.findViewById(R.id.input_sheet_field);
        if (hint != null) {
            field.setHint(hint);
        }
        com.google.android.material.textfield.TextInputEditText input = sheetView.findViewById(R.id.input_sheet_input);
        input.setText(initialText);
        input.setSelectAllOnFocus(true);
        sheetView.findViewById(R.id.input_sheet_cancel).setOnClickListener(v -> sheet.dismiss());
        sheetView.findViewById(R.id.input_sheet_confirm).setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (!value.isEmpty()) {
                onSubmit.accept(value);
                sheet.dismiss();
            }
        });
        sheet.setContentView(sheetView);
        sheet.show();
    }

    private int dp(int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private class RowDividerDecoration extends RecyclerView.ItemDecoration {
        private final android.graphics.Paint paint = new android.graphics.Paint();

        RowDividerDecoration() {
            paint.setColor(ContextCompat.getColor(activity, R.color.subtle_divider));
            paint.setStrokeWidth(dp(1));
        }

        @Override
        public void onDrawOver(@NonNull android.graphics.Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                float y = child.getBottom();
                c.drawLine(child.getLeft(), y, child.getRight(), y, paint);
            }
        }
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
            holder.dateView.setText(entry.getDisplayDate());
            if (entry.isDirectory) {
                holder.iconView.setImageResource(R.drawable.ic_folder);
            } else {
                holder.iconView.setImageResource(R.drawable.ic_file);
            }
            holder.itemView.setOnClickListener(v -> onItemClick(entry));
            holder.itemView.setOnLongClickListener(v -> {
                onItemLongClick(entry);
                return true;
            });
            holder.overflowButton.setOnClickListener(v -> showItemMenu(entry));
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView iconView;
            TextView nameView;
            TextView sizeView;
            TextView dateView;
            ImageButton overflowButton;

            VH(View itemView) {
                super(itemView);
                iconView = itemView.findViewById(R.id.file_icon);
                nameView = itemView.findViewById(R.id.file_name);
                sizeView = itemView.findViewById(R.id.file_size);
                dateView = itemView.findViewById(R.id.file_date);
                overflowButton = itemView.findViewById(R.id.file_overflow);
            }
        }
    }
}
