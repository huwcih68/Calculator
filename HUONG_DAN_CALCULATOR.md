# Hướng dẫn làm Calculator — Session 02

Hướng dẫn dành cho dự án hiện tại: Java, XML, package `vn.edu.course.calculator`.
Nguồn yêu cầu: `session-02.pdf`, trang 29–37, đặc biệt checklist trang 35 và 37.
File này là tài liệu thực hành kèm mã mẫu; mã nguồn ứng dụng hiện tại chưa được thay đổi.

## 1. Hiểu các phần cần làm

```text
MainActivity
    ├── nhận sự kiện bấm phím
    ├── lưu expression, result
    ├── gọi CalculatorEngine khi bấm =
    └── gọi render() để cập nhật màn hình

activity_main.xml: vị trí và hình thức các thành phần
strings.xml: nội dung tiếng Anh / tiếng Việt
CalculatorEngine.java: phân tích và tính biểu thức
```

Giao diện gồm toolbar, dòng biểu thức, dòng kết quả và bàn phím:

```text
          Calculator
                     2+3×4
                        14
--------------------------------
|       C       |  ⌫  |   ÷   |
|   7   |   8   |  9  |   ×   |
|   4   |   5   |  6  |   −   |
|   1   |   2   |  3  |   +   |
|       0       |  .  |   =   |
```

Các đoạn mã bên dưới dùng cùng tên ID. Làm theo thứ tự để tránh thiếu resource hoặc thiếu trường View Binding.

## 2. Bật View Binding

Mở `app/build.gradle.kts`, thêm **bên trong khối `android { ... }`**:

```kotlin
buildFeatures {
    viewBinding = true
}
```

Sau đó chọn **Sync Project with Gradle Files**. Không tạo thủ công lớp `ActivityMainBinding`: Android sinh lớp này từ `activity_main.xml` khi build.

PDF dùng minimum API 26. Dự án hiện tại đặt `minSdk = 37`; nếu cần đúng cấu hình bài và chạy trên máy API 26 trở lên, đổi riêng dòng đó thành `minSdk = 26`. Giữ `compileSdk`, `targetSdk` theo SDK đã cài; không cần đổi chúng thành 26.

Theme hiện tại đã kế thừa `Theme.Material3.DayNight.NoActionBar`, dùng được với các style bên dưới.

## 3. Khai báo chuỗi

Thay nội dung `app/src/main/res/values/strings.xml` bằng:

```xml
<resources>
    <string name="app_name">Calculator</string>
    <string name="expression_hint">Enter an expression</string>
    <string name="error_invalid">Invalid expression</string>
    <string name="error_divide_zero">Cannot divide by zero</string>

    <string name="cd_clear">Clear all</string>
    <string name="cd_delete">Delete last character</string>
    <string name="cd_divide">Divide</string>
    <string name="cd_multiply">Multiply</string>
    <string name="cd_subtract">Subtract</string>
    <string name="cd_add">Add</string>
    <string name="cd_point">Decimal point</string>
    <string name="cd_equals">Calculate result</string>

    <string name="key_0" translatable="false">0</string>
    <string name="key_1" translatable="false">1</string>
    <string name="key_2" translatable="false">2</string>
    <string name="key_3" translatable="false">3</string>
    <string name="key_4" translatable="false">4</string>
    <string name="key_5" translatable="false">5</string>
    <string name="key_6" translatable="false">6</string>
    <string name="key_7" translatable="false">7</string>
    <string name="key_8" translatable="false">8</string>
    <string name="key_9" translatable="false">9</string>
    <string name="key_clear" translatable="false">C</string>
    <string name="key_delete" translatable="false">⌫</string>
    <string name="key_divide" translatable="false">÷</string>
    <string name="key_multiply" translatable="false">×</string>
    <string name="key_subtract" translatable="false">−</string>
    <string name="key_add" translatable="false">+</string>
    <string name="key_point" translatable="false">.</string>
    <string name="key_equals" translatable="false">=</string>
</resources>
```

Tạo thư mục `app/src/main/res/values-vi`, rồi tạo `strings.xml` bên trong:

