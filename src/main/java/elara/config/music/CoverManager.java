package elara.config.music;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CoverManager {
   private static final File CACHE_DIR = new File("./config/Elara/music/.cache/Cover");
   private static final File PICTURE_DIR = new File("./config/Elara/music/.cache/Picture");
   private static final ConcurrentHashMap<String, String> coverCache = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, Boolean> downloadingUrls = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, List<String>> pendingSongFolders = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, String> hashCache = new ConcurrentHashMap<>();
   private static final ExecutorService downloadExecutor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "CoverDownloader");
      t.setDaemon(true);
      return t;
   });
   private static final ThreadLocal<MessageDigest> md5Digest = ThreadLocal.withInitial(() -> {
      try {
         return MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
         return null;
      }
   });

   public static void init() {
      try {
         if (!CACHE_DIR.exists()) {
            CACHE_DIR.mkdirs();
         }

         if (!PICTURE_DIR.exists()) {
            PICTURE_DIR.mkdirs();
         }
      } catch (Throwable var1) {
      }
   }

   public static String getCoverPath(Song song) {
      if (song == null) {
         return null;
      }

      if (song.hasCover()) {
         String key = getFileHash(song.getFile());
         if (coverCache.containsKey(key)) {
            return coverCache.get(key);
         }

         String cached = loadFromCache(key);
         if (cached != null) {
            coverCache.put(key, cached);
            return cached;
         }

         String path = saveCover(key, song.getCoverImage(), song.getCoverMime());
         if (path != null) {
            coverCache.put(key, path);
         }

         return path;
      } else {
         String songUrl = song.getUrl();
         String coverUrl = song.getCoverUrl();
         File musicCacheCover;
         if (songUrl != null && !songUrl.isEmpty() && (musicCacheCover = MusicCache.getCoverFile(songUrl)) != null) {
            String path = musicCacheCover.getAbsolutePath();
            coverCache.put(songUrl, path);
            return path;
         } else {
            return coverUrl != null && !coverUrl.isEmpty() ? getNetworkCoverPath(coverUrl, songUrl) : null;
         }
      }
   }

   public static String getNetworkCoverPath(String coverUrl) {
      return getNetworkCoverPath(coverUrl, null);
   }

   public static void preloadCover(String coverUrl, String songUrl) {
      if (coverUrl != null && !coverUrl.isEmpty()) {
         File musicCacheCover;
         if (songUrl == null || songUrl.isEmpty() || (musicCacheCover = MusicCache.getCoverFile(songUrl)) == null || !musicCacheCover.exists()) {
            String key = getUrlHash(coverUrl);
            String cachedPath = coverCache.get(key);
            if (cachedPath == null && (cachedPath = loadFromCache(key)) != null) {
               coverCache.put(key, cachedPath);
            }

            if (cachedPath != null) {
               if (songUrl != null && !songUrl.isEmpty()) {
                  ensureCoverInSongFolder(cachedPath, songUrl);
               }
            } else if (downloadingUrls.putIfAbsent(key, true) != null) {
               if (songUrl != null && !songUrl.isEmpty()) {
                  pendingSongFolders.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(songUrl);
               }
            } else {
               downloadExecutor.submit(() -> {
                  try {
                     String path = downloadAndSaveCover(key, coverUrl, songUrl);
                     if (path != null) {
                        coverCache.put(key, path);
                        List<String> pending = pendingSongFolders.remove(key);
                        byte[] imageData;
                        if (pending != null && !pending.isEmpty() && (imageData = readFileBytes(new File(path))) != null) {
                           for (String pendingSongUrl : pending) {
                              MusicCache.saveCover(pendingSongUrl, imageData, null);
                           }
                        }
                     }
                  } catch (Exception e) {
                     System.err.println("[Elara] Failed to preload cover: " + e.getMessage());
                  } finally {
                     downloadingUrls.remove(key);
                  }
               });
            }
         }
      }
   }

   public static String getNetworkCoverPath(String coverUrl, String songUrl) {
      if (coverUrl != null && !coverUrl.isEmpty()) {
         String key = getUrlHash(coverUrl);
         if (coverCache.containsKey(key)) {
            String path = coverCache.get(key);
            if (songUrl != null && !songUrl.isEmpty()) {
               ensureCoverInSongFolder(path, songUrl);
            }

            return path;
         } else {
            File musicCacheCover;
            if (songUrl != null && !songUrl.isEmpty() && (musicCacheCover = MusicCache.getCoverFile(songUrl)) != null) {
               String path = musicCacheCover.getAbsolutePath();
               coverCache.put(key, path);
               return path;
            }

            String cached = loadFromCache(key);
            if (cached != null) {
               coverCache.put(key, cached);
               if (songUrl != null && !songUrl.isEmpty()) {
                  ensureCoverInSongFolder(cached, songUrl);
               }

               return cached;
            } else {
               if (downloadingUrls.putIfAbsent(key, true) != null) {
                  return null;
               }

               downloadExecutor.submit(() -> {
                  try {
                     String path = downloadAndSaveCover(key, coverUrl, songUrl);
                     if (path != null) {
                        coverCache.put(key, path);
                     }
                  } catch (Exception e) {
                     System.err.println("[Elara] Failed to download cover: " + e.getMessage());
                  } finally {
                     downloadingUrls.remove(key);
                  }
               });
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static String downloadAndSaveCover(String hash, String coverUrl, String songUrl) {
      HttpURLConnection conn = null;

      try {
         URL url = new URL(coverUrl);
         conn = (HttpURLConnection)url.openConnection();
         conn.setRequestMethod("GET");
         conn.setConnectTimeout(10000);
         conn.setReadTimeout(15000);
         conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
         int responseCode = conn.getResponseCode();
         if (responseCode != 200) {
            return null;
         }

         InputStream is = conn.getInputStream();
         byte[] imageData = readAllBytes(is);
         is.close();
         if (imageData.length == 0) {
            return null;
         }

         String ext = "img";
         String contentType = conn.getContentType();
         if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("png")) {
               ext = "png";
            } else if (ct.contains("jpeg") || ct.contains("jpg")) {
               ext = "jpg";
            }
         }

         File saveDir = songUrl != null && !songUrl.isEmpty() ? CACHE_DIR : PICTURE_DIR;
         if (!saveDir.exists()) {
            saveDir.mkdirs();
         }

         File outFile = new File(saveDir, hash + "." + ext);
         FileOutputStream fos = new FileOutputStream(outFile);

         try {
            fos.write(imageData);
         } catch (Throwable var22) {
            try {
               fos.close();
            } catch (Throwable var21) {
               var22.addSuppressed(var21);
            }

            throw var22;
         }

         fos.close();
         if (songUrl != null && !songUrl.isEmpty()) {
            MusicCache.saveCover(songUrl, imageData, contentType);
         }

         return outFile.getAbsolutePath();
      } catch (Exception e) {
         return null;
      } finally {
         if (conn != null) {
            conn.disconnect();
         }
      }
   }

   private static void ensureCoverInSongFolder(String coverPath, String songUrl) {
      if (coverPath != null && songUrl != null && !songUrl.isEmpty()) {
         File musicCacheCover = MusicCache.getCoverFile(songUrl);
         if (musicCacheCover == null || !musicCacheCover.exists()) {
            File srcFile = new File(coverPath);
            if (srcFile.exists() && srcFile.length() != 0L) {
               downloadExecutor.submit(() -> {
                  try {
                     byte[] data = new byte[(int)srcFile.length()];
                     FileInputStream fis = new FileInputStream(srcFile);

                     try {
                        fis.read(data);
                     } catch (Throwable var7) {
                        try {
                           fis.close();
                        } catch (Throwable var6) {
                           var7.addSuppressed(var6);
                        }

                        throw var7;
                     }

                     fis.close();
                     MusicCache.saveCover(songUrl, data, null);
                  } catch (Exception e) {
                     System.err.println("[Elara] Failed to copy cover to song folder: " + e.getMessage());
                  }
               });
            }
         }
      }
   }

   private static byte[] readAllBytes(InputStream is) throws Exception {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];

      int n;
      while ((n = is.read(buf)) != -1) {
         bos.write(buf, 0, n);
      }

      return bos.toByteArray();
   }

   private static String loadFromCache(String hash) {
      try {
         for (File dir : new File[]{CACHE_DIR, PICTURE_DIR}) {
            File pngFile = new File(dir, hash + ".png");
            if (pngFile.exists() && pngFile.length() > 0L) {
               return pngFile.getAbsolutePath();
            }

            File jpgFile = new File(dir, hash + ".jpg");
            if (jpgFile.exists() && jpgFile.length() > 0L) {
               return jpgFile.getAbsolutePath();
            }

            File jpegFile = new File(dir, hash + ".jpeg");
            if (jpegFile.exists() && jpegFile.length() > 0L) {
               return jpegFile.getAbsolutePath();
            }
         }

         return null;
      } catch (Throwable e) {
         return null;
      }
   }

   private static String saveCover(String hash, byte[] imageData, String mime) {
      if (imageData != null && imageData.length != 0) {
         try {
            if (!CACHE_DIR.exists()) {
               CACHE_DIR.mkdirs();
            }

            String ext = "img";
            if (mime != null) {
               String ml = mime.toLowerCase();
               if (ml.contains("png")) {
                  ext = "png";
               } else if (ml.contains("jpeg") || ml.contains("jpg")) {
                  ext = "jpg";
               }
            }

            File outFile = new File(CACHE_DIR, hash + "." + ext);
            FileOutputStream fos = new FileOutputStream(outFile);

            try {
               fos.write(imageData);
            } catch (Throwable var9) {
               try {
                  fos.close();
               } catch (Throwable var8) {
                  var9.addSuppressed(var8);
               }

               throw var9;
            }

            fos.close();
            return outFile.getAbsolutePath();
         } catch (Throwable e) {
            System.err.println("[Elara] Failed to save cover image: " + e.getMessage());
            return null;
         }
      } else {
         return null;
      }
   }

   private static String getFileHash(File file) {
      String key = file.getName() + "_" + file.length();
      String cached = hashCache.get(key);
      if (cached != null) {
         return cached;
      } else {
         String hash = computeHash(key);
         if (hash != null) {
            hashCache.put(key, hash);
            return hash;
         } else {
            return String.valueOf(file.getName().hashCode());
         }
      }
   }

   private static String getUrlHash(String url) {
      String cached = hashCache.get(url);
      if (cached != null) {
         return cached;
      } else {
         String hash = computeHash(url);
         if (hash != null) {
            hashCache.put(url, hash);
            return hash;
         } else {
            return String.valueOf(url.hashCode());
         }
      }
   }

   private static String computeHash(String input) {
      MessageDigest md = md5Digest.get();
      if (md == null) {
         return null;
      }

      md.reset();
      byte[] digest = md.digest(input.getBytes());
      BigInteger bi = new BigInteger(1, digest);
      return bi.toString(16);
   }

   private static byte[] readFileBytes(File file) {
      if (file != null && file.exists() && file.length() != 0L) {
         try {
            byte[] data = new byte[(int)file.length()];
            FileInputStream fis = new FileInputStream(file);

            try {
               int offset = 0;

               while (offset < data.length) {
                  int read = fis.read(data, offset, data.length - offset);
                  if (read < 0) {
                     break;
                  }

                  offset += read;
               }
            } catch (Throwable var6) {
               try {
                  fis.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }

               throw var6;
            }

            fis.close();
            return data;
         } catch (Exception e) {
            return null;
         }
      } else {
         return null;
      }
   }
}
