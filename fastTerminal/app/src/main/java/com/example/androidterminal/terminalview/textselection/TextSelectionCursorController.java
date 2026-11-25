package com.example.androidterminal.terminalview.textselection;

import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.WcWidth;
import com.example.androidterminal.R;
import com.example.androidterminal.terminalview.TerminalView;

public class TextSelectionCursorController implements CursorController {

    private final TerminalView terminalView;
    private final TextSelectionHandleView mStartHandle, mEndHandle;
    private boolean mIsSelectingText = false;
    private boolean mShowSelectionHandles = true;
    private boolean mMouseSelectionActive = false;
    private long mShowStartTime = System.currentTimeMillis();

    private final int mHandleHeight;
    private int mSelX1 = -1, mSelX2 = -1, mSelY1 = -1, mSelY2 = -1;
    private int mAnchorX = -1, mAnchorY = -1;

    private ActionMode mActionMode;
    private final int ACTION_COPY = 1;
    private final int ACTION_PASTE = 2;
    private final int ACTION_MORE = 3;

    private static final int AUTO_SCROLL_EDGE_PX = 48;
    private static final long AUTO_SCROLL_INTERVAL_MS = 60L;
    private final Handler mAutoScrollHandler = new Handler(Looper.getMainLooper());
    private boolean mAutoScrollRunning = false;
    private int mAutoScrollDeltaRows = 0;
    private float mLastMouseX = -1f;
    private float mLastMouseY = -1f;