```xml
<resources>
    <string name="app_name">Máy tính</string>
    <string name="expression_hint">Nhập biểu thức</string>
    <string name="error_invalid">Biểu thức không hợp lệ</string>
    <string name="error_divide_zero">Không thể chia cho 0</string>
    <string name="cd_clear">Xóa tất cả</string>
    <string name="cd_delete">Xóa ký tự cuối</string>
    <string name="cd_divide">Chia</string>
    <string name="cd_multiply">Nhân</string>
    <string name="cd_subtract">Trừ</string>
    <string name="cd_add">Cộng</string>
    <string name="cd_point">Dấu thập phân</string>
    <string name="cd_equals">Tính kết quả</string>
</resources>
```

Các chữ số và ký hiệu không cần dịch, nên dùng `translatable="false"` ở bộ mặc định. Android tự chọn `values-vi` khi ngôn ngữ thiết bị là tiếng Việt.

## 4. Tạo kích thước và style dùng chung

Tạo `app/src/main/res/values/dimens.xml`:

```xml
<resources>
    <dimen name="display_margin">16dp</dimen>
    <dimen name="expression_text_size">24sp</dimen>
    <dimen name="result_text_size">32sp</dimen>
    <dimen name="calc_button_margin">4dp</dimen>
    <dimen name="calc_button_text_size">24sp</dimen>
</resources>
```

Tạo `app/src/main/res/values/styles.xml`:

```xml
<resources>
    <style name="Widget.Calculator.Button" parent="Widget.Material3.Button">
        <item name="android:layout_width">0dp</item>
        <item name="android:layout_height">0dp</item>
        <item name="android:layout_columnWeight">1</item>
        <item name="android:layout_rowWeight">1</item>
        <item name="android:layout_gravity">fill</item>
        <item name="android:layout_margin">@dimen/calc_button_margin</item>
        <item name="android:minWidth">48dp</item>
        <item name="android:minHeight">48dp</item>
        <item name="android:insetTop">0dp</item>
        <item name="android:insetBottom">0dp</item>
        <item name="android:textSize">@dimen/calc_button_text_size</item>
        <item name="backgroundTint">?attr/colorSurfaceVariant</item>
        <item name="android:textColor">?attr/colorOnSurfaceVariant</item>
    </style>

    <style name="Widget.Calculator.Button.Function" parent="Widget.Calculator.Button">
        <item name="backgroundTint">?attr/colorSecondaryContainer</item>
        <item name="android:textColor">?attr/colorOnSecondaryContainer</item>
    </style>

    <style name="Widget.Calculator.Button.Operator" parent="Widget.Calculator.Button">
        <item name="backgroundTint">?attr/colorPrimaryContainer</item>
        <item name="android:textColor">?attr/colorOnPrimaryContainer</item>
    </style>
</resources>
```

`0dp` kết hợp weight cho phép phím chia đều diện tích trong GridLayout. Hai phím `C` và `0` sẽ chiếm hai cột. Màu lấy từ theme để thích ứng sáng/tối.

## 5. Viết giao diện XML

