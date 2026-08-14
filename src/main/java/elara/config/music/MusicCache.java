package elara.config.music;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.PriorityQueue;

public class MusicCache {
   public static final File CACHE_DIR = new File("./config/Elara/music/.cache");
   public static final File AUDIO_DIR = new File(CACHE_DIR, "Audio");
   public static final File COVER_DIR = new File(CACHE_DIR, "Cover");
   private static final long MAX_CACHE_SIZE = 524288000L;
   private static final int BUFFER_SIZE = 8192;

   public static void init() {
      try {
         if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
         }

         if (!AUDIO_DIR.exists()) {
            AUDIO_DIR.mkdirs();
         }

         if (!COVER_DIR.exists()) {
            COVER_DIR.mkdirs();
         }
      } catch (Exception e) {
         System.err.println("[Elara] MusicCache init failed: " + e.getMessage());
      }
   }

   private static String getCacheKey(String url) {
      try {
         MessageDigest md = MessageDigest.getInstance("MD5");
         byte[] digest = md.digest(url.getBytes("UTF-8"));
         StringBuilder sb = new StringBuilder();

         for (byte b : digest) {
            sb.append(String.format("%02x", b & 255));
         }

         return sb.toString();
      } catch (Exception e) {
         return String.valueOf(Math.abs(url.hashCode()));
      }
   }

   private static String detectExt(String url) {
      String lower = url.toLowerCase();
      if (lower.endsWith(".ogg")) {
         return "ogg";
      } else if (lower.endsWith(".wav")) {
         return "wav";
      } else if (lower.endsWith(".flac")) {
         return "flac";
      } else {
         return lower.endsWith(".m4a") ? "m4a" : "mp3";
      }
   }

   private static File getAudioFile(String url) {
      String key = getCacheKey(url);

      for (String ext : new String[]{"mp3", "ogg", "wav", "flac", "m4a"}) {
         File f = new File(AUDIO_DIR, key + "." + ext);
         if (f.exists() && f.length() > 0L) {
            return f;
         }
      }

      return null;
   }

   public static File getCachedFile(String url) {
      if (url != null && !url.isEmpty()) {
         File audio = getAudioFile(url);
         if (audio != null) {
            audio.setLastModified(System.currentTimeMillis());
            return audio;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public static boolean isCached(Song song) {
      if (song == null) {
         return false;
      } else {
         return song.isLocal() ? true : getCachedFile(song.getUrl()) != null;
      }
   }

   public static File getPlayableFile(Song song) {
      if (song == null) {
         return null;
      } else {
         return song.isLocal() ? song.getFile() : getCachedFile(song.getUrl());
      }
   }

   public static Thread downloadAsync(String url, MusicCache.DownloadListener listener) {
      Thread thread = new Thread(() -> {
         try {
            File result = downloadSync(url, progress -> {
               if (listener != null) {
                  listener.onProgress(progress);
               }
            });
            if (listener != null) {
               listener.onComplete(result);
            }
         } catch (Exception e) {
            System.err.println("[Elara] Music download failed: " + e.getMessage());
            if (listener != null) {
               listener.onError(e.getMessage());
            }
         }
      }, "Elara-MusicDownload");
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   public static File downloadSync(String url, MusicCache.ProgressCallback listener) throws Exception {
      if (url != null && !url.isEmpty()) {
         init();
         File cached = getAudioFile(url);
         if (cached != null) {
            cached.setLastModified(System.currentTimeMillis());
            return cached;
         }

         String key = getCacheKey(url);
         String ext = detectExt(url);
         File finalFile = new File(AUDIO_DIR, key + "." + ext);
         File tempFile = new File(AUDIO_DIR, key + "." + ext + ".tmp");
         HttpURLConnection conn = null;
         InputStream in = null;
         FileOutputStream fos = null;
         FilterOutputStream bos = null;

         try {
            URL urlObj = new URL(url);
            conn = (HttpURLConnection)urlObj.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 ElaraClient");
            conn.connect();
            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
               throw new Exception("HTTP " + responseCode);
            }

            int contentLength = conn.getContentLength();
            in = new BufferedInputStream(conn.getInputStream(), 8192);
            fos = new FileOutputStream(tempFile);
            bos = new BufferedOutputStream(fos, 8192);
            byte[] buffer = new byte[8192];
            long totalRead = 0L;

            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
               ((BufferedOutputStream)bos).write(buffer, 0, bytesRead);
               totalRead += bytesRead;
               if (listener != null && contentLength > 0) {
                  listener.onProgress((float)totalRead / contentLength);
               }

               if (totalRead > 524288000L) {
                  throw new Exception("Cache size limit exceeded");
               }
            }

            ((BufferedOutputStream)bos).flush();
            if (tempFile.renameTo(finalFile)) {
               finalFile.setLastModified(System.currentTimeMillis());
               cleanupLRU();
               return finalFile;
            } else {
               throw new Exception("Failed to rename temp file");
            }
         } finally {
            try {
               if (bos != null) {
                  bos.close();
               }
            } catch (Exception var38) {
            }

            try {
               if (fos != null) {
                  fos.close();
               }
            } catch (Exception var37) {
            }

            try {
               if (in != null) {
                  in.close();
               }
            } catch (Exception var36) {
            }

            if (conn != null) {
               try {
                  conn.disconnect();
               } catch (Exception var35) {
               }
            }

            if (tempFile.exists()) {
               try {
                  tempFile.delete();
               } catch (Exception var34) {
               }
            }
         }
      } else {
         throw new IllegalArgumentException("URL is empty");
      }
   }

   public static File saveCover(String url, byte[] imageData, String mime) {
      if (url != null && imageData != null && imageData.length != 0) {
         String key = getCacheKey(url);
         String ext = "img";
         if (mime != null) {
            String ml = mime.toLowerCase();
            if (ml.contains("png")) {
               ext = "png";
            } else if (ml.contains("jpeg") || ml.contains("jpg")) {
               ext = "jpg";
            }
         }

         File coverFile = new File(COVER_DIR, key + "." + ext);

         try {
            FileOutputStream fos = new FileOutputStream(coverFile);

            try {
               fos.write(imageData);
            } catch (Throwable var10) {
               try {
                  fos.close();
               } catch (Throwable var9) {
                  var10.addSuppressed(var9);
               }

               throw var10;
            }

            fos.close();
            return coverFile;
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static File getCoverFile(String url) {
      if (url == null) {
         return null;
      }

      String key = getCacheKey(url);

      for (String ext : new String[]{"jpg", "png", "jpeg", "img"}) {
         File f = new File(COVER_DIR, key + "." + ext);
         if (f.exists() && f.length() > 0L) {
            return f;
         }
      }

      return null;
   }

   public static boolean saveLyrics(String url, String lyrics) {
      if (url == null) {
         return false;
      }

      if (lyrics == null) {
         return false;
      }

      if (lyrics.isEmpty()) {
         return false;
      }

      String key = getCacheKey(url);
      File lrcFile = new File(COVER_DIR, key + ".lrc");

      try {
         FileOutputStream fos = new FileOutputStream(lrcFile);

         boolean var6;
         try {
            fos.write(lyrics.getBytes("UTF-8"));
            boolean bl = true;
            var6 = bl;
         } catch (Throwable var8) {
            try {
               fos.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         fos.close();
         return var6;
      } catch (Exception e) {
         return false;
      }
   }

   public static String getLyrics(String url) {
      if (url == null) {
         return null;
      }

      String key = getCacheKey(url);
      File lrcFile = new File(COVER_DIR, key + ".lrc");
      if (lrcFile.exists() && lrcFile.length() != 0L) {
         try {
            byte[] data = new byte[(int)lrcFile.length()];
            FileInputStream fis = new FileInputStream(lrcFile);

            try {
               fis.read(data);
            } catch (Throwable var8) {
               try {
                  fis.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            fis.close();
            return new String(data, "UTF-8");
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static long getCacheSize() {
      if (!CACHE_DIR.exists()) {
         return 0L;
      }

      long total = 0L;
      File[] dirs = CACHE_DIR.listFiles();
      if (dirs == null) {
         return 0L;
      }

      for (File dir : dirs) {
         File[] files;
         if (dir.isDirectory() && (files = dir.listFiles()) != null) {
            for (File f : files) {
               if (f.isFile()) {
                  total += f.length();
               }
            }
         }
      }

      return total;
   }

   public static int getCachedCount() {
      if (!AUDIO_DIR.exists()) {
         return 0;
      }

      int count = 0;
      File[] files = AUDIO_DIR.listFiles();
      if (files == null) {
         return 0;
      }

      for (File f : files) {
         if (f.isFile() && isAudioFileExtension(f.getName())) {
            count++;
         }
      }

      return count;
   }

   private static boolean isAudioFileExtension(String name) {
      String lower = name.toLowerCase();
      return lower.endsWith(".mp3") || lower.endsWith(".ogg") || lower.endsWith(".wav") || lower.endsWith(".flac") || lower.endsWith(".m4a");
   }

   public static void cleanupLRU() {
      if (AUDIO_DIR.exists()) {
         long currentSize = getCacheSize();
         if (currentSize > 524288000L) {
            PriorityQueue<File> heap = new PriorityQueue<>(Comparator.comparingLong(File::lastModified));
            File[] files = AUDIO_DIR.listFiles();
            if (files != null) {
               for (File f : files) {
                  if (f.isFile() && isAudioFileExtension(f.getName())) {
                     heap.add(f);
                  }
               }

               while (currentSize > 4.194304E8 && !heap.isEmpty()) {
                  File oldest = heap.poll();
                  long fileSize = oldest.length();
                  oldest.delete();
                  deleteCorrespondingCoverAndLyrics(oldest.getName());
                  currentSize -= fileSize;
               }
            }
         }
      }
   }

   private static void deleteCorrespondingCoverAndLyrics(String audioFileName) {
      String key = audioFileName.substring(0, audioFileName.lastIndexOf(46));

      for (String ext : new String[]{"jpg", "png", "jpeg", "img", "lrc"}) {
         File f = new File(COVER_DIR, key + "." + ext);
         if (f.exists()) {
            f.delete();
         }
      }
   }

   public static void clearAll() {
      File[] files;
      if (AUDIO_DIR.exists() && (files = AUDIO_DIR.listFiles()) != null) {
         for (File f : files) {
            f.delete();
         }
      }

      if (COVER_DIR.exists() && (files = COVER_DIR.listFiles()) != null) {
         for (File f : files) {
            f.delete();
         }
      }
   }

   public static String formatSize(long bytes) {
      if (bytes < 1024L) {
         return bytes + " B";
      } else if (bytes < 1048576L) {
         return String.format("%.1f KB", (float)bytes / 1024.0F);
      } else {
         return bytes < 1073741824L ? String.format("%.1f MB", (float)bytes / 1048576.0F) : String.format("%.2f GB", (float)bytes / 1.0737418E9F);
      }
   }

   public interface DownloadListener {
      void onProgress(float var1);

      void onComplete(File var1);

      void onError(String var1);
   }

   @FunctionalInterface
   public interface ProgressCallback {
      void onProgress(float var1);
   }
}
