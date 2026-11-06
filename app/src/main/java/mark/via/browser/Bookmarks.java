package mark.via.browser;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class Bookmarks extends SQLiteOpenHelper {
    private static final String DB_NAME = "via_bookmarks";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "bookmarks";

    public static class Item {
        public long id;
        public String title;
        public String url;

        public Item(long id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }

    public Bookmarks(Context ctx) {
        super(ctx.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, url TEXT)");
        // Add default bookmarks
        addDefault(db, "Google", "https://www.google.com");
        addDefault(db, "Baidu", "https://www.baidu.com");
        addDefault(db, "Bing", "https://www.bing.com");
        addDefault(db, "GitHub", "https://github.com");
        addDefault(db, "Via", "https://viayoo.com/");
    }

    private void addDefault(SQLiteDatabase db, String title, String url) {
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("url", url);
        db.insert(TABLE, null, cv);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    public void add(String title, String url) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("url", url);
        db.insert(TABLE, null, cv);
    }

    public void update(long id, String title, String url) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("title", title);
        cv.put("url", url);
        db.update(TABLE, cv, "id=?", new String[]{String.valueOf(id)});
    }

    public List<Item> getAll() {
        List<Item> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, null, null, null, null, "title ASC");
        while (c.moveToNext()) {
            list.add(new Item(c.getLong(0), c.getString(1), c.getString(2)));
        }
        c.close();
        return list;
    }

    public boolean contains(String url) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.query(TABLE, null, "url=?", new String[]{url}, null, null, null);
        boolean has = c.getCount() > 0;
        c.close();
        return has;
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)});
    }

    public void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }
}
