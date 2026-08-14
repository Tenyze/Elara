package elara.config.music;

import elara.config.NotificationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;

public class CacheManager {
   private static CacheManager instance;
   private final Logger logger = LoggerFactory.getLogger(CacheManager.class);

   public static synchronized CacheManager getInstance() {
      if (instance == null) {
         instance = new CacheManager();
      }

      return instance;
   }

   private CacheManager() {
      MusicCache.init();
   }

   public void clearCache() {
      MusicCache.clearAll();
      this.logger.info("Cache cleared");
      NotificationHelper.sendCacheCleared();
   }

   public long getCacheSizeBytes() {
      return MusicCache.getCacheSize();
   }

   public long getCacheSizeMB() {
      return this.getCacheSizeBytes() / 1048576L;
   }

   public long getFileCount() {
      return MusicCache.getCachedCount();
   }

   public String getFormattedCacheSize() {
      return this.formatSize(this.getCacheSizeBytes());
   }

   public String formatSize(long bytes) {
      return MusicCache.formatSize(bytes);
   }

   public File getCacheDir() {
      return MusicCache.CACHE_DIR;
   }
}
