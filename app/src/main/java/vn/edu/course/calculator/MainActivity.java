package vn.edu.course.calculator;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.widget.Toast;
import vn.edu.course.calculator.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_EXPRESSION = "expression";
    private static final String STATE_RESULT = "result";
    private static final String STATE_EVALUATED = "evaluated";
    private static final String STATE_ERROR = "error";

    private ActivityMainBinding binding;
    private String expression = "";
    private String result = "";
    private boolean evaluated;
    private int errorResId;

    private HistorySheet historySheet;
    private PetalEffects petalEffects;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        petalEffects = new PetalEffects(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        if (savedInstanceState != null) {
            expression = savedInstanceState.getString(STATE_EXPRESSION, "");
            result = savedInstanceState.getString(STATE_RESULT, "");
            evaluated = savedInstanceState.getBoolean(STATE_EVALUATED, false);
            errorResId = savedInstanceState.getInt(STATE_ERROR, 0);
        }
        binding.expressionText.addOnLayoutChangeListener(
                (v, left, top, right, bottom,
                 oldLeft, oldTop, oldRight, oldBottom) -> {
                    int horizontalGravity = android.view.Gravity.getAbsoluteGravity(
                            binding.expressionText.getGravity(),
                            v.getLayoutDirection()
                    ) & android.view.Gravity.HORIZONTAL_GRAVITY_MASK;

                    v.setPivotX(
                            horizontalGravity == android.view.Gravity.RIGHT
                                    ? v.getWidth()
                                    : 0f
                    );

                    // Màn hình dọc căn dưới, màn hình ngang căn trên.
                    boolean bottomAligned =
                            (binding.expressionText.getGravity()
                                    & android.view.Gravity.VERTICAL_GRAVITY_MASK)
                                    == android.view.Gravity.BOTTOM;

                    v.setPivotY(bottomAligned ? v.getHeight() : 0f);
                });

        bindKeys();
        render();
    }

    private void bindKeys() {
        binding.buttonHistory.setOnClickListener(v -> {
            v.setEnabled(false); // Tránh bấm liên tục.

            v.animate()
                    .alpha(0.4f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        v.animate()
                                .alpha(1f)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    v.setEnabled(true);
                                    showHistory();
                                })
                                .start();
                    })
                    .start();
        });
        // Các phím số và phép toán cùng có hành vi nối ký tự.
        for (int i = 0; i < binding.keypad.getChildCount(); i++) {
            View child = binding.keypad.getChildAt(i);
            if (child instanceof Button) {
                Button key = (Button) child;
                petalEffects.bind(key, () -> {
                    int id = key.getId();
                    if (id == R.id.buttonClear) clear();
                    else if (id == R.id.buttonDelete) deleteLast();
                    else if (id == R.id.buttonEquals) calculate();
                    else append(key.getText().toString());
                });
            }
        }

        binding.buttonDelete.setOnLongClickListener(v -> {
            clear();
            return true;
        });
        binding.resultText.setOnLongClickListener(v -> copyResult());
        ViewCompat.replaceAccessibilityAction(binding.resultText,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK,
                getString(R.string.copy_result), (view, arguments) -> copyResult());
    }

    private boolean copyResult() {
        if (!evaluated || errorResId != 0 || result.isEmpty()) return false;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) return false;
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.copy_result), result));
        // Android 13 and newer already show a clipboard confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, R.string.result_copied, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private void append(String key) {
        boolean operator = key.equals("+") || key.equals("−")
                || key.equals("×") || key.equals("÷");

        if (evaluated) {
            // 2+3=5, bấm × rồi 4: tiếp tục từ 5, được 20.
            // Nếu bấm chữ số hoặc dấu chấm: bắt đầu biểu thức mới.
            expression = operator ? result : "";
        }
        evaluated = false;
        errorResId = 0;
        result = "";
        expression += key;
        render();
    }

    private void calculate() {
        evaluated = false;
        errorResId = 0;
        result = "";

        try {
            result = CalculatorEngine.evaluate(expression);
            evaluated = true;

            HistoryStore.get(this).add(expression, result, error ->
                    Toast.makeText(getApplicationContext(),
                            R.string.history_save_error, Toast.LENGTH_SHORT).show());
        } catch (ArithmeticException e) {
            errorResId = R.string.error_divide_zero;
        } catch (IllegalArgumentException e) {
            errorResId = R.string.error_invalid;
        }

        render(true);
    }

    private void deleteLast() {
        if (!expression.isEmpty()) {
            expression = expression.substring(0, expression.length() - 1);
        }
        result = "";
        errorResId = 0;
        evaluated = false;
        render();
    }

    private void clear() {
        petalEffects.dissolve(binding.expressionText);
        expression = "";
        result = "";
        errorResId = 0;
        evaluated = false;
        render();
    }
    private void showHistory() {
        if (isFinishing() || isDestroyed()) return;
        if (historySheet != null && historySheet.isShowing()) return;

        historySheet = new HistorySheet(this, this::restoreHistoryEntry);
        historySheet.show();
    }

    private void restoreHistoryEntry(HistoryStore.Entry entry) {
        expression = entry.expression;
        result = "";
        errorResId = 0;
        evaluated = false;
        render();
    }

    private void render() {
        render(false);
    }

    @Override
    protected void onStop() {
        petalEffects.stop();
        render();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        petalEffects.dispose();
        binding.buttonHistory.animate().cancel();
        binding.expressionText.animate().cancel();
        binding.resultText.animate().cancel();
        if (historySheet != null) {
            historySheet.setDismissWithAnimation(false);
            historySheet.dismiss();
            historySheet = null;
        }
        super.onDestroy();
    }

    private void render(boolean animateResult) {
        // Dừng hiệu ứng cũ nếu người dùng bấm phím nhanh.
        binding.expressionText.animate().cancel();
        binding.resultText.animate().cancel();
        binding.resultText.setTranslationY(0f);

        binding.expressionText.setText(expression);
        binding.resultText.setText(errorResId == 0
                ? result : getString(errorResId));
        binding.resultText.setLongClickable(evaluated && errorResId == 0 && !result.isEmpty());

        float expressionScale = evaluated ? 0.85f : 1f;
        float expressionAlpha = evaluated ? 0.6f : 1f;

        if (animateResult && evaluated && petalEffects.motionEnabled()) {
            // Bắt đầu từ trạng thái phép tính đang được nhập.
            binding.expressionText.setScaleX(1f);
            binding.expressionText.setScaleY(1f);
            binding.expressionText.setAlpha(1f);

            // Phép tính thu nhỏ và mờ nhẹ.
            binding.expressionText.animate()
                    .scaleX(expressionScale)
                    .scaleY(expressionScale)
                    .alpha(expressionAlpha)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Kết quả hiện rõ dần.
            binding.resultText.setAlpha(0f);
            binding.resultText.setTranslationY(10f * getResources().getDisplayMetrics().density);
            binding.resultText.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(320)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            // Nhập/xóa thì khôi phục; xoay màn hình giữ đúng trạng thái.
            binding.expressionText.setScaleX(expressionScale);
            binding.expressionText.setScaleY(expressionScale);
            binding.expressionText.setAlpha(expressionAlpha);
            binding.resultText.setAlpha(1f);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_EXPRESSION, expression);
        outState.putString(STATE_RESULT, result);
        outState.putBoolean(STATE_EVALUATED, evaluated);
        outState.putInt(STATE_ERROR, errorResId);
        super.onSaveInstanceState(outState);
    }
}
