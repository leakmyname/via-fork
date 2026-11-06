package mark.via.browser;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;

public class DownloadHelper {

    public static class DownloadItem {
        public long id;
        public String title;
        public String url;
        public int status;
        public long total;
        public long downloaded;
        public String mimeType;

        public String getStatusText(Context ctx) {
            switch (status) {
                case DownloadManager.STATUS_RUNNING:
                    int pct = total > 0 ? (int)(downloaded * 100 / total) : 0;
                    return pct + "%" + (total > 0 ? " (" + formatSize(downloaded) + "/" + formatSize(total) + ")" : "");
                case DownloadManager.STATUS_SUCCESSFUL:
                    return "Done";
                case DownloadManager.STATUS_FAILED:
                    return "Failed";
                case DownloadManager.STATUS_PENDING:
                    return "Pending";
                case DownloadManager.STATUS_PAUSED:
                    return "Paused";
                default:
                    return "Unknown";
            }
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static void startDownload(Context context, String url, String userAgent,
            String contentDisposition, String mimetype, long contentLength) {
        if (url == null || url.isEmpty()) return;

        // Handle data: URLs - can't use DownloadManager for those
        if (url.startsWith("data:")) {
            Toast.makeText(context, "Cannot download data URL", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation dialog
        String filename = guessFilename(contentDisposition, url, mimetype);
        String sizeStr = contentLength > 0 ? formatSize(contentLength) : "Unknown size";
        String message = filename + "\n" + sizeStr + "\n" + url;

        new AlertDialog.Builder(context)
            .setTitle("Download")
            .setMessage(message)
            .setPositiveButton("Download", (d, w) -> {
                enqueueDownload(context, url, userAgent, contentDisposition, mimetype, filename);
            })
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Copy link", (d, w) -> {
                ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("url", url));
                Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show();
            })
            .show();
    }

    private static void enqueueDownload(Context context, String url, String userAgent,
            String contentDisposition, String mimetype, String filename) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));

            // Set MIME type
            if (mimetype != null && !mimetype.isEmpty()) {
                request.setMimeType(mimetype);
            }

            // Set destination
            File downloadDir = getDownloadDir();
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);

            // Allow mobile + wifi
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);

            // Set title for notification
            request.setTitle(filename);
            request.setDescription("Via Browser download");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

            // Set cookies from WebView
            try {
                String cookies = android.webkit.CookieManager.getInstance().getCookie(url);
                if (cookies != null && !cookies.isEmpty()) {
                    request.addRequestHeader("Cookie", cookies);
                }
            } catch (Exception e) { }

            // Set user agent
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }

            // Set referer
            request.addRequestHeader("Referer", url);

            long id = dm.enqueue(request);
            Toast.makeText(context, "Downloading: " + filename, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static String guessFilename(String contentDisposition, String url, String mimeType) {
        // Try Content-Disposition header first
        if (contentDisposition != null && !contentDisposition.isEmpty()) {
            String name = parseContentDisposition(contentDisposition);
            if (name != null) return name;
        }

        // Try URL path
        if (url != null) {
            try {
                String decoded = URLDecoder.decode(url, "UTF-8");
                int queryIdx = decoded.indexOf('?');
                if (queryIdx > 0) decoded = decoded.substring(0, queryIdx);
                int slashIdx = decoded.lastIndexOf('/');
                if (slashIdx >= 0 && slashIdx < decoded.length() - 1) {
                    String name = decoded.substring(slashIdx + 1);
                    if (!name.isEmpty()) return name;
                }
            } catch (Exception e) { }
        }

        // Fallback
        String ext = getExtensionForMime(mimeType);
        return "download_" + System.currentTimeMillis() + ext;
    }

    private static String parseContentDisposition(String cd) {
        try {
            // Look for filename*=UTF-8''... or filename="..." or filename=...
            int idx = cd.indexOf("filename*=");
            if (idx >= 0) {
                String rest = cd.substring(idx + 10);
                int semi = rest.indexOf(';');
                if (semi >= 0) rest = rest.substring(0, semi);
                // Remove charset prefix
                int quote = rest.indexOf("''");
                if (quote >= 0) rest = rest.substring(quote + 2);
                return URLDecoder.decode(rest.trim(), "UTF-8");
            }
            idx = cd.indexOf("filename=");
            if (idx >= 0) {
                String rest = cd.substring(idx + 9);
                int semi = rest.indexOf(';');
                if (semi >= 0) rest = rest.substring(0, semi);
                rest = rest.trim();
                if (rest.startsWith("\"") && rest.endsWith("\"")) {
                    rest = rest.substring(1, rest.length() - 1);
                }
                return rest;
            }
        } catch (Exception e) { }
        return null;
    }

    private static String getExtensionForMime(String mime) {
        if (mime == null) return "";
        switch (mime) {
            case "application/pdf": return ".pdf";
            case "application/zip": return ".zip";
            case "application/x-rar-compressed": return ".rar";
            case "application/x-7z-compressed": return ".7z";
            case "application/octet-stream": return "";
            case "image/jpeg": return ".jpg";
            case "image/png": return ".png";
            case "image/gif": return ".gif";
            case "image/webp": return ".webp";
            case "image/svg+xml": return ".svg";
            case "audio/mpeg": return ".mp3";
            case "audio/ogg": return ".ogg";
            case "audio/wav": return ".wav";
            case "audio/flac": return ".flac";
            case "video/mp4": return ".mp4";
            case "video/webm": return ".webm";
            case "video/x-matroska": return ".mkv";
            case "text/plain": return ".txt";
            case "text/html": return ".html";
            case "application/json": return ".json";
            case "application/vnd.android.package-archive": return ".apk";
            case "application/msword": return ".doc";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document": return ".docx";
            case "application/vnd.ms-excel": return ".xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": return ".xlsx";
            case "application/vnd.ms-powerpoint": return ".ppt";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation": return ".pptx";
            default: return "";
        }
    }

    public static File getDownloadDir() {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<DownloadItem> getDownloads(Context context) {
        List<DownloadItem> list = new ArrayList<>();
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterByStatus(DownloadManager.STATUS_RUNNING | DownloadManager.STATUS_PENDING |
                    DownloadManager.STATUS_PAUSED | DownloadManager.STATUS_SUCCESSFUL | DownloadManager.STATUS_FAILED);
            Cursor c = dm.query(query);
            while (c.moveToNext()) {
                DownloadItem item = new DownloadItem();
                item.id = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID));
                item.title = c.getString(c.getColumnIndex(DownloadManager.COLUMN_TITLE));
                item.url = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI));
                item.status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                item.total = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                item.downloaded = c.getLong(c.getColumnIndex("bytes_so_far"));
                item.mimeType = c.getString(c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE));
                list.add(item);
            }
            c.close();
        } catch (Exception e) { }
        return list;
    }

    public static void cancelDownload(Context context, long id) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            dm.remove(id);
        } catch (Exception e) { }
    }

    public static void clearCompletedDownloads(Context context) {
        try {
            DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL | DownloadManager.STATUS_FAILED);
            Cursor c = dm.query(query);
            List<Long> ids = new ArrayList<>();
            while (c.moveToNext()) {
                ids.add(c.getLong(c.getColumnIndex(DownloadManager.COLUMN_ID)));
            }
            c.close();
            for (long id : ids) {
                dm.remove(id);
            }
        } catch (Exception e) { }
    }
}
