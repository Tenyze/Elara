package elara.config.music;

import elara.config.NotificationHelper;
import java.util.HashMap;
import java.util.Map;

public class MusicApiManager {
   private static MusicApiManager instance;
   private final Map<MusicApiManager.Platform, MusicApi> apiMap = new HashMap<>();
   private MusicApiManager.Platform currentPlatform = MusicApiManager.Platform.NETEASE_CLOUD;
   private final LoginManager loginManager;

   private MusicApiManager(LoginManager loginManager) {
      this.loginManager = loginManager;
      this.initApis();
   }

   public static synchronized MusicApiManager getInstance(LoginManager loginManager) {
      if (instance == null) {
         instance = new MusicApiManager(loginManager);
      }

      return instance;
   }

   public static synchronized MusicApiManager getInstance() {
      return instance;
   }

   public static synchronized void resetInstance(LoginManager loginManager) {
      instance = new MusicApiManager(loginManager);
   }

   private void initApis() {
      this.apiMap.put(MusicApiManager.Platform.NETEASE_CLOUD, new NetEaseMusicApi(this.loginManager));
   }

   public MusicApi getCurrentApi() {
      return this.apiMap.get(this.currentPlatform);
   }

   public MusicApiManager.Platform getCurrentPlatform() {
      return this.currentPlatform;
   }

   public void switchPlatform(MusicApiManager.Platform platform) {
      if (this.apiMap.containsKey(platform)) {
         this.currentPlatform = platform;
         NotificationHelper.sendPlatformSwitched(platform.getDisplayName());
      }
   }

   public MusicApiManager.Platform[] getAvailablePlatforms() {
      return MusicApiManager.Platform.values();
   }

   public boolean supportsLogin(MusicApiManager.Platform platform) {
      return platform == MusicApiManager.Platform.NETEASE_CLOUD;
   }

   public boolean supportsQrLogin(MusicApiManager.Platform platform) {
      return platform == MusicApiManager.Platform.NETEASE_CLOUD;
   }

   public boolean testConnection() {
      MusicApi api = this.getCurrentApi();
      return api != null && api.testConnection();
   }

   public enum Platform {
      NETEASE_CLOUD("NetEase Cloud Music", "");

      private final String displayName;
      private final String icon;

      Platform(String displayName, String icon) {
         this.displayName = displayName;
         this.icon = icon;
      }

      public String getDisplayName() {
         return this.displayName;
      }

      public String getIcon() {
         return this.icon;
      }
   }
}
