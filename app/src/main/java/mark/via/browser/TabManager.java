package mark.via.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class TabManager {
    public static class Tab {
        public WebView webView;
        public String title = "";
        public String url = "";
        public Bitmap favicon = null;
        public boolean isIncognito = false;
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int currentIndex = 0;
    private final FrameLayout container;
    private final Context context;
    private final Settings settings;
    private final AdBlocker adBlocker;
    private final History history;
    private final Bookmarks bookmarks;
    private final TabCallback callback;

    public interface TabCallback {
        void onTitleChanged(String title);
        void onUrlChanged(String url);
        void onProgressChanged(int progress);
        void onTabCountChanged(int count);
    }

    public TabManager(Context ctx, FrameLayout container, Settings settings,
                      AdBlocker adBlocker, History history, Bookmarks bookmarks, TabCallback callback) {
        this.context = ctx;
        this.container = container;
        this.settings = settings;
        this.adBlocker = adBlocker;
        this.history = history;
        this.bookmarks = bookmarks;
        this.callback = callback;
    }

    public Tab newTab(String url) {
        Tab tab = new Tab();
        tab.webView = createWebView();
        tabs.add(tab);
        currentIndex = tabs.size() - 1;
        switchTo(currentIndex);
        if (url != null && !url.isEmpty()) {
            tab.webView.loadUrl(url);
        }
        callback.onTabCountChanged(tabs.size());
        return tab;
    }

    public void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab tab = tabs.remove(index);
        if (tab.webView != null) {
            container.removeView(tab.webView);
            tab.webView.destroy();
        }
        if (tabs.isEmpty()) {
            newTab(settings.getHomepage());
        } else {
            if (currentIndex >= tabs.size()) {
                currentIndex = tabs.size() - 1;
            }
            switchTo(currentIndex);
        }
        callback.onTabCountChanged(tabs.size());
    }

    public void closeCurrentTab() {
        closeTab(currentIndex);
    }

    public void switchTo(int index) {
        if (index < 0 || index >= tabs.size()) return;
        currentIndex = index;
        container.removeAllViews();
        Tab tab = tabs.get(index);
        if (tab.webView.getParent() != null) {
            ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
        }
        container.addView(tab.webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        callback.onTitleChanged(tab.title);
        callback.onUrlChanged(tab.url);
        callback.onProgressChanged(100);
    }

    public Tab getCurrentTab() {
        if (currentIndex >= 0 && currentIndex < tabs.size()) {
            return tabs.get(currentIndex);
        }
        return null;
    }

    public WebView getCurrentWebView() {
        Tab tab = getCurrentTab();
        return tab != null ? tab.webView : null;
    }

    public int getTabCount() {
        return tabs.size();
    }

    public List<Tab> getAllTabs() {
        return tabs;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public void destroyAll() {
        for (Tab tab : tabs) {
            if (tab.webView != null) {
                container.removeView(tab.webView);
                tab.webView.destroy();
            }
        }
        tabs.clear();
    }

    private void safeSet(Runnable r) {
        try { r.run(); } catch (Exception e) { }
    }

    private WebView createWebView() {
        WebView webView = new WebView(context);
        configureWebView(webView);
        return webView;
    }

    private void configureWebView(WebView webView) {
        WebSettings ws = webView.getSettings();

        // All settings wrapped in try-catch for Android 16 / WebView 153 compatibility
        safeSet(() -> ws.setJavaScriptEnabled(true));
        safeSet(() -> ws.setDomStorageEnabled(true));
        safeSet(() -> ws.setBuiltInZoomControls(true));
        safeSet(() -> ws.setDisplayZoomControls(false));
        safeSet(() -> ws.setUseWideViewPort(true));
        safeSet(() -> ws.setLoadWithOverviewMode(true));
        safeSet(() -> ws.setSupportZoom(true));
        safeSet(() -> ws.setCacheMode(WebSettings.LOAD_DEFAULT));
        safeSet(() -> ws.setAllowFileAccess(true));
        safeSet(() -> ws.setAllowContentAccess(true));
        safeSet(() -> ws.setMediaPlaybackRequiresUserGesture(false));
        safeSet(() -> ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE));
        safeSet(() -> ws.setDatabaseEnabled(true));
        safeSet(() -> ws.setJavaScriptCanOpenWindowsAutomatically(true));
        safeSet(() -> ws.setDefaultTextEncodingName("UTF-8"));
        safeSet(() -> ws.setMinimumFontSize(1));
        safeSet(() -> ws.setLoadsImagesAutomatically(true));
        safeSet(() -> ws.setTextZoom(100));

        // Safe browsing - wrap carefully for newer APIs
        if (Build.VERSION.SDK_INT >= 26) {
            safeSet(() -> ws.setSafeBrowsingEnabled(true));
        }

        // File access from URLs - security hardening
        safeSet(() -> ws.setAllowFileAccessFromFileURLs(false));
        safeSet(() -> ws.setAllowUniversalAccessFromFileURLs(false));

        // Geolocation
        safeSet(() -> ws.setGeolocationEnabled(true));

        // Remove dangerous JS interfaces
        try {
            webView.removeJavascriptInterface("searchBoxJavaBridge_");
            webView.removeJavascriptInterface("accessibility");
            webView.removeJavascriptInterface("accessibilityTraversal");
        } catch (Exception e) { }

        // Cookies
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            if (Build.VERSION.SDK_INT >= 21) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
            }
        } catch (Exception e) { }

        // Apply user settings
        applySettings(ws);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("via:") || url.startsWith("intent:")) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Tab tab = findTab(view);
                if (tab != null) {
                    tab.url = url;
                    if (favicon != null) tab.favicon = favicon;
                }
                if (tab == getCurrentTab()) {
                    callback.onUrlChanged(url);
                    callback.onProgressChanged(0);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Tab tab = findTab(view);
                if (tab != null) {
                    tab.url = url;
                    if (settings.isNightMode()) {
                        injectNightMode(view);
                    }
                    if (!tab.isIncognito) {
                        history.add(view.getTitle(), url);
                    }
                }
                if (tab == getCurrentTab()) {
                    callback.onProgressChanged(100);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                try {
                    String url = request.getUrl().toString();
                    if (adBlocker.shouldBlock(url)) {
                        return adBlocker.block();
                    }
                } catch (Exception e) { }
                return null;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                // Don't crash on resource load errors
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                Tab tab = findTab(view);
                if (tab != null) {
                    String lastUrl = tab.url;
                    int idx = tabs.indexOf(tab);
                    container.removeView(view);
                    view.destroy();
                    tab.webView = createWebView();
                    if (lastUrl != null && !lastUrl.isEmpty()) {
                        tab.webView.loadUrl(lastUrl);
                    }
                    if (idx == currentIndex) {
                        switchTo(currentIndex);
                    }
                    return true;
                }
                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Tab tab = findTab(view);
                if (tab == getCurrentTab()) {
                    callback.onProgressChanged(newProgress);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                Tab tab = findTab(view);
                if (tab != null) {
                    tab.title = title;
                }
                if (tab == getCurrentTab()) {
                    callback.onTitleChanged(title);
                }
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                Tab tab = findTab(view);
                if (tab != null) {
                    tab.favicon = icon;
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, android.webkit.GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                result.confirm();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, android.webkit.JsResult result) {
                return false;
            }
        });

        // Download listener
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                DownloadHelper.startDownload(context, url, userAgent, contentDisposition, mimetype, contentLength);
            }
        });
    }

    public void applySettings(WebSettings ws) {
        try {
            ws.setBlockNetworkImage(settings.isBlockImages());
        } catch (Exception e) { }

        try {
            String ua = settings.getUserAgent();
            if (settings.isDesktopMode()) {
                ws.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            } else if (ua != null && !ua.isEmpty()) {
                ws.setUserAgentString(ua);
            } else {
                ws.setUserAgentString(null);
            }
        } catch (Exception e) { }
    }

    public void reloadAllSettings() {
        for (Tab tab : tabs) {
            applySettings(tab.webView.getSettings());
        }
    }

    private void injectNightMode(WebView view) {
        try {
            String js = "javascript:(function(){var s=document.createElement('style');" +
                    "s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important}" +
                    "img,video,canvas,iframe{filter:invert(1) hue-rotate(180deg)!important}';" +
                    "document.head.appendChild(s);})();";
            view.evaluateJavascript(js, null);
        } catch (Exception e) { }
    }

    private Tab findTab(WebView webView) {
        for (Tab tab : tabs) {
            if (tab.webView == webView) return tab;
        }
        return null;
    }
}
