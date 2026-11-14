package vn.edu.course.calculator;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDragHandleView;
import com.google.android.material.color.MaterialColors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** History sheet that snaps between half-screen and full-screen. */
public final class HistorySheet extends BottomSheetDialog {
    private static final int PURPLE = Color.rgb(173, 142, 248);
    private static final int RED = Color.rgb(229, 78, 87);
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final HistoryStore store;
    private final Typeface typeface;
    private final Consumer<HistoryStore.Entry> onEntrySelected;
    private final Set<Long> selected = new HashSet<>();
    private final List<Object> rows = new ArrayList<>();
    private List<HistoryStore.Entry> entries = new ArrayList<>();
    private final RowsAdapter adapter = new RowsAdapter();
    private final int surface;
    private final int textColor;
    private final int secondaryText;
    private Button topDone;
    private Button leftAction;
    private Button rightAction;
    private TextView emptyText;
    private LinearLayout footer;
    private TextView pinnedDate;
    private FrameLayout pinnedDateContainer;
    private boolean selecting;
    private boolean busy;
    private boolean loaded;
    private int statusBarInset;
    private int windowHeight;
    private boolean footerShown = true;
    private boolean closing;
    private ValueAnimator entranceAnimator;

    public HistorySheet(Activity host, Consumer<HistoryStore.Entry> onEntrySelected) {
        this(host, HistoryStore.get(host), onEntrySelected);
    }

    HistorySheet(Activity host, HistoryStore store) {
        this(host, store, entry -> {});
    }

    HistorySheet(Activity host, HistoryStore store,
                 Consumer<HistoryStore.Entry> onEntrySelected) {
        super(host);
        typeface = getContext().getResources().getFont(R.font.times_new_roman);
        this.store = store;
        this.onEntrySelected = onEntrySelected;
        surface = MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorSurface, Color.DKGRAY);
        textColor = MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
        secondaryText = MaterialColors.getColor(getContext(),
                com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY);

        LinearLayout root = column();
        root.setBackground(roundedTop(surface));
        root.setPadding(0, dp(8), 0, 0);

