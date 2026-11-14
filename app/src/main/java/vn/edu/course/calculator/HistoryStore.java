package vn.edu.course.calculator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Stores history on disk; all database operations run on one background thread. */
public final class HistoryStore extends SQLiteOpenHelper {
    private static HistoryStore instance;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public static synchronized HistoryStore get(Context context) {
        if (instance == null) {
            instance = new HistoryStore(context.getApplicationContext(), "calculator_history.db");
        }
        return instance;
    }

    HistoryStore(Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, 1);
    }

    public static final class Entry {
        public final long id;
        public final String expression;
        public final String result;
        public final long createdAt;

        Entry(long id, String expression, String result, long createdAt) {
            this.id = id;
            this.expression = expression;
            this.result = result;
            this.createdAt = createdAt;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "expression TEXT NOT NULL, result TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_date ON history(created_at DESC, _id DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 only. Add a non-destructive migration when changing the schema.
        throw new IllegalStateException("Missing history database migration");
    }

    public void add(String expression, String result, Consumer<Exception> onError) {
        // Capture when '=' was pressed, not when the worker eventually runs.
        long createdAt = System.currentTimeMillis();
        submit(() -> {
            ContentValues values = new ContentValues();
            values.put("expression", expression);
            values.put("result", result);
            values.put("created_at", createdAt);
            getWritableDatabase().insertOrThrow("history", null, values);
            return null;
        }, null, onError);
    }

    public void read(Consumer<List<Entry>> onSuccess, Consumer<Exception> onError) {
        submit(() -> {
            List<Entry> entries = new ArrayList<>();
            try (Cursor cursor = getReadableDatabase().query(
                    "history", new String[]{"_id", "expression", "result", "created_at"},
                    null, null, null, null, "created_at DESC, _id DESC")) {
                while (cursor.moveToNext()) {
                    entries.add(new Entry(cursor.getLong(0), cursor.getString(1),
                            cursor.getString(2), cursor.getLong(3)));
                }
            }
            return entries;
        }, onSuccess, onError);
    }

    public void delete(Set<Long> selectedIds, Runnable onSuccess,
                       Consumer<Exception> onError) {
        Set<Long> ids = new HashSet<>(selectedIds);
        submit(() -> {
            SQLiteDatabase db = getWritableDatabase();
            db.beginTransaction();
            try {
                for (long id : ids) {
                    db.delete("history", "_id = ?", new String[]{Long.toString(id)});
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            return null;
        }, ignored -> onSuccess.run(), onError);
    }

    public void clear(Runnable onSuccess, Consumer<Exception> onError) {
        submit(() -> {
            getWritableDatabase().delete("history", null, null);
            return null;
        }, ignored -> onSuccess.run(), onError);
    }

    private <T> void submit(Callable<T> work, Consumer<T> onSuccess,
                            Consumer<Exception> onError) {
        io.execute(() -> {
            try {
                T value = work.call();
                if (onSuccess != null) main.post(() -> onSuccess.accept(value));
            } catch (Exception error) {
                Log.e("HistoryStore", "History database operation failed", error);
                if (onError != null) main.post(() -> onError.accept(error));
            }
        });
    }
}
