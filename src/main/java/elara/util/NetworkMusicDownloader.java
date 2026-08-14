/*
 * Decompiled with CFR 0.152.
 */
package elara.util;

import elara.config.music.MusicCache;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetworkMusicDownloader {
    private static NetworkMusicDownloader instance;
    private final OkHttpClient httpClient = new OkHttpClient.Builder().connectTimeout(15L, TimeUnit.SECONDS).readTimeout(30L, TimeUnit.SECONDS).build();
    private final File cacheDir;
    private final Map<String, DownloadTask> downloadTasks;
    private final Logger logger;
    private static final int BUFFER_SIZE = 8192;

    private NetworkMusicDownloader() {
        MusicCache.init();
        this.cacheDir = MusicCache.AUDIO_DIR;
        this.downloadTasks = new ConcurrentHashMap<String, DownloadTask>();
        this.logger = LoggerFactory.getLogger(NetworkMusicDownloader.class);
    }

    public static synchronized NetworkMusicDownloader getInstance() {
        if (instance == null) {
            instance = new NetworkMusicDownloader();
        }
        return instance;
    }

    public void download(String url, ProgressListener listener) {
        if (url == null || url.isEmpty()) {
            if (listener != null) {
                listener.onError("URL cannot be null or empty");
            }
            return;
        }
        String cacheKey = this.getCacheFileName(url);
        String ext = this.detectExt(url);
        File cachedFile = new File(this.cacheDir, cacheKey + "." + ext);
        if (cachedFile.exists() && cachedFile.length() > 0L) {
            cachedFile.setLastModified(System.currentTimeMillis());
            if (listener != null) {
                listener.onComplete(cachedFile);
            }
            return;
        }
        DownloadTask existing = this.downloadTasks.get(url);
        if (existing != null && !existing.cancelled.get()) {
            return;
        }
        File tempFile = new File(this.cacheDir, cacheKey + "." + ext + ".tmp");
        if (tempFile.exists()) {
            tempFile.delete();
        }
        Request request = new Request.Builder().url(url).get()
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Referer", "https://music.163.com/")
                .build();
        Call call = this.httpClient.newCall(request);
        DownloadTask task = new DownloadTask(call, tempFile);
        this.downloadTasks.put(url, task);
        new Thread(() -> {
            block48: {
                try {
                    Response response = call.execute();
                    if (task.cancelled.get()) {
                        response.close();
                        return;
                    }
                    if (!response.isSuccessful()) {
                        response.close();
                        this.downloadTasks.remove(url);
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        if (listener != null) {
                            listener.onError("HTTP error: " + response.code());
                        }
                        return;
                    }
                    ResponseBody body = response.body();
                    if (body == null) {
                        response.close();
                        this.downloadTasks.remove(url);
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        if (listener != null) {
                            listener.onError("Empty response body");
                        }
                        return;
                    }
                    String contentType = body.contentType() != null ? body.contentType().toString() : "";
                    if (contentType.startsWith("text/html")) {
                        response.close();
                        this.downloadTasks.remove(url);
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        if (listener != null) {
                            listener.onError("Server returned HTML instead of audio");
                        }
                        return;
                    }
                    long contentLength = body.contentLength();
                    try (InputStream is = body.byteStream();
                         FileOutputStream fos = new FileOutputStream(tempFile);){
                        int bytesRead;
                        byte[] buffer = new byte[8192];
                        long totalRead = 0L;
                        int lastPercent = -1;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            int percent;
                            if (task.cancelled.get()) {
                                throw new IOException("cancelled");
                            }
                            fos.write(buffer, 0, bytesRead);
                            if (contentLength <= 0L || listener == null || (percent = (int)((totalRead += (long)bytesRead) * 100L / contentLength)) == lastPercent) continue;
                            lastPercent = percent;
                            listener.onProgress(percent);
                        }
                        fos.flush();
                    }
                    if (task.cancelled.get()) {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        this.downloadTasks.remove(url);
                        return;
                    }
                    if (tempFile.length() == 0L) {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        this.downloadTasks.remove(url);
                        if (listener != null) {
                            listener.onError("Downloaded file is empty");
                        }
                        return;
                    }
                    File finalFile = new File(this.cacheDir, cacheKey + "." + ext);
                    boolean renamed = tempFile.renameTo(finalFile);
                    this.downloadTasks.remove(url);
                    if (!renamed) {
                        if (tempFile.exists()) {
                            tempFile.delete();
                        }
                        if (listener != null) {
                            listener.onError("Failed to rename temp file");
                        }
                        return;
                    }
                    finalFile.setLastModified(System.currentTimeMillis());
                    this.logger.info("Download complete: {}", (Object)finalFile.getName());
                    if (listener != null) {
                        listener.onComplete(finalFile);
                    }
                }
                catch (IOException e) {
                    this.downloadTasks.remove(url);
                    if (tempFile.exists()) {
                        tempFile.delete();
                    }
                    if (task.cancelled.get()) {
                        if (listener != null) {
                            listener.onError("Download cancelled");
                        }
                    }
                    this.logger.error("Download failed: {}", (Object)e.getMessage());
                    if (listener == null) break block48;
                    listener.onError("Network error: " + e.getMessage());
                }
            }
        }, "MusicDownloader").start();
    }

    public boolean cancelDownload(String url) {
        if (url == null) {
            return false;
        }
        DownloadTask task = this.downloadTasks.remove(url);
        if (task != null && !task.cancelled.get()) {
            task.cancel();
            return true;
        }
        return false;
    }

    public void shutdown() {
        for (DownloadTask task : this.downloadTasks.values()) {
            task.cancel();
        }
        this.downloadTasks.clear();
        File[] files = this.cacheDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || !f.getName().endsWith(".tmp")) continue;
                f.delete();
            }
        }
        this.logger.info("NetworkMusicDownloader shutdown");
    }

    private String getCacheFileName(String url) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b & 0xFF));
            }
            return sb.toString();
        }
        catch (Exception e) {
            return String.valueOf(Math.abs(url.hashCode()));
        }
    }

    private String detectExt(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".ogg")) {
            return "ogg";
        }
        if (lower.endsWith(".wav")) {
            return "wav";
        }
        if (lower.endsWith(".flac")) {
            return "flac";
        }
        if (lower.endsWith(".m4a")) {
            return "m4a";
        }
        return "mp3";
    }

    public File getCacheDir() {
        return this.cacheDir;
    }

    public boolean isCached(String url) {
        if (url == null) {
            return false;
        }
        String cacheKey = this.getCacheFileName(url);
        for (String ext : new String[]{"mp3", "ogg", "wav", "flac", "m4a"}) {
            File f = new File(this.cacheDir, cacheKey + "." + ext);
            if (!f.exists() || f.length() <= 0L) continue;
            return true;
        }
        return false;
    }

    public File getCachedFile(String url) {
        if (url == null) {
            return null;
        }
        String cacheKey = this.getCacheFileName(url);
        for (String ext : new String[]{"mp3", "ogg", "wav", "flac", "m4a"}) {
            File f = new File(this.cacheDir, cacheKey + "." + ext);
            if (!f.exists() || f.length() <= 0L) continue;
            return f;
        }
        return null;
    }

    private static class DownloadTask {
        final Call call;
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        volatile File tempFile;

        DownloadTask(Call call, File tempFile) {
            this.call = call;
            this.tempFile = tempFile;
        }

        void cancel() {
            if (this.cancelled.compareAndSet(false, true)) {
                this.call.cancel();
                if (this.tempFile != null && this.tempFile.exists()) {
                    this.tempFile.delete();
                }
            }
        }
    }

    public static interface ProgressListener {
        public void onProgress(int var1);

        public void onComplete(File var1);

        public void onError(String var1);
    }
}