        BottomSheetDragHandleView handle = new BottomSheetDragHandleView(getContext());
        handle.setImageTintList(ColorStateList.valueOf(secondaryText));
        root.addView(handle, new LinearLayout.LayoutParams(MATCH, dp(32)));

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(16), 0);
        TextView title = text(getContext().getString(R.string.menu_history), 20, textColor);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        topDone = button(getContext().getString(R.string.history_done), PURPLE);
        topDone.setOnClickListener(v -> dismiss());
        header.addView(topDone, new LinearLayout.LayoutParams(WRAP, dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(MATCH, WRAP));

        FrameLayout listArea = new FrameLayout(getContext());
        listArea.setPadding(dp(16), 0, dp(16), 0);
        ListView list = new ListView(getContext());
        list.setId(R.id.history_list);
        list.setVerticalScrollBarEnabled(false);
        list.setDivider(new ColorDrawable(getContext().getColor(R.color.history_divider)));
        list.setDividerHeight(dp(1));
        list.setSelector(new ColorDrawable(Color.TRANSPARENT));
        list.setNestedScrollingEnabled(true);
        list.setAdapter(adapter);
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int scrollState) {}
            @Override public void onScroll(AbsListView view, int firstVisibleItem,
                    int visibleItemCount, int totalItemCount) {
                updatePinnedDate(list);
            }
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (busy || closing || !(rows.get(position) instanceof HistoryStore.Entry)) return;
            HistoryStore.Entry entry = (HistoryStore.Entry) rows.get(position);
            if (!selecting) {
                onEntrySelected.accept(entry);
                dismiss();
                return;
            }
            if (!selected.add(entry.id)) selected.remove(entry.id);
            updateRowSelection(view, entry, true);
            updateControls();
        });
        listArea.addView(list, new FrameLayout.LayoutParams(MATCH, MATCH));
        emptyText = text(getContext().getString(R.string.history_loading), 16, secondaryText);
        emptyText.setGravity(Gravity.CENTER);
        listArea.addView(emptyText, new FrameLayout.LayoutParams(MATCH, MATCH));
        list.setEmptyView(emptyText);

        pinnedDateContainer = new FrameLayout(getContext());
        pinnedDateContainer.setBackgroundColor(surface);
        // Swallows taps on the heading so covered rows cannot be selected accidentally.
        pinnedDateContainer.setClickable(true);
        pinnedDateContainer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        pinnedDate = dateLabel();
        pinnedDate.setId(R.id.history_pinned_date);
        pinnedDateContainer.addView(pinnedDate, new FrameLayout.LayoutParams(MATCH, WRAP));
        pinnedDateContainer.setVisibility(View.INVISIBLE);
        pinnedDateContainer.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updatePinnedDate(list));
        listArea.addView(pinnedDateContainer, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP));
        root.addView(listArea, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        footer = new LinearLayout(getContext());
        footer.setId(R.id.history_footer);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        footer.setBackgroundColor(MaterialColors.getColor(host,
                com.google.android.material.R.attr.colorSurfaceVariant, surface));
        footer.setPadding(dp(16), 0, dp(16), dp(8));
        leftAction = button(getContext().getString(R.string.history_select), PURPLE);
        leftAction.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        leftAction.setOnClickListener(v -> {
            selecting = !selecting;
            selected.clear();
            for (int child = 0; child < list.getChildCount(); child++) {
                View row = list.getChildAt(child);
                int position = list.getFirstVisiblePosition() + child;
                if (position < rows.size() && rows.get(position) instanceof HistoryStore.Entry
                        && row.getTag() instanceof EntryHolder) {
                    ((EntryHolder) row.getTag()).setSelectionMode(selecting, true);
                    updateRowSelection(row, (HistoryStore.Entry) rows.get(position), false);
                }
            }
            updateControls();
        });
        rightAction = button(getContext().getString(R.string.history_delete_all), RED);
        rightAction.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        rightAction.setOnClickListener(v -> deleteHistory());
        footer.addView(leftAction, new LinearLayout.LayoutParams(0, WRAP, 1f));
        footer.addView(rightAction, new LinearLayout.LayoutParams(0, WRAP, 1f));
        root.addView(footer, new LinearLayout.LayoutParams(MATCH, WRAP));
        footer.addOnLayoutChangeListener((v, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!footerShown && bottom - top != oldBottom - oldTop) {
                footer.setTranslationY(bottom - top);
            }
        });

        // Keep the footer above both gesture navigation and three-button navigation.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            statusBarInset = bars.top;
            v.setPadding(bars.left, dp(8), bars.right, 0);
            footer.setPadding(dp(16), 0, dp(16), dp(8) + bars.bottom);
            FrameLayout sheet = findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) updateVisibleContent(sheet, root);
            return insets;
        });
        setContentView(root, new ViewGroup.LayoutParams(MATCH, MATCH));
        if (getWindow() != null) getWindow().setWindowAnimations(0);
        setDismissWithAnimation(true);
        setTitle(R.string.menu_history);
        getBehavior().setDraggable(true);
        getBehavior().setDraggableOnNestedScroll(false);
        getBehavior().setMaxWidth(host.getResources().getDisplayMetrics().widthPixels);
        updateControls();

        setOnShowListener(ignored -> {
            FrameLayout sheet = findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                windowHeight = host.getWindow().getDecorView().getHeight();
                if (windowHeight <= 0) windowHeight = host.getResources().getDisplayMetrics().heightPixels;
                ViewGroup.LayoutParams params = sheet.getLayoutParams();
                params.height = MATCH;
                params.width = MATCH;
                sheet.setLayoutParams(params);
                sheet.setBackgroundColor(Color.TRANSPARENT);
                getBehavior().setFitToContents(true);
                getBehavior().setPeekHeight(windowHeight / 2);
                getBehavior().setSkipCollapsed(false);
                getBehavior().setState(BottomSheetBehavior.STATE_COLLAPSED);
                getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override public void onStateChanged(View bottomSheet, int newState) {
                        updateVisibleContent(sheet, root);
                    }
                    @Override public void onSlide(View bottomSheet, float slideOffset) {
                        updateVisibleContent(sheet, root);
                    }
                });
                sheet.addOnLayoutChangeListener((v, left, top, right, bottom,
                        oldLeft, oldTop, oldRight, oldBottom) ->
                        sheet.post(() -> updateVisibleContent(sheet, root)));
                sheet.post(() -> updateVisibleContent(sheet, root));
                sheet.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        sheet.getViewTreeObserver().removeOnPreDrawListener(this);
                        animateEntrance(sheet, root);
                        return true;
                    }
                });
            }
            ViewCompat.requestApplyInsets(root);
            reload();
        });
    }

    private void animateEntrance(FrameLayout sheet, LinearLayout content) {
        if (closing || !isShowing() || !ValueAnimator.areAnimatorsEnabled()) return;
        View parent = (View) sheet.getParent();
        float start = Math.max(0, parent.getHeight() - sheet.getTop());
        sheet.setTranslationY(start);
        getBehavior().setDraggable(false);
        updateVisibleContent(sheet, content);
        ValueAnimator motion = ValueAnimator.ofFloat(start, 0f);
        entranceAnimator = motion;
        motion.setDuration(320);
        motion.setInterpolator(new DecelerateInterpolator(1.5f));
        motion.addUpdateListener(animation -> {
            sheet.setTranslationY((float) animation.getAnimatedValue());
            updateVisibleContent(sheet, content);
        });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (entranceAnimator != animation) return;
                entranceAnimator = null;
                sheet.setTranslationY(0);
                if (!closing && isShowing()) getBehavior().setDraggable(true);
                updateVisibleContent(sheet, content);
            }
        });
        motion.start();
    }

    private void stopEntrance() {
        ValueAnimator running = entranceAnimator;
        entranceAnimator = null;
        if (running != null) running.cancel();
    }

    @Override public void cancel() {
        closing = true;
        stopEntrance();
        super.cancel();
    }

    @Override public void dismiss() {
        if (isShowing() && getDismissWithAnimation()
                && getBehavior().getState() != BottomSheetBehavior.STATE_HIDDEN) {
            cancel();
        } else {
            super.dismiss();
        }
    }

    @Override public void onDetachedFromWindow() {
        stopEntrance();
        footer.animate().cancel();
        super.onDetachedFromWindow();
    }

    private void updateFooterVisibility(float visibleFraction) {
        boolean show = visibleFraction >= 0.30f;
        if (footerShown == show) return;
        footerShown = show;
        footer.animate().cancel();
        float offset = show ? 0 : Math.max(footer.getHeight(), dp(56));
        if (ValueAnimator.areAnimatorsEnabled()) {
            footer.animate().translationY(offset).setDuration(180)
                    .setInterpolator(new DecelerateInterpolator(1.5f)).start();
        } else {
            footer.setTranslationY(offset);
        }
        footer.setImportantForAccessibility(show ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        updateControls();
    }

    private void updateRowSelection(View row, HistoryStore.Entry entry, boolean animate) {
        EntryHolder holder = (EntryHolder) row.getTag();
        boolean checked = selected.contains(entry.id);
        holder.check.setChecked(checked, animate);
        row.setBackgroundColor(checked ? 0x33AD8EF8 : Color.TRANSPARENT);
        int description = !selecting ? R.string.history_entry_description
                : checked ? R.string.history_entry_selected_description
                : R.string.history_entry_unselected_description;
        row.setContentDescription(getContext().getString(description, entry.expression, entry.result));
    }

    private void updatePinnedDate(ListView list) {
        if (pinnedDateContainer == null) return;
        if (rows.isEmpty() || list.getChildCount() == 0) {
            pinnedDateContainer.setVisibility(View.INVISIBLE);
            return;
        }
        int first = Math.min(list.getFirstVisiblePosition(), rows.size() - 1);
        int heading = first;
        while (heading >= 0 && !(rows.get(heading) instanceof String)) heading--;
        if (heading < 0) {
            pinnedDateContainer.setVisibility(View.INVISIBLE);
            return;
        }
        String date = (String) rows.get(heading);
        if (!date.contentEquals(pinnedDate.getText())) pinnedDate.setText(date);
        pinnedDateContainer.setVisibility(View.VISIBLE);

        // The next date pushes the current pinned heading out as its group reaches the top.
        float offset = 0;
        for (int child = 0; child < list.getChildCount(); child++) {
            int position = list.getFirstVisiblePosition() + child;
            if (position >= rows.size()) break;
            if (position > heading && rows.get(position) instanceof String) {
                offset = Math.min(0, list.getChildAt(child).getTop()
                        - pinnedDateContainer.getHeight());
                break;
            }
        }
        pinnedDateContainer.setTranslationY(offset);
    }

    private void updateVisibleContent(FrameLayout sheet, LinearLayout content) {
        if (!(sheet.getParent() instanceof View)) return;
        View parent = (View) sheet.getParent();
        int visibleSheetHeight = Math.max(0, Math.min(sheet.getHeight(),
                parent.getHeight() - Math.round(sheet.getTop() + sheet.getTranslationY())));
        // Material adds padding to the sheet itself when it reaches the status bar.
        // The content starts below that padding, so it cannot use the entire sheet height.
        int visibleHeight = Math.max(0,
                visibleSheetHeight
                        - sheet.getPaddingTop() - sheet.getPaddingBottom());
        if (visibleHeight > 0 && content.getLayoutParams().height != visibleHeight) {
            ViewGroup.LayoutParams params = content.getLayoutParams();
            params.height = visibleHeight;
            content.setLayoutParams(params);
        }
        int[] location = new int[2];
        sheet.getLocationOnScreen(location);
        int contentTop = location[1] + sheet.getPaddingTop();
        int topPadding = dp(8) + Math.max(0, statusBarInset - contentTop);
        content.setPadding(content.getPaddingLeft(), topPadding, content.getPaddingRight(), 0);
        int viewport = windowHeight > 0 ? windowHeight : parent.getHeight();
        if (viewport > 0) updateFooterVisibility(visibleSheetHeight / (float) viewport);
    }

    private void reload() {
        busy = true;
        emptyText.setText(R.string.history_loading);
        updateControls();
        store.read(values -> {
            if (!isShowing()) return;
            entries = values;
            loaded = true;
            busy = false;
            rows.clear();
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    .withLocale(getContext().getResources().getConfiguration().getLocales().get(0));
            LocalDate previous = null;
            Set<Long> existingIds = new HashSet<>();
            for (HistoryStore.Entry entry : entries) {
                existingIds.add(entry.id);
                LocalDate date = Instant.ofEpochMilli(entry.createdAt).atZone(zone).toLocalDate();
                if (!date.equals(previous)) {
                    String label = date.equals(today) ? getContext().getString(R.string.history_today)
                            : date.equals(today.minusDays(1)) ? getContext().getString(R.string.history_yesterday)
                            : date.format(dateFormat);
                    rows.add(label);
                    previous = date;
                }
                rows.add(entry);
            }
            selected.retainAll(existingIds);
            if (rows.isEmpty()) pinnedDateContainer.setVisibility(View.INVISIBLE);
            emptyText.setText(R.string.history_empty);
            adapter.notifyDataSetChanged();
            updateControls();
        }, error -> {
            if (!isShowing()) return;
            busy = false;
            loaded = false;
            emptyText.setText(R.string.history_load_retry);
            updateControls();
            Toast.makeText(getContext(), R.string.history_load_error, Toast.LENGTH_SHORT).show();
        });
    }

    private void deleteHistory() {
        if (busy || !loaded || entries.isEmpty() || (selecting && selected.isEmpty())) return;
        busy = true;
        updateControls();
        Runnable onSuccess = () -> {
            if (!isShowing()) return;
            selected.clear();
            reload();
        };
        java.util.function.Consumer<Exception> onError = error -> {
            if (!isShowing()) return;
            busy = false;
            updateControls();
            Toast.makeText(getContext(), R.string.history_delete_error, Toast.LENGTH_SHORT).show();
        };
        if (selecting) store.delete(selected, onSuccess, onError);
        else store.clear(onSuccess, onError);
    }

    private void updateControls() {
        topDone.setVisibility(selecting ? View.INVISIBLE : View.VISIBLE);
        leftAction.setText(selecting ? R.string.history_done : R.string.history_select);
        leftAction.setEnabled(footerShown && !busy && (selecting || (loaded && !entries.isEmpty())));
        leftAction.setAlpha(leftAction.isEnabled() ? 1f : 0.4f);
        rightAction.setText(selecting ? R.string.history_delete_selected : R.string.history_delete_all);
        boolean canDelete = footerShown && loaded && !busy && !entries.isEmpty()
                && (!selecting || !selected.isEmpty());
        rightAction.setEnabled(canDelete);
        rightAction.setAlpha(canDelete ? 1f : 0.4f);
    }

    private final class RowsAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) {
            Object row = rows.get(position);
            return row instanceof HistoryStore.Entry ? ((HistoryStore.Entry) row).id : -position - 1L;
        }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int position) { return rows.get(position) instanceof String ? 0 : 1; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int position) { return rows.get(position) instanceof HistoryStore.Entry; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Object item = rows.get(position);
            if (item instanceof String) {
                TextView label = convertView instanceof TextView
                        ? (TextView) convertView : dateLabel();
                label.setText((String) item);
                // The pinned copy provides the accessible heading for the current group.
                label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                return label;
            }
            EntryHolder holder;
            if (convertView == null) {
                LinearLayout row = new LinearLayout(getContext());
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(8), dp(12), dp(8), dp(12));
                HistorySelectionView check = new HistorySelectionView(getContext());
                row.addView(check, new LinearLayout.LayoutParams(0, dp(48)));
                LinearLayout details = column();
                TextView expression = text("", 16, secondaryText);
                TextView result = text("", 24, textColor);
                details.addView(expression);
                details.addView(result);
                row.addView(details, new LinearLayout.LayoutParams(0, WRAP, 1f));
                holder = new EntryHolder(check, expression, result);
                row.setTag(holder);
                convertView = row;
            } else holder = (EntryHolder) convertView.getTag();

            HistoryStore.Entry entry = (HistoryStore.Entry) item;
            if (holder.entryId != entry.id) {
                holder.entryId = entry.id;
                holder.setSelectionMode(selecting, false);
                holder.check.bindChecked(selected.contains(entry.id));
            } else if (holder.selectionMode != selecting) {
                holder.setSelectionMode(selecting, false);
            }
            holder.expression.setText(entry.expression);
            holder.result.setText(entry.result);
            updateRowSelection(convertView, entry, false);
            return convertView;
        }
    }

    private TextView dateLabel() {
        TextView label = text("", 15, secondaryText);
        label.setPadding(dp(4), dp(12), dp(4), dp(8));
        ViewCompat.setAccessibilityHeading(label, true);
        return label;
    }

    private final class EntryHolder {
        final HistorySelectionView check;
        final TextView expression;
        final TextView result;
        long entryId = -1;
        boolean selectionMode;
        ValueAnimator modeAnimator;

        EntryHolder(HistorySelectionView check, TextView expression, TextView result) {
            this.check = check;
            this.expression = expression;
            this.result = result;
        }

        void setSelectionMode(boolean mode, boolean animate) {
            selectionMode = mode;
            if (modeAnimator != null) modeAnimator.cancel();
            int startWidth = check.getLayoutParams().width;
            int targetWidth = mode ? dp(48) : 0;
            float startAlpha = check.getAlpha();
            float targetAlpha = mode ? 1f : 0f;
            if (!animate || !ValueAnimator.areAnimatorsEnabled()) {
                setSlotWidth(targetWidth);
                check.setAlpha(targetAlpha);
                return;
            }
            modeAnimator = ValueAnimator.ofFloat(0f, 1f);
            modeAnimator.setDuration(260);
            modeAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
            modeAnimator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                setSlotWidth(Math.round(startWidth + (targetWidth - startWidth) * fraction));
                check.setAlpha(startAlpha + (targetAlpha - startAlpha) * fraction);
            });
            modeAnimator.start();
        }

        private void setSlotWidth(int width) {
            ViewGroup.LayoutParams params = check.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            check.setLayoutParams(params);
        }
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setTypeface(typeface);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, int color) {
        Button button = new Button(getContext());
        button.setTypeface(typeface);
        button.setText(label);
        button.setTextSize(16);
        button.setTextColor(color);
        button.setAllCaps(false);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private GradientDrawable roundedTop(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        float radius = dp(20);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
