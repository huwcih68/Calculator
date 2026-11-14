package vn.edu.course.calculator;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PetalEffectsInstrumentedTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Set<Long> originalHistory = new HashSet<>();
    private MainActivity activity;
    private String animatorScale;
    private HistoryStore store;

    @Before public void setUp() throws Exception {
        animatorScale = shell("settings get global animator_duration_scale").trim();
        shell("settings put global animator_duration_scale 1");
        shell("am start -W -n vn.edu.course.calculator/.MainActivity");
        instrumentation.waitForIdleSync();
        activity = resumed();
        assertNotNull(activity);
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT);
        store = HistoryStore.get(activity);
        for (HistoryStore.Entry entry : history()) originalHistory.add(entry.id);
        click(R.id.buttonClear);
        SystemClock.sleep(900);
    }

    @After public void tearDown() throws Exception {
        try {
            if (activity != null) instrumentation.runOnMainSync(() -> activity.finish());
            if (store != null) {
                Set<Long> created = new HashSet<>();
                for (HistoryStore.Entry entry : history()) {
                    if (!originalHistory.contains(entry.id)) created.add(entry.id);
                }
                CountDownLatch deleted = new CountDownLatch(1);
                store.delete(created, deleted::countDown, error -> deleted.countDown());
                assertTrue(deleted.await(10, TimeUnit.SECONDS));
            }
        } finally {
            if (animatorScale != null) shell("null".equals(animatorScale)
                    ? "settings delete global animator_duration_scale"
                    : "settings put global animator_duration_scale " + animatorScale);
        }
    }

    @Test public void touchCancellationAndRapidInputKeepCorrectState() throws Exception {
        touch(R.id.button7, MotionEvent.ACTION_DOWN);
        SystemClock.sleep(140);
        instrumentation.runOnMainSync(() -> {
            View key = activity.findViewById(R.id.button7);
            assertEquals(0.94f, key.getScaleX(), 0.015f);
        });
        capture("petals-pressed");
        touch(R.id.button7, MotionEvent.ACTION_CANCEL);
        SystemClock.sleep(200);
        assertText(R.id.expressionText, "");
        instrumentation.runOnMainSync(() ->
                assertEquals(1f, activity.findViewById(R.id.button7).getScaleX(), 0.01f));

        // A real touch completes once, while its release animation is still running.
        touch(R.id.button2, MotionEvent.ACTION_DOWN);
        SystemClock.sleep(90);
        touch(R.id.button2, MotionEvent.ACTION_UP);
        instrumentation.waitForIdleSync();
        assertText(R.id.expressionText, "2");
        click(R.id.buttonAdd);
        click(R.id.button3);
        click(R.id.buttonEquals);
        assertText(R.id.resultText, "5");
        SystemClock.sleep(210);
        capture("petals-result");
        // Edit before the result effect finishes, then clear and immediately type again.
        click(R.id.button4);
        click(R.id.button5);
        assertText(R.id.expressionText, "45");
        assertText(R.id.resultText, "");
        click(R.id.buttonClear);
        assertText(R.id.expressionText, "");
        SystemClock.sleep(160);
        capture("petals-clear");
        click(R.id.button6);
        click(R.id.button7);
        assertText(R.id.expressionText, "67");
        SystemClock.sleep(1000);
        instrumentation.runOnMainSync(() -> {
            assertEquals(1f, activity.findViewById(R.id.button2).getScaleX(), 0.001f);
            assertEquals(0f, activity.findViewById(R.id.resultText).getTranslationY(), 0.001f);
        });
        click(R.id.buttonEquals);
        SystemClock.sleep(1000);
        assertText(R.id.resultText, "67");
        instrumentation.runOnMainSync(() ->
                assertEquals(1f, activity.findViewById(R.id.resultText).getAlpha(), 0.001f));
    }

    @Test public void rotationDuringResultAnimationRetainsResultAndAllKeysWork() throws Exception {
        click(R.id.button8);
        click(R.id.buttonMultiply);
        click(R.id.button7);
        click(R.id.buttonEquals);
        rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE);
        assertText(R.id.expressionText, "8×7");
        assertText(R.id.resultText, "56");
        instrumentation.runOnMainSync(() -> assertNotNull(activity.findViewById(R.id.buttonHistory)));
        click(R.id.buttonAdd);
        click(R.id.button1);
        click(R.id.buttonEquals);
        assertText(R.id.resultText, "57");
        SystemClock.sleep(210);
        capture("petals-landscape");
        click(R.id.buttonClear);
        click(R.id.button9);
        assertText(R.id.expressionText, "9");
        rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT);
        assertText(R.id.expressionText, "9");
        assertText(R.id.resultText, "");
    }

    @Test public void disabledSystemAnimationsStillShowResultImmediately() throws Exception {
        shell("settings put global animator_duration_scale 0");
        SystemClock.sleep(250);
        click(R.id.button6);
        click(R.id.buttonDivide);
        click(R.id.button2);
        click(R.id.buttonEquals);
        assertText(R.id.resultText, "3");
        instrumentation.runOnMainSync(() -> {
            View result = activity.findViewById(R.id.resultText);
            assertEquals(1f, result.getAlpha(), 0.001f);
            assertEquals(0f, result.getTranslationY(), 0.001f);
            assertEquals(1f, activity.findViewById(R.id.buttonEquals).getScaleX(), 0.001f);
        });
        click(R.id.buttonClear);
        click(R.id.button1);
        assertText(R.id.expressionText, "1");
    }

    private void click(int id) {
        instrumentation.runOnMainSync(() -> assertTrue(activity.findViewById(id).performClick()));
    }

    private void touch(int id, int action) {
        instrumentation.runOnMainSync(() -> {
            View view = activity.findViewById(id);
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, action,
                    view.getWidth() * 0.35f, view.getHeight() * 0.4f, 0);
            view.dispatchTouchEvent(event);
            event.recycle();
        });
    }

    private void assertText(int id, String expected) {
        instrumentation.runOnMainSync(() ->
                assertEquals(expected, ((TextView) activity.findViewById(id)).getText().toString()));
    }

    private MainActivity resumed() {
        AtomicReference<MainActivity> found = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            for (Activity candidate : ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)) {
                if (candidate instanceof MainActivity) found.set((MainActivity) candidate);
            }
        });
        return found.get();
    }

    private void rotate(int requested, int expected) {
        instrumentation.runOnMainSync(() -> activity.setRequestedOrientation(requested));
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            instrumentation.waitForIdleSync();
            MainActivity current = resumed();
            if (current != null && current.getResources().getConfiguration().orientation == expected) {
                activity = current;
                SystemClock.sleep(180);
                return;
            }
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        fail("Orientation did not change");
    }

    private List<HistoryStore.Entry> history() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<List<HistoryStore.Entry>> entries = new AtomicReference<>();
        store.read(value -> { entries.set(value); completed.countDown(); }, error -> completed.countDown());
        assertTrue(completed.await(10, TimeUnit.SECONDS));
        assertNotNull(entries.get());
        return entries.get();
    }

    private String shell(String command) throws Exception {
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.getUiAutomation().executeShellCommand(command))) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = stream.read(buffer)) != -1) bytes.write(buffer, 0, length);
            return bytes.toString("UTF-8");
        }
    }

    private void capture(String name) throws Exception {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("capturePetals"))) return;
        AtomicReference<Bitmap> rendered = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            Bitmap frame = Bitmap.createBitmap(decor.getWidth(), decor.getHeight(), Bitmap.Config.ARGB_8888);
            decor.draw(new Canvas(frame));
            rendered.set(frame);
        });
        Bitmap bitmap = rendered.get();
        assertNotNull(bitmap);
        try (FileOutputStream output = new FileOutputStream(
                new File(activity.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally {
            bitmap.recycle();
        }
    }
}
