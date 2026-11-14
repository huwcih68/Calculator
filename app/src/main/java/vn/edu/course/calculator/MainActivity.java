package vn.edu.course.calculator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import android.widget.Toast;
import java.util.ArrayList;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

    private static final String STATE_HISTORY = "history";
    private static final int MAX_HISTORY = 50;

    private final ArrayList<String> history = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

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
            ArrayList<String> savedHistory =
                    savedInstanceState.getStringArrayList(STATE_HISTORY);

            if (savedHistory != null) {
                history.addAll(savedHistory);
            }
        }

        bindKeys();
        render();
    }

    private void bindKeys() {
        binding.buttonHistory.setOnClickListener(v -> showHistory());
        // Các phím số và phép toán cùng có hành vi nối ký tự.
        for (int i = 0; i < binding.keypad.getChildCount(); i++) {
            View child = binding.keypad.getChildAt(i);
            if (child instanceof Button) {
                child.setOnClickListener(v ->
                        append(((Button) v).getText().toString()));
            }
        }

        // Ghi đè listener cho ba phím có hành vi riêng.
        binding.buttonClear.setOnClickListener(v -> clear());
        binding.buttonDelete.setOnClickListener(v -> deleteLast());
        binding.buttonEquals.setOnClickListener(v -> calculate());
        binding.buttonDelete.setOnLongClickListener(v -> {
            clear();
            return true;
        });
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

            // Thêm phép tính mới vào đầu danh sách.
            history.add(0, expression + " = " + result);

            // Chỉ giữ 50 phép tính gần nhất.
            if (history.size() > MAX_HISTORY) {
                history.remove(history.size() - 1);
            }
        } catch (ArithmeticException e) {
            errorResId = R.string.error_divide_zero;
        } catch (IllegalArgumentException e) {
            errorResId = R.string.error_invalid;
        }

        render();
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
        expression = "";
        result = "";
        errorResId = 0;
        evaluated = false;
        render();
    }
    private void showHistory() {
        MaterialAlertDialogBuilder builder =
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.menu_history)
                        .setPositiveButton(android.R.string.ok, null);

        if (history.isEmpty()) {
            builder.setMessage(R.string.history_empty);
        } else {
            builder.setItems(
                    history.toArray(new String[0]),
                    null
            );

            builder.setNeutralButton(
                    R.string.clear_history,
                    (dialog, which) -> history.clear()
            );
        }

        builder.show();
    }
    private void render() {
        binding.expressionText.setText(expression);
        binding.resultText.setText(errorResId == 0
                ? result : getString(errorResId));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_EXPRESSION, expression);
        outState.putString(STATE_RESULT, result);
        outState.putBoolean(STATE_EVALUATED, evaluated);
        outState.putInt(STATE_ERROR, errorResId);
        outState.putStringArrayList(
                STATE_HISTORY,
                new ArrayList<>(history)
        );
        super.onSaveInstanceState(outState);
    }
}