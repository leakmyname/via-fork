package mark.via.browser;

import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

public class AdBlocker {
    private static final String PREFS = "via_adblock";
    private static final String KEY_RULES = "rules";
    private static final String KEY_ENABLED = "enabled";

    private final SharedPreferences sp;
    private final Set<String> blockedDomains = new HashSet<>();
    private boolean enabled = true;

    // Common ad/tracking domains
    private static final String[] DEFAULT_BLOCKED = {
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "ads.google.com", "adservice.google.com", "adnxs.com", "adsrvr.org",
        "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "ads.yahoo.com",
        "amazon-adsystem.com", "adsystem.com", "moatads.com", "adsafeprotected.com",
        "scorecardresearch.com", "quantserve.com", "chartbeat.com",
        "facebook.com/tr", "connect.facebook.net", "analytics.twitter.com",
        "ads.linkedin.com", "hotjar.com", "mixpanel.com", "segment.io",
        "fullstory.com", "clarity.ms", "bat.bing.com"
    };

    public AdBlocker(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        enabled = sp.getBoolean(KEY_ENABLED, true);
        loadRules();
    }

    private void loadRules() {
        blockedDomains.clear();
        for (String d : DEFAULT_BLOCKED) {
            blockedDomains.add(d);
        }
        Set<String> custom = sp.getStringSet(KEY_RULES, new HashSet<>());
        blockedDomains.addAll(custom);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        sp.edit().putBoolean(KEY_ENABLED, on).apply();
    }

    public boolean shouldBlock(String url) {
        if (!enabled || url == null) return false;
        String lower = url.toLowerCase();
        for (String domain : blockedDomains) {
            if (lower.contains(domain)) return true;
        }
        // Block common ad path patterns
        if (lower.contains("/ads/") || lower.contains("/ad/") || lower.contains("/banner/")
                || lower.contains("/popunder") || lower.contains("/popup")
                || lower.contains("pagead") || lower.contains("show_ads")) {
            return true;
        }
        return false;
    }

    public WebResourceResponse block() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }
}
