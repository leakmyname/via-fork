package mark.via.browser;

import android.app.Application;
import android.os.Build;
import android.util.Log;
import android.webkit.WebView;

public class BrowserApp extends Application {
    private static final String TAG = "ViaCrash";
    private static BrowserApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Global crash logger - write stack trace to logcat before dying
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "Uncaught exception on thread " + thread.getName(), throwable);
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });

        // WebView data directory suffix for multi-process safety
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String processName = Application.getProcessName();
                if (processName != null && !processName.equals(getPackageName())) {
                    WebView.setDataDirectorySuffix(processName);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "WebView data dir suffix failed", t);
        }
    }

    public static BrowserApp get() {
        return instance;
    }
}