    private final Runnable mAutoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsSelectingText || !mMouseSelectionActive || mAutoScrollDeltaRows == 0 || terminalView.mEmulator == null) {
                stopAutoScroll();
                return;
            }
            terminalView.scrollSelectionBy(mAutoScrollDeltaRows);
            applyMouseSelectionAt(mLastMouseX, mLastMouseY);
            mAutoScrollHandler.postDelayed(this, AUTO_SCROLL_INTERVAL_MS);
        }
    };

    public TextSelectionCursorController(TerminalView terminalView) {
        this.terminalView = terminalView;
        mStartHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT);
        mEndHandle = new TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT);

        mHandleHeight = Math.max(mStartHandle.getHandleHeight(), mEndHandle.getHandleHeight());
    }

    @Override
    public void show(MotionEvent event) {
        mShowSelectionHandles = true;
        mMouseSelectionActive = false;
        setInitialTextSelectionPosition(event);
        mAnchorX = mSelX1;
        mAnchorY = mSelY1;
        mStartHandle.positionAtCursor(mSelX1, mSelY1, true);
        mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, true);

        setActionModeCallBacks();
        mShowStartTime = System.currentTimeMillis();
        mIsSelectingText = true;
    }

    public void startMouseSelection(MotionEvent event) {
        stopAutoScroll();
        int[] columnAndRow = terminalView.getColumnAndRow(event, true);
        mAnchorX = clampColumn(columnAndRow[0]);
        mAnchorY = clampSelectionRow(columnAndRow[1]);
        mSelX1 = mSelX2 = mAnchorX;
        mSelY1 = mSelY2 = mAnchorY;
        mShowSelectionHandles = false;
        mMouseSelectionActive = true;
        mShowStartTime = System.currentTimeMillis();
        mIsSelectingText = true;
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        }
        terminalView.invalidate();
    }

    public void updateMouseSelection(MotionEvent event) {
        if (!mMouseSelectionActive) {
            return;
        }
        mLastMouseX = event.getX();
        mLastMouseY = event.getY();
        updateAutoScrollDirection(mLastMouseY);
        applyMouseSelectionAt(mLastMouseX, mLastMouseY);
    }

    private void updateAutoScrollDirection(float y) {
        int height = terminalView.getHeight();
        if (height <= 0) {
            stopAutoScroll();
            return;
        }

        int delta = 0;
        if (y < AUTO_SCROLL_EDGE_PX) {
            delta = (y < AUTO_SCROLL_EDGE_PX / 2f) ? -2 : -1;
        } else if (y > height - AUTO_SCROLL_EDGE_PX) {
            delta = (y > height - AUTO_SCROLL_EDGE_PX / 2f) ? 2 : 1;
        }

        if (delta == 0) {
            stopAutoScroll();
            return;
        }

        mAutoScrollDeltaRows = delta;
        if (!mAutoScrollRunning) {
            mAutoScrollRunning = true;
            mAutoScrollHandler.removeCallbacks(mAutoScrollRunnable);
            mAutoScrollHandler.postDelayed(mAutoScrollRunnable, AUTO_SCROLL_INTERVAL_MS);
        }
    }

    private void stopAutoScroll() {
        mAutoScrollRunning = false;
        mAutoScrollDeltaRows = 0;
        mAutoScrollHandler.removeCallbacks(mAutoScrollRunnable);
    }

    private void applyMouseSelectionAt(float x, float y) {
        if (terminalView.mEmulator == null || terminalView.mRenderer == null) {
            return;
        }

        int currentX = clampColumn(terminalView.getCursorX(x));
        int currentY = clampSelectionRow(terminalView.getCursorY(y));
        TerminalBuffer screen = terminalView.mEmulator.getScreen();

        currentX = getValidCurX(screen, currentY, currentX);

        if (comesBefore(mAnchorY, mAnchorX, currentY, currentX)) {
            mSelY1 = mAnchorY;
            mSelX1 = mAnchorX;
            mSelY2 = currentY;
            mSelX2 = currentX;
        } else {
            mSelY1 = currentY;
            mSelX1 = currentX;
            mSelY2 = mAnchorY;
            mSelX2 = mAnchorX;
        }

        terminalView.invalidate();
    }

    public void finishMouseSelection() {
        stopAutoScroll();
        if (!mMouseSelectionActive) {
            return;
        }

        mMouseSelectionActive = false;
        if (hasSelectionRange()) {
            setActionModeCallBacks();
        } else {
            hide();
        }
        terminalView.invalidate();
    }

    @Override
    public boolean hide() {
        return hide(false);
    }

    public boolean hide(boolean force) {
        stopAutoScroll();
        if (!isActive()) return false;

        if (!force && System.currentTimeMillis() - mShowStartTime < 300) {
            return false;
        }

        ActionMode actionMode = mActionMode;
        mActionMode = null;

        if (mShowSelectionHandles) {
            mStartHandle.hide();
            mEndHandle.hide();
        }

        if (actionMode != null) {
            actionMode.finish();
        }

        mSelX1 = mSelY1 = mSelX2 = mSelY2 = -1;
        mAnchorX = mAnchorY = -1;
        mIsSelectingText = false;
        mMouseSelectionActive = false;
        mShowSelectionHandles = true;

        return true;
    }

    @Override
    public void render() {
        if (!isActive()) return;

        if (mShowSelectionHandles) {
            mStartHandle.positionAtCursor(mSelX1, mSelY1, false);
            mEndHandle.positionAtCursor(mSelX2 + 1, mSelY2, false);
        }

        if (mActionMode != null) {
            mActionMode.invalidate();
        }
    }

    public void setInitialTextSelectionPosition(MotionEvent event) {
        int[] columnAndRow = terminalView.getColumnAndRow(event, true);
        mSelX1 = mSelX2 = columnAndRow[0];
        mSelY1 = mSelY2 = columnAndRow[1];

        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        if (!" ".equals(screen.getSelectedText(mSelX1, mSelY1, mSelX1, mSelY1))) {
            while (mSelX1 > 0 && !"".equals(screen.getSelectedText(mSelX1 - 1, mSelY1, mSelX1 - 1, mSelY1))) {
                mSelX1--;
            }
            while (mSelX2 < terminalView.mEmulator.mColumns - 1 && !"".equals(screen.getSelectedText(mSelX2 + 1, mSelY1, mSelX2 + 1, mSelY1))) {
                mSelX2++;
            }
        }
    }

    public void setActionModeCallBacks() {
        final ActionMode.Callback callback = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                int show = MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT;

                ClipboardManager clipboard = (ClipboardManager) terminalView.getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, R.string.copy_text).setShowAsAction(show);
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, R.string.paste_text).setEnabled(clipboard != null && clipboard.hasPrimaryClip()).setShowAsAction(show);
                menu.add(Menu.NONE, ACTION_MORE, Menu.NONE, R.string.text_selection_more);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (!isActive()) {
                    return true;
                }

                switch (item.getItemId()) {
                    case ACTION_COPY:
                        String selectedText = terminalView.mEmulator.getSelectedText(mSelX1, mSelY1, mSelX2, mSelY2).trim();
                        terminalView.mTermSession.onCopyTextToClipboard(selectedText);
                        terminalView.stopTextSelectionMode();
                        break;
                    case ACTION_PASTE:
                        terminalView.stopTextSelectionMode();
                        terminalView.mTermSession.onPasteTextFromClipboard();
                        break;
                    case ACTION_MORE:
                        terminalView.stopTextSelectionMode();
                        terminalView.showContextMenu();
                        break;
                }

                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                mActionMode = null;
                if (mIsSelectingText) {
                    hide(true);
                    terminalView.invalidate();
                }
            }
        };

        mActionMode = terminalView.startActionMode(new ActionMode.Callback2() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                return callback.onActionItemClicked(mode, item);
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
            }

            @Override
            public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                int x1 = Math.round(mSelX1 * terminalView.mRenderer.getFontWidth());
                int x2 = Math.round(mSelX2 * terminalView.mRenderer.getFontWidth());
                int y1 = Math.round((mSelY1 - 1 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing());
                int y2 = Math.round((mSelY2 + 1 - terminalView.getTopRow()) * terminalView.mRenderer.getFontLineSpacing());

                if (x1 > x2) {
                    int tmp = x1;
                    x1 = x2;
                    x2 = tmp;
                }

                outRect.set(x1, y1 + mHandleHeight, x2, y2 + mHandleHeight);
            }
        }, ActionMode.TYPE_FLOATING);
    }

    @Override
    public void updatePosition(TextSelectionHandleView handle, int x, int y) {
        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        final int scrollRows = screen.getActiveRows() - terminalView.mEmulator.mRows;
        if (handle == mStartHandle) {
            mSelX1 = terminalView.getCursorX(x);
            mSelY1 = terminalView.getCursorY(y);
            if (mSelX1 < 0) {
                mSelX1 = 0;
            }

            if (mSelY1 < -scrollRows) {
                mSelY1 = -scrollRows;
            } else if (mSelY1 > terminalView.mEmulator.mRows - 1) {
                mSelY1 = terminalView.mEmulator.mRows - 1;
            }

            if (mSelY1 > mSelY2) {
                mSelY1 = mSelY2;
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX1 = mSelX2;
            }

            if (!terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = terminalView.getTopRow();

                if (mSelY1 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (mSelY1 >= topRow + terminalView.mEmulator.mRows) {
                    topRow++;
                    if (topRow > 0) {
                        topRow = 0;
                    }
                }

                terminalView.setTopRow(topRow);
            }

            mSelX1 = getValidCurX(screen, mSelY1, mSelX1);
        } else {
            mSelX2 = terminalView.getCursorX(x);
            mSelY2 = terminalView.getCursorY(y);
            if (mSelX2 < 0) {
                mSelX2 = 0;
            }

            if (mSelY2 < -scrollRows) {
                mSelY2 = -scrollRows;
            } else if (mSelY2 > terminalView.mEmulator.mRows - 1) {
                mSelY2 = terminalView.mEmulator.mRows - 1;
            }

            if (mSelY1 > mSelY2) {
                mSelY2 = mSelY1;
            }
            if (mSelY1 == mSelY2 && mSelX1 > mSelX2) {
                mSelX2 = mSelX1;
            }

            if (!terminalView.mEmulator.isAlternateBufferActive()) {
                int topRow = terminalView.getTopRow();

                if (mSelY2 <= topRow) {
                    topRow--;
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows;
                    }
                } else if (mSelY2 >= topRow + terminalView.mEmulator.mRows) {
                    topRow++;
                    if (topRow > 0) {
                        topRow = 0;
                    }
                }

                terminalView.setTopRow(topRow);
            }

            mSelX2 = getValidCurX(screen, mSelY2, mSelX2);
        }

        terminalView.invalidate();
    }

    private int getValidCurX(TerminalBuffer screen, int cy, int cx) {
        String line = screen.getSelectedText(0, cy, cx, cy);
        if (!TextUtils.isEmpty(line)) {
            int col = 0;
            for (int i = 0, len = line.length(); i < len; i++) {
                char ch1 = line.charAt(i);
                if (ch1 == 0) {
                    break;
                }

                int wc;
                if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                    char ch2 = line.charAt(++i);
                    wc = WcWidth.width(Character.toCodePoint(ch1, ch2));
                } else {
                    wc = WcWidth.width(ch1);
                }

                final int cend = col + wc;
                if (cx > col && cx < cend) {
                    return cend;
                }
                if (cend == col) {
                    return col;
                }
                col = cend;
            }
        }
        return cx;
    }

    public void decrementYTextSelectionCursors(int decrement) {
        mSelY1 -= decrement;
        mSelY2 -= decrement;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public void onTouchModeChanged(boolean isInTouchMode) {
        if (!isInTouchMode) {
            terminalView.stopTextSelectionMode();
        }
    }

    @Override
    public void onDetached() {
        stopAutoScroll();
    }

    @Override
    public boolean isActive() {
        return mIsSelectingText;
    }

    public void getSelectors(int[] sel) {
        if (sel == null || sel.length != 4) {
            return;
        }

        sel[0] = mSelY1;
        sel[1] = mSelY2;
        sel[2] = mSelX1;
        sel[3] = mSelX2;
    }

    public ActionMode getActionMode() {
        return mActionMode;
    }

    public boolean isSelectionStartDragged() {
        return mStartHandle.isDragging();
    }

    public boolean isSelectionEndDragged() {
        return mEndHandle.isDragging();
    }

    private boolean hasSelectionRange() {
        return mSelX1 != mSelX2 || mSelY1 != mSelY2;
    }

    private int clampColumn(int column) {
        if (column < 0) {
            return 0;
        }
        return Math.min(column, terminalView.mEmulator.mColumns - 1);
    }

    private int clampSelectionRow(int row) {
        if (terminalView.mEmulator == null) return 0;
        TerminalBuffer screen = terminalView.mEmulator.getScreen();
        int scrollRows = screen.getActiveTranscriptRows();
        int minRow = -scrollRows;
        int maxRow = Math.max(minRow, terminalView.getTopRow() + terminalView.mEmulator.mRows - 1);
        if (row < minRow) return minRow;
        if (row > maxRow) return maxRow;
        return row;
    }

    private boolean comesBefore(int rowA, int colA, int rowB, int colB) {
        return rowA < rowB || (rowA == rowB && colA <= colB);
    }
}