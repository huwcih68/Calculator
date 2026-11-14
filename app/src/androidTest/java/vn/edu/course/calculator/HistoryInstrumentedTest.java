package vn.edu.course.calculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ListView;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isCompletelyDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HistoryInstrumentedTest {
    private Context context;
    private String databaseName;
    private HistoryStore store;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        databaseName = "history-test-" + UUID.randomUUID() + ".db";
        store = new HistoryStore(context, databaseName);
    }

    @After
    public void tearDown() {
        store.close();
        context.deleteDatabase(databaseName);
    }

    @Test
    public void recallEditsExpressionAndLongPressCopiesOnlyValidResult() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        HistoryStore liveStore = HistoryStore.get(context);
        Set<Long> originalIds = new HashSet<>();
        for (HistoryStore.Entry entry : read(liveStore)) originalIds.add(entry.id);
        try {
            onView(withId(R.id.buttonClear)).perform(activate());
            onView(withId(R.id.buttonSubtract)).perform(activate());
            onView(withId(R.id.button1)).perform(activate());
            onView(withId(R.id.button2)).perform(activate());
            onView(withId(R.id.buttonDivide)).perform(activate());
            onView(withId(R.id.button8)).perform(activate());
            onView(withId(R.id.buttonEquals)).perform(activate());
            onView(withId(R.id.resultText)).check(matches(withText("-1.5")));
            List<HistoryStore.Entry> saved = read(liveStore);
            long recalledId = saved.stream().filter(entry -> !originalIds.contains(entry.id)
                    && entry.expression.equals("−12÷8")).findFirst().get().id;

            onView(withId(R.id.resultText)).perform(new ViewAction() {
                @Override public Matcher<View> getConstraints() { return isDisplayed(); }
                @Override public String getDescription() { return "copy the full numeric result"; }
                @Override public void perform(UiController controller, View view) {
                    assertTrue(view.isLongClickable());
                    assertTrue(view.performLongClick());
                    ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                    assertNotNull(clipboard.getPrimaryClip());
                    assertEquals("-1.5", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
                }
            });

            onView(withId(R.id.buttonClear)).perform(activate());
            onView(withId(R.id.button1)).perform(activate());
            onView(withId(R.id.buttonDivide)).perform(activate());
            onView(withId(R.id.button0)).perform(activate());
            onView(withId(R.id.buttonEquals)).perform(activate());
            onView(withId(R.id.resultText)).check(matches(withText(R.string.error_divide_zero)));
            assertResultCannotCopy(activity);

            // Opening History includes the toolbar animation and an asynchronous database query.
            onView(withId(R.id.buttonHistory)).perform(activate(1000));
            read(liveStore);
            onData(entryWithId(recalledId)).inAdapterView(withId(R.id.history_list))
                    .perform(activate(500));
            onView(withId(R.id.expressionText)).check(matches(withText("−12÷8")));
            onView(withId(R.id.resultText)).check(matches(withText("")));
            assertEquals("Recalling must not save another entry", saved.size(), read(liveStore).size());
            assertResultCannotCopy(activity);

            onView(withId(R.id.button2)).perform(activate());
            onView(withId(R.id.expressionText)).check(matches(withText("−12÷82")));
            onView(withId(R.id.buttonDelete)).perform(activate());
            onView(withId(R.id.buttonEquals)).perform(activate());
            onView(withId(R.id.resultText)).check(matches(withText("-1.5")));
        } finally {
            Set<Long> createdIds = new HashSet<>();
            for (HistoryStore.Entry entry : read(liveStore)) {
                if (!originalIds.contains(entry.id) && entry.expression.equals("−12÷8")) {
                    createdIds.add(entry.id);
                }
            }
            complete((done, fail) -> liveStore.delete(createdIds, done, fail));
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    private void assertResultCannotCopy(MainActivity activity) {
        onView(withId(R.id.resultText)).check((view, error) -> {
            if (error != null) throw error;
            ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
            ClipData before = clipboard.getPrimaryClip();
            assertFalse(view.isLongClickable());
            assertFalse(view.performLongClick());
            assertNotNull(before);
            assertEquals(before.getItemAt(0).getText().toString(),
                    clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        });
    }

    @Test
    public void reopeningAndDeletingKeepUnselectedDuplicate() throws Exception {
        AtomicReference<Exception> saveError = new AtomicReference<>();
        store.add("2+3", "5", saveError::set);
        store.add("2+3", "5", saveError::set);
        store.add("8×4", "32", saveError::set);
        List<HistoryStore.Entry> original = read();
        assertNull(saveError.get());
        assertEquals(3, original.size());
        assertEquals("8×4", original.get(0).expression);
        long selectedId = original.get(1).id;
        long unselectedDuplicateId = original.get(2).id;
        assertNotEquals(selectedId, unselectedDuplicateId);
        assertTrue(original.get(0).createdAt > 0);

        store.close();
        store = new HistoryStore(context, databaseName);
        assertEquals(3, read().size());

        complete((done, fail) -> store.delete(Collections.singleton(selectedId), done, fail));
        List<HistoryStore.Entry> remaining = read();
        assertEquals(2, remaining.size());
        assertTrue(remaining.stream().anyMatch(entry -> entry.id == unselectedDuplicateId));
        assertFalse(remaining.stream().anyMatch(entry -> entry.id == selectedId));

        complete((done, fail) -> store.delete(Collections.emptySet(), done, fail));
        assertEquals(2, read().size());
        complete((done, fail) -> store.clear(done, fail));
        store.close();
        store = new HistoryStore(context, databaseName);
        assertTrue(read().isEmpty());
    }

    @Test
    public void sheetGroupsDaysAndDeletesOnlyCheckedRow() throws Exception {
        LocalDate today = LocalDate.now();
        long unselectedId = seed("2+3", "5", today);
        long selectedId = seed("2+3", "5", today);
        long yesterdayId = seed("8×4", "32", today.minusDays(1));
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        AtomicReference<HistorySheet> dialog = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                HistorySheet sheet = new HistorySheet(activity, store);
                dialog.set(sheet);
                sheet.show();
            });
            read(); // FIFO: the sheet's initial query completes before this callback.
            awaitSheetLayout(dialog.get());
            instrumentation.runOnMainSync(() -> {
                ViewGroup sheet = dialog.get().findViewById(
                        com.google.android.material.R.id.design_bottom_sheet);
                assertNotNull(sheet);
                assertEquals(BottomSheetBehavior.STATE_COLLAPSED, dialog.get().getBehavior().getState());
                assertEquals(activity.getWindow().getDecorView().getHeight() / 2,
                        sheet.getChildAt(0).getLayoutParams().height);
            });
            onData(equalTo(activity.getString(R.string.history_today))).inAdapterView(isAssignableFrom(ListView.class))
                    .check(matches(isDisplayed()));
            onData(equalTo(activity.getString(R.string.history_yesterday))).inAdapterView(isAssignableFrom(ListView.class))
                    .check(matches(isDisplayed()));
            onView(withText(R.string.history_select)).perform(activate());
            onView(withText(R.string.history_delete_selected)).check(matches(not(isEnabled())));
            onData(entryWithId(selectedId)).inAdapterView(isAssignableFrom(ListView.class))
                    .perform(activate());
            onView(withText(R.string.history_delete_selected)).check(matches(isEnabled())).perform(activate());
            List<HistoryStore.Entry> remaining = read();
            assertEquals(2, remaining.size());
            assertTrue(remaining.stream().anyMatch(entry -> entry.id == unselectedId));
            assertTrue(remaining.stream().anyMatch(entry -> entry.id == yesterdayId));
            read(); // Wait for the post-delete reload before clicking the mode button.
            onView(allOf(withText(R.string.history_done), isDisplayed())).perform(activate());
            onView(withText(R.string.history_select)).check(matches(isCompletelyDisplayed()));
            onView(withText(R.string.history_delete_all)).check(matches(isCompletelyDisplayed()));
            onView(allOf(withText(R.string.history_done), isDisplayed())).perform(activate());
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) {
                    dialog.get().setDismissWithAnimation(false);
                    dialog.get().dismiss();
                }
                activity.finish();
            });
        }
    }

    @Test
    public void footerFollowsThresholdAndSheetSlidesInAndOut() throws Exception {
        seed("2+3", "5", LocalDate.now());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        AtomicReference<HistorySheet> dialog = new AtomicReference<>();
        List<Float> entrancePositions = new ArrayList<>();
        try {
            instrumentation.runOnMainSync(() -> {
                HistorySheet history = new HistorySheet(activity, store);
                dialog.set(history);
                View sheet = history.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                sheet.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
                    @Override public void onDraw() {
                        if (sheet.getTranslationY() > 0 || !entrancePositions.isEmpty()) {
                            entrancePositions.add(sheet.getTop() + sheet.getTranslationY());
                        }
                    }
                });
                history.show();
            });
            read();
            awaitSheetLayout(dialog.get());
            if (ValueAnimator.areAnimatorsEnabled()) {
                assertTrue("Entrance did not move up", entrancePositions.size() > 1);
                assertTrue(entrancePositions.get(0)
                        > entrancePositions.get(entrancePositions.size() - 1));
            }
            onView(withId(R.id.history_footer)).check(matches(isCompletelyDisplayed()));
            AtomicReference<SheetDrag> gesture = new AtomicReference<>();
            instrumentation.runOnMainSync(() -> {
                gesture.set(new SheetDrag(dialog.get()));
                gesture.get().moveToVisibleFraction(0.2f);
            });
            waitForSelectionMotion();
            onView(withId(R.id.history_footer)).check(matches(not(isDisplayed())));
            onView(withText(R.string.history_select)).check(matches(not(isEnabled())));

            instrumentation.runOnMainSync(() -> gesture.get().moveToVisibleFraction(0.4f));
            waitForSelectionMotion();
            onView(withId(R.id.history_footer)).check(matches(isCompletelyDisplayed()));
            onView(withText(R.string.history_select)).check(matches(isEnabled()));

            CountDownLatch expanded = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                dialog.get().getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override public void onStateChanged(View sheet, int state) {
                        if (state == BottomSheetBehavior.STATE_EXPANDED) expanded.countDown();
                    }
                    @Override public void onSlide(View sheet, float offset) {}
                });
                gesture.get().release();
            });
            assertTrue("Sheet did not expand after upward release", expanded.await(5, TimeUnit.SECONDS));
            waitForSelectionMotion();
            List<Integer> closingPositions = new ArrayList<>();
            CountDownLatch hidden = new CountDownLatch(1);
            instrumentation.runOnMainSync(() -> {
                View sheet = dialog.get().findViewById(com.google.android.material.R.id.design_bottom_sheet);
                closingPositions.add(sheet.getTop());
                dialog.get().getBehavior().addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                    @Override public void onStateChanged(View view, int state) {
                        if (state == BottomSheetBehavior.STATE_HIDDEN) hidden.countDown();
                    }
                    @Override public void onSlide(View view, float offset) {
                        closingPositions.add(view.getTop());
                    }
                });
            });
            onView(allOf(withText(R.string.history_done), isDisplayed())).perform(activate());
            assertTrue("Done did not slide the sheet down", hidden.await(5, TimeUnit.SECONDS));
            instrumentation.waitForIdleSync();
            assertFalse(dialog.get().isShowing());
            assertTrue(closingPositions.get(closingPositions.size() - 1) > closingPositions.get(0));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) {
                    dialog.get().setDismissWithAnimation(false);
                    dialog.get().dismiss();
                }
                activity.finish();
            });
        }
    }

    private final class SheetDrag {
        private final ViewGroup sheet;
        private final ViewGroup parent;
        private final float x;
        private float y;
        private final long downTime = SystemClock.uptimeMillis();
        private long eventTime = downTime;

        SheetDrag(HistorySheet dialog) {
            sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            parent = (ViewGroup) sheet.getParent();
            ViewGroup content = (ViewGroup) sheet.getChildAt(0);
            x = sheet.getLeft() + sheet.getWidth() / 2f;
            y = sheet.getTop() + sheet.getPaddingTop() + content.getPaddingTop()
                    + content.getChildAt(0).getHeight() / 2f;
            send(MotionEvent.ACTION_DOWN, y);
        }

        void moveToVisibleFraction(float fraction) {
            float distance = parent.getHeight() * (1f - fraction) - sheet.getTop();
            float start = y;
            for (int step = 1; step <= 12; step++) {
                send(MotionEvent.ACTION_MOVE, start + distance * step / 12f);
            }
        }

        void release() {
            send(MotionEvent.ACTION_UP, y);
        }

        private void send(int action, float position) {
            eventTime += 20;
            MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, position, 0);
            parent.dispatchTouchEvent(event);
            event.recycle();
            y = position;
        }
    }

    @Test
    public void selectionMotionHandlesRapidTapsAndRecycledRows() throws Exception {
        LocalDate today = LocalDate.now();
        long oldest = 0;
        long newest = 0;
        for (int index = 0; index < 20; index++) {
            newest = seed(index + "+1", Integer.toString(index + 1), today);
            if (index == 0) oldest = newest;
        }
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        AtomicReference<HistorySheet> dialog = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                HistorySheet sheet = new HistorySheet(activity, store);
                dialog.set(sheet);
                sheet.show();
            });
            read();
            awaitSheetLayout(dialog.get());
            onView(withText(R.string.history_select)).perform(activate());
            onView(allOf(withText(R.string.history_done), isDisplayed())).perform(activate());
            onView(withText(R.string.history_select)).perform(activate());
            onData(entryWithId(newest)).inAdapterView(withId(R.id.history_list))
                    .perform(activate(), activate(), activate());
            waitForSelectionMotion();
            assertSelectionRow(newest, true, true);

            // Scroll out and back to exercise recycled views while keeping the selected ID.
            assertSelectionRow(oldest, true, false);
            assertSelectionRow(newest, true, true);
            onView(allOf(withText(R.string.history_done), isDisplayed())).perform(activate());
            waitForSelectionMotion();
            assertSelectionRow(newest, false, false);
            onView(withText(R.string.history_select)).perform(activate());
            waitForSelectionMotion();
            assertSelectionRow(newest, true, false);
            assertEquals(20, read().size());
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) {
                    dialog.get().setDismissWithAnimation(false);
                    dialog.get().dismiss();
                }
                activity.finish();
            });
        }
    }

    private void waitForSelectionMotion() {
        onView(withId(R.id.history_list)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(ListView.class); }
            @Override public String getDescription() { return "wait for selection motion to settle"; }
            @Override public void perform(UiController controller, View view) {
                controller.loopMainThreadForAtLeast(500);
            }
        });
    }

    private void assertSelectionRow(long id, boolean selectionMode, boolean checked) {
        onData(entryWithId(id)).inAdapterView(withId(R.id.history_list)).check((view, error) -> {
            if (error != null) throw error;
            HistorySelectionView check = (HistorySelectionView) ((ViewGroup) view).getChildAt(0);
            int slotWidth = selectionMode
                    ? Math.round(48 * view.getResources().getDisplayMetrics().density) : 0;
            assertEquals(slotWidth, check.getLayoutParams().width);
            assertEquals(selectionMode ? 1f : 0f, check.getAlpha(), 0.001f);
            assertEquals(checked, check.isChecked());
        });
    }

    @Test
    public void dateHeadingStaysPinnedAndChangesWithScrolledDay() throws Exception {
        LocalDate today = LocalDate.now();
        for (int index = 0; index < 16; index++) {
            seed(index + "+1", Integer.toString(index + 1), today);
            seed(index + "+2", Integer.toString(index + 2), today.minusDays(1));
        }
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        AtomicReference<HistorySheet> dialog = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                HistorySheet sheet = new HistorySheet(activity, store);
                dialog.set(sheet);
                sheet.show();
            });
            read();
            awaitSheetLayout(dialog.get());
            onView(withId(R.id.history_pinned_date))
                    .check(matches(withText(R.string.history_today)))
                    .check(matches(isCompletelyDisplayed()));
            int[] initialLocation = new int[2];
            instrumentation.runOnMainSync(() -> dialog.get().findViewById(R.id.history_pinned_date)
                    .getLocationOnScreen(initialLocation));

            scrollHistoryTo(8);
            onView(withId(R.id.history_pinned_date)).check(matches(withText(R.string.history_today)));
            instrumentation.runOnMainSync(() -> {
                int[] location = new int[2];
                dialog.get().findViewById(R.id.history_pinned_date).getLocationOnScreen(location);
                assertEquals(initialLocation[1], location[1]);
            });

            scrollHistoryTo(22);
            onView(withId(R.id.history_pinned_date))
                    .check(matches(withText(R.string.history_yesterday)))
                    .check(matches(isCompletelyDisplayed()));
            instrumentation.runOnMainSync(() -> {
                int[] location = new int[2];
                dialog.get().findViewById(R.id.history_pinned_date).getLocationOnScreen(location);
                assertEquals(initialLocation[1], location[1]);
            });
            dragAndAwait(dialog.get(), true, BottomSheetBehavior.STATE_EXPANDED);
            scrollHistoryTo(8);
            onView(withId(R.id.history_pinned_date))
                    .check(matches(withText(R.string.history_today)))
                    .check(matches(isCompletelyDisplayed()));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) {
                    dialog.get().setDismissWithAnimation(false);
                    dialog.get().dismiss();
                }
                activity.finish();
            });
        }
    }

    private void scrollHistoryTo(int position) {
        onView(withId(R.id.history_list)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isAssignableFrom(ListView.class); }
            @Override public String getDescription() { return "scroll history to row " + position; }
            @Override public void perform(UiController controller, View view) {
                ((ListView) view).setSelectionFromTop(position, 0);
                controller.loopMainThreadForAtLeast(100);
            }
        });
    }

    @Test
    public void draggingHandleSnapsBetweenHalfAndFullScreen() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        MainActivity activity = launchActivity(instrumentation);
        AtomicReference<HistorySheet> dialog = new AtomicReference<>();
        try {
            instrumentation.runOnMainSync(() -> {
                HistorySheet sheet = new HistorySheet(activity, store);
                dialog.set(sheet);
                sheet.show();
            });
            read();
            awaitSheetLayout(dialog.get());
            dragAndAwait(dialog.get(), true, BottomSheetBehavior.STATE_EXPANDED);
            instrumentation.runOnMainSync(() -> {
                ViewGroup sheet = dialog.get().findViewById(
                        com.google.android.material.R.id.design_bottom_sheet);
                View parent = (View) sheet.getParent();
                assertTrue(sheet.getTop() <= parent.getHeight() / 10);
                assertEquals(parent.getHeight() - sheet.getTop()
                                - sheet.getPaddingTop() - sheet.getPaddingBottom(),
                        sheet.getChildAt(0).getLayoutParams().height);
            });
            onView(withText(R.string.history_select)).check(matches(isCompletelyDisplayed()));
            onView(withText(R.string.history_delete_all)).check(matches(isCompletelyDisplayed()));

            dragAndAwait(dialog.get(), false, BottomSheetBehavior.STATE_COLLAPSED);
            instrumentation.runOnMainSync(() -> {
                ViewGroup sheet = dialog.get().findViewById(
                        com.google.android.material.R.id.design_bottom_sheet);
                assertEquals(activity.getWindow().getDecorView().getHeight() / 2,
                        sheet.getChildAt(0).getLayoutParams().height);
            });
            onView(withText(R.string.history_select)).check(matches(isDisplayed()));
            onView(withText(R.string.history_delete_all)).check(matches(isDisplayed()));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if (dialog.get() != null) {
                    dialog.get().setDismissWithAnimation(false);
                    dialog.get().dismiss();
                }
                activity.finish();
            });
        }
    }

    private void awaitSheetLayout(HistorySheet dialog) throws Exception {
        CountDownLatch laidOut = new CountDownLatch(1);
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        AtomicReference<View> sheetRef = new AtomicReference<>();
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> {
            ViewGroup sheet = (ViewGroup) sheetRef.get();
            View parent = (View) sheet.getParent();
            if (sheet.getHeight() > 0 && sheet.getChildAt(0).getHeight()
                    == parent.getHeight() - sheet.getTop()
                            - sheet.getPaddingTop() - sheet.getPaddingBottom()) laidOut.countDown();
        };
        instrumentation.runOnMainSync(() -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            sheetRef.set(sheet);
            sheet.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            listener.onGlobalLayout();
        });
        try {
            assertTrue("History layout timed out", laidOut.await(5, TimeUnit.SECONDS));
        } finally {
            instrumentation.runOnMainSync(() -> sheetRef.get().getViewTreeObserver()
                    .removeOnGlobalLayoutListener(listener));
        }
        // The dialog's window entrance animation can still be running after layout.
        onView(withText(R.string.menu_history)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "wait for the sheet entrance animation"; }
            @Override public void perform(UiController controller, View view) {
                controller.loopMainThreadForAtLeast(500);
            }
        });
    }

    private void dragAndAwait(HistorySheet dialog, boolean upward, int expectedState)
            throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        CountDownLatch settled = new CountDownLatch(1);
        StringBuilder trace = new StringBuilder();
        BottomSheetBehavior.BottomSheetCallback callback = new BottomSheetBehavior.BottomSheetCallback() {
            @Override public void onStateChanged(View sheet, int state) {
                trace.append(" state=").append(state).append(" top=").append(sheet.getTop());
                if (state == expectedState) settled.countDown();
            }
            @Override public void onSlide(View sheet, float offset) {}
        };
        instrumentation.runOnMainSync(() -> {
            dialog.getBehavior().addBottomSheetCallback(callback);
            ViewGroup sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            ViewGroup parent = (ViewGroup) sheet.getParent();
            ViewGroup content = (ViewGroup) sheet.getChildAt(0);
            View handle = content.getChildAt(0);
            float x = sheet.getLeft() + sheet.getWidth() / 2f;
            float y = sheet.getTop() + content.getPaddingTop() + handle.getHeight() / 2f;
            float distance = parent.getHeight() * (upward ? -0.3f : 0.3f);
            trace.append(" start=").append(x).append(',').append(y)
                    .append(" parentHeight=").append(parent.getHeight())
                    .append(" sheetHeight=").append(sheet.getHeight());
            long downTime = SystemClock.uptimeMillis();
            for (int step = 0; step <= 12; step++) {
                int action = step == 0 ? MotionEvent.ACTION_DOWN
                        : step == 12 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                MotionEvent event = MotionEvent.obtain(downTime, downTime + step * 20L,
                        action, x, y + distance * step / 12f, 0);
                trace.append(' ').append(step).append(':').append(parent.dispatchTouchEvent(event))
                        .append('/').append(sheet.getTop());
                event.recycle();
            }
        });
        try {
            boolean reachedState = settled.await(5, TimeUnit.SECONDS);
            assertTrue("History did not snap to state " + expectedState + ": " + trace, reachedState);
            instrumentation.waitForIdleSync();
        } finally {
            instrumentation.runOnMainSync(() -> dialog.getBehavior().removeBottomSheetCallback(callback));
        }
    }

    private ViewAction activate() {
        return activate(0);
    }

    private ViewAction activate(long settleMillis) {
        // Exercise the app's real click handlers without requiring MIUI input-injection access.
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() { return isDisplayed(); }
            @Override public String getDescription() { return "activate the view's click handler"; }
            @Override public void perform(UiController controller, View view) {
                if (view.getParent() instanceof ListView) {
                    ListView list = (ListView) view.getParent();
                    int position = list.getPositionForView(view);
                    assertTrue(list.performItemClick(view, position,
                            list.getAdapter().getItemId(position)));
                } else assertTrue(view.performClick());
                if (settleMillis > 0) controller.loopMainThreadForAtLeast(settleMillis);
                controller.loopMainThreadUntilIdle();
            }
        };
    }

    private MainActivity launchActivity(Instrumentation instrumentation) throws Exception {
        // The shell can bring the app forward on devices that block background launches.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(
                        "am start -W -n vn.edu.course.calculator/.MainActivity"))) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) != -1) output.write(buffer, 0, length);
        }
        instrumentation.waitForIdleSync();
        AtomicReference<MainActivity> activity = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (candidate instanceof MainActivity) activity.set((MainActivity) candidate);
            }
        });
        assertNotNull("Calculator did not open: " + output.toString("UTF-8"), activity.get());
        return activity.get();
    }

    private long seed(String expression, String result, LocalDate date) {
        ContentValues values = new ContentValues();
        values.put("expression", expression);
        values.put("result", result);
        values.put("created_at", date.atTime(12, 0).atZone(ZoneId.systemDefault())
                .toInstant().toEpochMilli());
        return store.getWritableDatabase().insertOrThrow("history", null, values);
    }

    private List<HistoryStore.Entry> read() throws Exception {
        return read(store);
    }

    private List<HistoryStore.Entry> read(HistoryStore source) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<List<HistoryStore.Entry>> result = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        source.read(entries -> {
            result.set(entries);
            finished.countDown();
        }, failure -> {
            error.set(failure);
            finished.countDown();
        });
        assertTrue("History operation timed out", finished.await(10, TimeUnit.SECONDS));
        if (error.get() != null) throw error.get();
        return result.get();
    }

    private interface Operation {
        void start(Runnable onSuccess, Consumer<Exception> onError);
    }

    private void complete(Operation operation) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Exception> error = new AtomicReference<>();
        operation.start(finished::countDown, failure -> {
            error.set(failure);
            finished.countDown();
        });
        assertTrue("History operation timed out", finished.await(10, TimeUnit.SECONDS));
        if (error.get() != null) throw error.get();
    }

    private TypeSafeMatcher<Object> entryWithId(long id) {
        return new TypeSafeMatcher<Object>() {
            @Override protected boolean matchesSafely(Object value) {
                return value instanceof HistoryStore.Entry && ((HistoryStore.Entry) value).id == id;
            }
            @Override public void describeTo(Description description) {
                description.appendText("History entry with ID ").appendValue(id);
            }
        };
    }
}