Thay nội dung `app/src/main/res/layout/activity_main.xml` bằng:

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?attr/colorSurface"
    tools:context=".MainActivity">

    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="0dp"
        android:layout_height="?attr/actionBarSize"
        app:title="@string/app_name"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <androidx.constraintlayout.widget.Guideline
        android:id="@+id/displayGuideline"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        app:layout_constraintGuide_percent="0.35" />

    <TextView
        android:id="@+id/expressionText"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:layout_marginHorizontal="@dimen/display_margin"
        android:gravity="end|bottom"
        android:hint="@string/expression_hint"
        android:textColor="?attr/colorOnSurface"
        android:textSize="@dimen/expression_text_size"
        android:maxLines="2"
        android:ellipsize="start"
        app:layout_constraintTop_toBottomOf="@id/toolbar"
        app:layout_constraintBottom_toTopOf="@id/resultText"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <TextView
        android:id="@+id/resultText"
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="@dimen/display_margin"
        android:gravity="end"
        android:textColor="?attr/colorPrimary"
        android:textSize="@dimen/result_text_size"
        android:maxLines="2"
        android:ellipsize="start"
        app:layout_constraintBottom_toTopOf="@id/displayGuideline"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <GridLayout
        android:id="@+id/keypad"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:columnCount="4"
        android:rowCount="5"
        app:layout_constraintTop_toTopOf="@id/displayGuideline"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonClear"
            style="@style/Widget.Calculator.Button.Function"
            android:layout_row="0" android:layout_column="0"
            android:layout_columnSpan="2" android:layout_columnWeight="2"
            android:text="@string/key_clear"
            android:contentDescription="@string/cd_clear" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonDelete"
            style="@style/Widget.Calculator.Button.Function"
            android:layout_row="0" android:layout_column="2"
            android:text="@string/key_delete"
            android:contentDescription="@string/cd_delete" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonDivide"
            style="@style/Widget.Calculator.Button.Operator"
            android:layout_row="0" android:layout_column="3"
            android:text="@string/key_divide"
            android:contentDescription="@string/cd_divide" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button7"
            style="@style/Widget.Calculator.Button"
            android:layout_row="1" android:layout_column="0"
            android:text="@string/key_7" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button8"
            style="@style/Widget.Calculator.Button"
            android:layout_row="1" android:layout_column="1"
            android:text="@string/key_8" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button9"
            style="@style/Widget.Calculator.Button"
            android:layout_row="1" android:layout_column="2"
            android:text="@string/key_9" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonMultiply"
            style="@style/Widget.Calculator.Button.Operator"
            android:layout_row="1" android:layout_column="3"
            android:text="@string/key_multiply"
            android:contentDescription="@string/cd_multiply" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button4"
            style="@style/Widget.Calculator.Button"
            android:layout_row="2" android:layout_column="0"
            android:text="@string/key_4" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button5"
            style="@style/Widget.Calculator.Button"
            android:layout_row="2" android:layout_column="1"
            android:text="@string/key_5" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button6"
            style="@style/Widget.Calculator.Button"
            android:layout_row="2" android:layout_column="2"
            android:text="@string/key_6" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonSubtract"
            style="@style/Widget.Calculator.Button.Operator"
            android:layout_row="2" android:layout_column="3"
            android:text="@string/key_subtract"
            android:contentDescription="@string/cd_subtract" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button1"
            style="@style/Widget.Calculator.Button"
            android:layout_row="3" android:layout_column="0"
            android:text="@string/key_1" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button2"
            style="@style/Widget.Calculator.Button"
            android:layout_row="3" android:layout_column="1"
            android:text="@string/key_2" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button3"
            style="@style/Widget.Calculator.Button"
            android:layout_row="3" android:layout_column="2"
            android:text="@string/key_3" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonAdd"
            style="@style/Widget.Calculator.Button.Operator"
            android:layout_row="3" android:layout_column="3"
            android:text="@string/key_add"
            android:contentDescription="@string/cd_add" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/button0"
            style="@style/Widget.Calculator.Button"
            android:layout_row="4" android:layout_column="0"
            android:layout_columnSpan="2" android:layout_columnWeight="2"
            android:text="@string/key_0" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonPoint"
            style="@style/Widget.Calculator.Button"
            android:layout_row="4" android:layout_column="2"
            android:text="@string/key_point"
            android:contentDescription="@string/cd_point" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/buttonEquals"
            style="@style/Widget.Calculator.Button.Operator"
            android:layout_row="4" android:layout_column="3"
            android:text="@string/key_equals"
            android:contentDescription="@string/cd_equals" />
    </GridLayout>
</androidx.constraintlayout.widget.ConstraintLayout>
```

Điểm cần hiểu:

- `ConstraintLayout` sắp xếp các vùng lớn; `GridLayout` chia bàn phím thành 5 hàng × 4 cột.
- `Guideline` chia vùng hiển thị và bàn phím tại 35% chiều cao, theo slide.
- `layout_row` và `layout_column` đếm từ 0.
- `layout_columnSpan="2"` cho phím rộng hai cột; weight tương ứng là 2.
- Chiều rộng/chiều cao của mỗi phím nằm trong style. Đó vẫn là thuộc tính layout của phím.
- Ví dụ này dùng ký hiệu văn bản `⌫` cho phím xóa; có `contentDescription` để TalkBack đọc tên hành động.

**Mốc kiểm tra:** Build project sau bước này. Android cần nhận đủ các ID để sinh View Binding. Bạn sẽ thấy bố cục trong Preview, nhưng nút chưa hoạt động.

## 6. Viết bộ tính có ưu tiên phép toán

Trong cùng thư mục với `MainActivity.java`, tạo `CalculatorEngine.java`:

```java
package vn.edu.course.calculator;

import java.math.BigDecimal;
import java.math.MathContext;

public final class CalculatorEngine {
    private static final MathContext MC = MathContext.DECIMAL64;
    private final String input;
    private int position;

