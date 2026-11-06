package mark.via.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class History extends SQLiteOpenHelper {
    private static final String DB_NAME = "via_history";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "history";

    public static class Item {
        public long id;
        public String title;
        public String url;
        public long time;

        public Item(long id, String title, String url, long time) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.time = time;
        }
    }

    public History(Context ctx) {
        super(ctx.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT, time INTEGER)");
        db.execSQL("CREATE INDEX idx_time ON " + TABLE + "(time DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void add(String title, String url) {
        if (url == null || url.startsWith("data:") || url.startsWith("about:")) return;
        SQLiteDatabase db = getWritableDatabase();
        // Remove existing entry with same URL
        db.delete(TABLE, "url=?", new String[]{url});
        ContentValues cv = new ContentValues();
        cv.put("title", title != null ? title : url);
        cv.put("url", url);
        cv.put("time", System.currentTimeMillis());
        db.insert(TABLE, null, cv);
    }

    public List<Item> getAll() {
        List<Item> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "time DESC", "500");
        while (c.moveToNext()) {
            list.add(new Item(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)));
        }
        c.close();
        return list;
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }
}
