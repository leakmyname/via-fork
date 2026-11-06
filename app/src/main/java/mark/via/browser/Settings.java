package mark.via.browser;

import android.content.Context;
import android.content.SharedPreferences;

public class Settings {
    private static final String PREFS = "via_settings";
    private final SharedPreferences sp;

    public Settings(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getSearchEngine() {
        return sp.getString("search_engine", "https://www.google.com/search?q=");
    }

    public void setSearchEngine(String url) {
        sp.edit().putString("search_engine", url).apply();
    }

    public String getHomepage() {
        return sp.getString("homepage", "https://viayoo.com/");
    }

    public void setHomepage(String url) {
        sp.edit().putString("homepage", url).apply();
    }

    public boolean isNightMode() {
        return sp.getBoolean("night_mode", false);
    }

    public void setNightMode(boolean on) {
        sp.edit().putBoolean("night_mode", on).apply();
    }

    public boolean isBlockImages() {
        return sp.getBoolean("block_images", false);
    }

    public void setBlockImages(boolean on) {
        sp.edit().putBoolean("block_images", on).apply();
    }

    public boolean isAdBlock() {
        return sp.getBoolean("ad_block", true);
    }

    public void setAdBlock(boolean on) {
        sp.edit().putBoolean("ad_block", on).apply();
    }

    public boolean isDesktopMode() {
        return sp.getBoolean("desktop_mode", false);
    }

    public void setDesktopMode(boolean on) {
        sp.edit().putBoolean("desktop_mode", on).apply();
    }

    public boolean isIncognito() {
        return sp.getBoolean("incognito", false);
    }

    public void setIncognito(boolean on) {
        sp.edit().putBoolean("incognito", on).apply();
    }

    public String getUserAgent() {
        return sp.getString("user_agent", "");
    }

    public void setUserAgent(String ua) {
        sp.edit().putString("user_agent", ua).apply();
    }
}