    private CalculatorEngine(String expression) {
        input = expression.replace('×', '*')
                .replace('÷', '/')
                .replace('−', '-');
    }

    public static String evaluate(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException();
        }
        CalculatorEngine parser = new CalculatorEngine(expression);
        BigDecimal value = parser.parseExpression();
        // Ví dụ 2.3.4: không được bỏ qua phần .4 còn dư.
        if (parser.position != parser.input.length()) {
            throw new IllegalArgumentException();
        }
        return value.stripTrailingZeros().toPlainString();
    }

    // expression = term (('+' | '-') term)*
    private BigDecimal parseExpression() {
        BigDecimal value = parseTerm();
        while (position < input.length()) {
            char operator = input.charAt(position);
            if (operator != '+' && operator != '-') break;
            position++;
            BigDecimal right = parseTerm();
            value = operator == '+'
                    ? value.add(right, MC)
                    : value.subtract(right, MC);
        }
        return value;
    }

    // term = number (('*' | '/') number)*
    private BigDecimal parseTerm() {
        BigDecimal value = parseNumber();
        while (position < input.length()) {
            char operator = input.charAt(position);
            if (operator != '*' && operator != '/') break;
            position++;
            BigDecimal right = parseNumber();
            if (operator == '*') {
                value = value.multiply(right, MC);
            } else {
                if (right.signum() == 0) throw new ArithmeticException();
                value = value.divide(right, MC);
            }
        }
        return value;
    }

    // Một số có thể bắt đầu bằng dấu âm, ví dụ -3 hoặc -.5.
    private BigDecimal parseNumber() {
        int start = position;
        if (position < input.length() && input.charAt(position) == '-') {
            position++;
        }
        boolean hasDigit = false;
        boolean hasPoint = false;
        while (position < input.length()) {
            char c = input.charAt(position);
            if (c >= '0' && c <= '9') {
                hasDigit = true;
                position++;
            } else if (c == '.' && !hasPoint) {
                hasPoint = true;
                position++;
            } else {
                break;
            }
        }
        if (!hasDigit) throw new IllegalArgumentException();
        return new BigDecimal(input.substring(start, position));
    }
}
```

### Vì sao `2 + 3 × 4` ra 14?

`parseExpression()` đọc số hạng đầu tiên là `2`. Khi gặp `+`, nó gọi `parseTerm()` để đọc cả cụm `3×4`. `parseTerm()` trả về `12`, rồi `parseExpression()` mới cộng `2 + 12`.

```text
parseExpression:  2  +  parseTerm(3 × 4)
                 2  +         12        = 14
