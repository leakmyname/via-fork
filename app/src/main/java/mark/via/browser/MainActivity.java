package mark.via.browser;
import mark.via.gp.R;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;

import android.util.Log;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements TabManager.TabCallback {

    private Settings settings;
    private TabManager tabManager;
    private AdBlocker adBlocker;
    private History history;
    private Bookmarks bookmarks;

    private EditText urlBar;
    private ProgressBar progressBar;
    private FrameLayout webContainer;
    private TextView tvTabCount;
    private ImageButton btnBack, btnForward, btnRefresh, btnMenu;
    private ImageButton btnHome, btnTab, btnBookmark, btnShare;

    private boolean urlBarEditing = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
        } catch (Throwable t) {
            Log.e("ViaCrash", "setContentView failed", t);
            throw t;
        }

        try {
            settings = new Settings(this);
            adBlocker = new AdBlocker(this);
            history = new History(this);
            bookmarks = new Bookmarks(this);

            initViews();

            webContainer = findViewById(R.id.web_container);

            tabManager = new TabManager(this, webContainer, settings, adBlocker, history, bookmarks, this);

            // Create first tab
            String url = settings.getHomepage();
            Intent intent = getIntent();
            if (intent != null && intent.getData() != null) {
                url = intent.getData().toString();
            }
            tabManager.newTab(url);
        } catch (Throwable t) {
            Log.e("ViaCrash", "MainActivity init failed", t);
            throw t;
        }
    }

    private void initViews() {
        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnRefresh = findViewById(R.id.btn_refresh);
        btnMenu = findViewById(R.id.btn_menu);
        btnHome = findViewById(R.id.btn_home);
        btnTab = findViewById(R.id.btn_tab);
        btnBookmark = findViewById(R.id.btn_bookmark);
        btnShare = findViewById(R.id.btn_share);
        tvTabCount = findViewById(R.id.tv_tab_count);

        btnBack.setOnClickListener(v -> {
            WebView wv = tabManager.getCurrentWebView();
            if (wv != null && wv.canGoBack()) {
                wv.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            WebView wv = tabManager.getCurrentWebView();
            if (wv != null && wv.canGoForward()) {
                wv.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> {
            WebView wv = tabManager.getCurrentWebView();
            if (wv != null) {
                if (progressBar.getVisibility() == View.VISIBLE) {
                    wv.stopLoading();
                } else {
                    wv.reload();
                }
            }
        });

        btnMenu.setOnClickListener(v -> showMenu(v));

        btnHome.setOnClickListener(v -> {
            WebView wv = tabManager.getCurrentWebView();
            if (wv != null) {
                wv.loadUrl(settings.getHomepage());
            }
        });

        btnTab.setOnClickListener(v -> showTabDialog());

        btnBookmark.setOnClickListener(v -> {
            TabManager.Tab tab = tabManager.getCurrentTab();
            if (tab != null && !tab.url.isEmpty()) {
                if (bookmarks.contains(tab.url)) {
                    // Already bookmarked - show bookmarks list
                    showBookmarksDialog();
                } else {
                    bookmarks.add(tab.title.isEmpty() ? tab.url : tab.title, tab.url);
                    Toast.makeText(this, R.string.add_bookmark, Toast.LENGTH_SHORT).show();
                }
            } else {
                showBookmarksDialog();
            }
        });

        btnShare.setOnClickListener(v -> {
            TabManager.Tab tab = tabManager.getCurrentTab();
            if (tab != null && !tab.url.isEmpty()) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, tab.title);
                shareIntent.putExtra(Intent.EXTRA_TEXT, tab.url);
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
            }
        });

        // URL bar handling
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateTo(urlBar.getText().toString().trim());
                urlBar.clearFocus();
                return true;
            }
            return false;
        });

        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            urlBarEditing = hasFocus;
            if (hasFocus) {
                urlBar.selectAll();
            }
        });
    }

    private void navigateTo(String input) {
        WebView wv = tabManager.getCurrentWebView();
        if (wv == null) return;
        if (input.isEmpty()) return;

        String url;
        if (input.startsWith("http://") || input.startsWith("https://")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            // Looks like a domain
            url = "https://" + input;
        } else {
            // Search
            url = settings.getSearchEngine() + URLEncoder.encode(input);
        }

        wv.loadUrl(url);
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, R.string.new_tab);
        popup.getMenu().add(0, 2, 0, R.string.incognito);
        popup.getMenu().add(0, 3, 0, R.string.bookmarks);
        popup.getMenu().add(0, 4, 0, R.string.history);
        popup.getMenu().add(0, 5, 0, R.string.downloads);
        popup.getMenu().add(0, 6, 0, R.string.find_in_page);
        popup.getMenu().add(0, 7, 0, R.string.desktop_site);
        popup.getMenu().add(0, 8, 0, R.string.night_mode);
        popup.getMenu().add(0, 9, 0, R.string.share);
        popup.getMenu().add(0, 10, 0, R.string.settings);
        popup.getMenu().add(0, 11, 0, R.string.exit);

        // Update checkable items
        popup.getMenu().findItem(7).setCheckable(true).setChecked(settings.isDesktopMode());
        popup.getMenu().findItem(8).setCheckable(true).setChecked(settings.isNightMode());

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: // New tab
                    tabManager.newTab(settings.getHomepage());
                    return true;
                case 2: // Incognito
                    settings.setIncognito(!settings.isIncognito());
                    Toast.makeText(this, R.string.incognito, Toast.LENGTH_SHORT).show();
                    return true;
                case 3: // Bookmarks
                    showBookmarksDialog();
                    return true;
                case 4: // History
                    showHistoryDialog();
                    return true;
                case 5: // Downloads
                    showDownloadsDialog();
                    return true;
                case 6: // Find in page
                    showFindDialog();
                    return true;
                case 7: // Desktop site
                    settings.setDesktopMode(!settings.isDesktopMode());
                    tabManager.reloadAllSettings();
                    WebView wv = tabManager.getCurrentWebView();
                    if (wv != null) wv.reload();
                    return true;
                case 8: // Night mode
                    settings.setNightMode(!settings.isNightMode());
                    tabManager.reloadAllSettings();
                    wv = tabManager.getCurrentWebView();
                    if (wv != null) wv.reload();
                    return true;
                case 9: // Share
                    TabManager.Tab tab = tabManager.getCurrentTab();
                    if (tab != null && !tab.url.isEmpty()) {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_SUBJECT, tab.title);
                        shareIntent.putExtra(Intent.EXTRA_TEXT, tab.url);
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                    }
                    return true;
                case 10: // Settings
                    showSettingsDialog();
                    return true;
                case 11: // Exit
                    finish();
                    return true;
            }
            return false;
        });
        popup.show();
    }

    private void showTabDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.tabs);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_list, null);
        ListView listView = dialogView.findViewById(R.id.list_view);

        List<TabManager.Tab> tabs = tabManager.getAllTabs();
        List<String> items = new ArrayList<>();
        for (TabManager.Tab tab : tabs) {
            items.add((tab.title.isEmpty() ? tab.url : tab.title) + (tab.isIncognito ? " [Incognito]" : ""));
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            tabManager.switchTo(position);
            dialog.dismiss();
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            tabManager.closeTab(position);
            dialog.dismiss();
            return true;
        });

        builder.setPositiveButton("+ " + getString(R.string.new_tab), (d, w) -> {
            tabManager.newTab(settings.getHomepage());
        });

        dialog.show();
    }

    private void showBookmarksDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.bookmarks);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_list, null);
        ListView listView = dialogView.findViewById(R.id.list_view);

        List<Bookmarks.Item> bookmarksList = bookmarks.getAll();
        List<String> items = new ArrayList<>();
        for (Bookmarks.Item item : bookmarksList) {
            items.add(item.title + "\n" + item.url);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        listView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Bookmarks.Item item = bookmarksList.get(position);
            tabManager.getCurrentWebView().loadUrl(item.url);
            dialog.dismiss();
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Bookmarks.Item item = bookmarksList.get(position);
            showEditBookmarkDialog(item);
            dialog.dismiss();
            return true;
        });

        dialog.show();
    }

    private void showEditBookmarkDialog(Bookmarks.Item item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.edit_bookmark);

        View view = getLayoutInflater().inflate(R.layout.dialog_bookmark_edit, null);
        EditText etTitle = view.findViewById(R.id.et_title);
        EditText etUrl = view.findViewById(R.id.et_url);
        etTitle.setText(item.title);
        etUrl.setText(item.url);

        builder.setView(view);
        builder.setPositiveButton(R.string.ok, (d, w) -> {
            bookmarks.update(item.id, etTitle.getText().toString().trim(), etUrl.getText().toString().trim());
        });
        builder.setNegativeButton(R.string.delete, (d, w) -> {
            bookmarks.delete(item.id);
        });
        builder.setNeutralButton(R.string.cancel, null);
        builder.show();
    }

    private void showHistoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.history);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_list, null);
        ListView listView = dialogView.findViewById(R.id.list_view);

        List<History.Item> historyList = history.getAll();
        List<String> items = new ArrayList<>();
        for (History.Item item : historyList) {
            items.add(item.title + "\n" + item.url);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items);
        listView.setAdapter(adapter);

        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.clear_history, (d, w) -> {
            history.clear();
            Toast.makeText(this, R.string.clear_history, Toast.LENGTH_SHORT).show();
        });
        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            History.Item item = historyList.get(position);
            tabManager.getCurrentWebView().loadUrl(item.url);
            dialog.dismiss();
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            History.Item item = historyList.get(position);
            history.delete(item.id);
            dialog.dismiss();
            showHistoryDialog();
            return true;
        });

        dialog.show();
    }

    private void showFindDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.find_in_page);

        final EditText input = new EditText(this);
        input.setHint(R.string.find_in_page);
        builder.setView(input);
        builder.setPositiveButton(R.string.ok, (d, w) -> {
            String query = input.getText().toString();
            if (!query.isEmpty()) {
                WebView wv = tabManager.getCurrentWebView();
                if (wv != null) {
                    wv.findAllAsync(query);
                }
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showDownloadsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.downloads);

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_list, null);
        ListView listView = dialogView.findViewById(R.id.list_view);

        java.util.List<DownloadHelper.DownloadItem> downloads = DownloadHelper.getDownloads(this);
        if (downloads.isEmpty()) {
            builder.setMessage(R.string.no_downloads);
        } else {
            List<String> items = new ArrayList<>();
            for (DownloadHelper.DownloadItem item : downloads) {
                items.add(item.title + "\n" + item.getStatusText(this));
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_2, android.R.id.text1, items);
            listView.setAdapter(adapter);

            listView.setOnItemClickListener((parent, view, position, id) -> {
                DownloadHelper.DownloadItem item = downloads.get(position);
                if (item.status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    Toast.makeText(this, "Saved to Downloads/" + item.title, Toast.LENGTH_SHORT).show();
                }
            });

            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                DownloadHelper.DownloadItem item = downloads.get(position);
                DownloadHelper.cancelDownload(this, item.id);
                Toast.makeText(this, R.string.delete, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        builder.setView(dialogView);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton("Clear", (d, w) -> {
            DownloadHelper.clearCompletedDownloads(this);
        });
        builder.show();
    }

    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings);

        String[] items = {
            getString(R.string.search_engine),
            getString(R.string.homepage),
            getString(R.string.user_agent),
            getString(R.string.ad_block) + " [" + (settings.isAdBlock() ? "ON" : "OFF") + "]",
            getString(R.string.block_image) + " [" + (settings.isBlockImages() ? "ON" : "OFF") + "]",
            getString(R.string.night_mode) + " [" + (settings.isNightMode() ? "ON" : "OFF") + "]",
            getString(R.string.desktop_site) + " [" + (settings.isDesktopMode() ? "ON" : "OFF") + "]"
        };

        builder.setItems(items, (d, which) -> {
            switch (which) {
                case 0: // Search engine
                    showSearchEngineDialog();
                    break;
                case 1: // Homepage
                    showHomepageDialog();
                    break;
                case 2: // User agent
                    showUserAgentDialog();
                    break;
                case 3: // Ad block
                    settings.setAdBlock(!settings.isAdBlock());
                    adBlocker.setEnabled(settings.isAdBlock());
                    break;
                case 4: // Block images
                    settings.setBlockImages(!settings.isBlockImages());
                    tabManager.reloadAllSettings();
                    break;
                case 5: // Night mode
                    settings.setNightMode(!settings.isNightMode());
                    tabManager.reloadAllSettings();
                    WebView wv = tabManager.getCurrentWebView();
                    if (wv != null) wv.reload();
                    break;
                case 6: // Desktop mode
                    settings.setDesktopMode(!settings.isDesktopMode());
                    tabManager.reloadAllSettings();
                    wv = tabManager.getCurrentWebView();
                    if (wv != null) wv.reload();
                    break;
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.show();
    }

    private void showSearchEngineDialog() {
        final String[][] engines = {
            {"Google", "https://www.google.com/search?q="},
            {"Bing", "https://www.bing.com/search?q="},
            {"Baidu", "https://www.baidu.com/s?wd="},
            {"DuckDuckGo", "https://duckduckgo.com/?q="},
            {"Yahoo", "https://search.yahoo.com/search?p="},
            {"Yandex", "https://yandex.com/search?text="}
        };

        String[] names = new String[engines.length];
        for (int i = 0; i < engines.length; i++) names[i] = engines[i][0];

        String current = settings.getSearchEngine();
        int selected = 0;
        for (int i = 0; i < engines.length; i++) {
            if (engines[i][1].equals(current)) {
                selected = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.search_engine)
            .setSingleChoiceItems(names, selected, (d, w) -> {
                settings.setSearchEngine(engines[w][1]);
                d.dismiss();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showHomepageDialog() {
        final EditText input = new EditText(this);
        input.setText(settings.getHomepage());
        new AlertDialog.Builder(this)
            .setTitle(R.string.homepage)
            .setView(input)
            .setPositiveButton(R.string.ok, (d, w) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    settings.setHomepage(url);
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    private void showUserAgentDialog() {
        final String[] uas = {
            "Default",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        };

        new AlertDialog.Builder(this)
            .setTitle(R.string.user_agent)
            .setItems(uas, (d, w) -> {
                settings.setUserAgent(w == 0 ? "" : uas[w]);
                tabManager.reloadAllSettings();
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    // TabManager.TabCallback
    @Override
    public void onTitleChanged(String title) {
        if (!urlBarEditing) {
            // We keep the URL bar showing the URL, not the title
        }
    }

    @Override
    public void onUrlChanged(String url) {
        if (!urlBarEditing) {
            urlBar.setText(url);
        }
        // Update back/forward button states
        WebView wv = tabManager.getCurrentWebView();
        btnBack.setEnabled(wv != null && wv.canGoBack());
        btnForward.setEnabled(wv != null && wv.canGoForward());
    }

    @Override
    public void onProgressChanged(int progress) {
        if (progress < 100) {
            progressBar.setVisibility(View.VISIBLE);
            progressBar.setProgress(progress);
            btnRefresh.setImageResource(R.drawable.ic_close);
        } else {
            progressBar.setVisibility(View.GONE);
            btnRefresh.setImageResource(R.drawable.ic_refresh);
        }
    }

    @Override
    public void onTabCountChanged(int count) {
        tvTabCount.setText(String.valueOf(count));
    }

    @Override
    public void onBackPressed() {
        WebView wv = tabManager.getCurrentWebView();
        if (wv != null && wv.canGoBack()) {
            wv.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getData() != null) {
            tabManager.newTab(intent.getData().toString());
        }
    }

    @Override
    protected void onDestroy() {
        if (tabManager != null) {
            tabManager.destroyAll();
        }
        super.onDestroy();
    }
}