```

Nhân/chia cùng mức ưu tiên và thực hiện từ trái sang phải: `8÷2×2 = 8`.

`BigDecimal` xử lý số thập phân; `DECIMAL64` giới hạn phép tính ở 16 chữ số có nghĩa, nên phép chia vô hạn như `1÷3` được làm tròn. `stripTrailingZeros()` giúp kết quả `14.0` hiện thành `14`. Mẫu hỗ trợ số âm và bốn phép toán; chưa có ngoặc hoặc phần trăm vì bàn phím bài không yêu cầu.

## 7. Nối giao diện với Java

Thay nội dung `app/src/main/java/vn/edu/course/calculator/MainActivity.java` bằng:

```java
package vn.edu.course.calculator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

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
        }

        bindKeys();
        render();
    }

    private void bindKeys() {
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
        super.onSaveInstanceState(outState);
    }
}
```

### Luồng xử lý khi bấm phím

1. Bấm `2` → `append("2")` → `expression = "2"` → `render()`.
2. Bấm `+`, `3`, `×`, `4` → `expression = "2+3×4"`.
3. Bấm `=` → `calculate()` gọi `CalculatorEngine.evaluate(...)` → `result = "14"`.
4. Bấm xóa → bỏ ký tự cuối của biểu thức; giữ xóa → `clear()`.

Mẫu này cho phép nhập chuỗi chưa hợp lệ, rồi báo lỗi khi bấm `=`. Ví dụ `2++3`, `1..2`, `2+` đều báo lỗi, không làm app crash. Sau lỗi, người dùng có thể bấm xóa để sửa hoặc `C` để nhập lại. Dấu âm ở đầu số được hỗ trợ: `−3+5 = 2`, `2×−3 = −6`.

Một hàm `render()` duy nhất cập nhật hai dòng hiển thị. Trong Java, ký hiệu dùng để xử lý không phải nội dung thông báo; các thông báo hiển thị cho người dùng đều lấy từ string resource.

### Vì sao cần lưu trạng thái?

Xoay màn hình thường làm Activity bị hủy và tạo lại. Các biến Java không tự giữ giá trị. `onSaveInstanceState()` lưu dữ liệu vào `Bundle`; `onCreate()` khôi phục **sau khi tạo binding**, rồi gọi `render()`.

Lưu thêm `evaluated` để hành vi nhập tiếp sau dấu bằng vẫn đúng khi xoay màn hình. Lưu ID lỗi thay vì câu thông báo để `render()` chọn đúng bản dịch hiện tại.

## 8. Chạy và kiểm tra

Chọn **Build > Make Project**, sau đó chạy app trên emulator hoặc điện thoại có API phù hợp với `minSdk`.

| Thao tác / biểu thức | Kết quả mong đợi |
|---|---|
| `2+3×4=` | `14` |
| `8÷2×2=` | `8` |
| `10−3−2=` | `5` |
| `0.1+0.2=` | `0.3` |
| `−3+5=` | `2` |
| `2×−3=` | `−6` |
| `5÷0=` | Thông báo chia cho 0 |
| `2+=`, `2++3=`, `1..2=` hoặc chỉ `=` | Thông báo biểu thức không hợp lệ |
| `123`, bấm xóa | Còn `12` |
| Giữ nút xóa | Xóa cả biểu thức và kết quả |
| `2+3=`, rồi `×4=` | `20` |
| `2+3=`, rồi `7` | Biểu thức mới `7` |
| Xoay màn hình sau `2+3×4=` | Biểu thức và `14` vẫn còn |
| Đổi ngôn ngữ thiết bị sang tiếng Việt | Tiêu đề, gợi ý, lỗi và mô tả phím được dịch |
| Bật dark theme | Chữ và phím vẫn đọc được |

Với màn hình ngang hoặc font lớn, kiểm tra phím còn đủ vùng chạm 48dp và dòng kết quả không bị che. Bố cục 35% này dành cho bước cơ bản ở màn hình dọc; thiết bị quá thấp có thể cần `layout-land/activity_main.xml` để dành vùng bàn phím hợp lý hơn. Nếu thêm layout ngang, giữ nguyên ID để dùng chung View Binding. Menu lịch sử và bố cục ngang là phần mở rộng theo trang 36, làm sau các yêu cầu cốt lõi.

## 9. Lỗi thường gặp

- **Không tìm thấy `ActivityMainBinding`:** kiểm tra View Binding trong Gradle, Sync lại, sửa hết lỗi XML rồi build. Import đúng package `vn.edu.course.calculator.databinding`.
- **Không có `binding.buttonEquals`:** ID trong XML phải là `buttonEquals`, và layout phải tên `activity_main.xml`.
- **Thiếu resource:** tạo đủ `strings.xml`, `dimens.xml`, `styles.xml` trước khi build.
- **Có hai app bar:** theme phải dùng `NoActionBar` vì giao diện đã có `MaterialToolbar`.
- **`2+3×4` ra 20:** phần tính đang thực hiện mọi phép toán từ trái sang phải; kiểm tra `parseExpression()` gọi `parseTerm()`.
- **Xoay màn hình mất dữ liệu:** kiểm tra đã lưu Bundle và gọi `render()` sau khi khôi phục.
- **Không chạy được trên điện thoại:** so sánh API điện thoại với `minSdk`; dự án ban đầu đang đặt 37, còn bài dùng 26.

Nên tự làm từng mốc: bật binding → tạo resource → dựng giao diện → nối nút → tính toán → lưu trạng thái. Sau mỗi mốc, build để phát hiện lỗi ngay tại phần vừa thêm.

### Mã mẫu đã được kiểm tra đến đâu?

Bộ tính đã được biên dịch với mức tương thích Java 11 và vượt qua 17 trường hợp kiểm tra gồm ưu tiên phép toán, số âm, số thập phân, đầu vào sai và chia cho 0. Năm đoạn XML đã được kiểm tra cú pháp và tham chiếu resource; 18 phím phủ đúng 20 ô của lưới, không chồng ô. Chưa build hoặc chạy toàn bộ ứng dụng Android vì hướng dẫn chưa được áp dụng vào mã nguồn dự án; bạn cần thực hiện bước 8 sau khi làm theo các bước trên.
