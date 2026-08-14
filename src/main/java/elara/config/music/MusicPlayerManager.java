package elara.config.music;

import elara.config.gui.TextInputHandler;

public class MusicPlayerManager {
   private static MusicPlayerManager instance;
   private final Playlist playlist;
   private final MusicEngine engine;
   private final LoginManager loginManager;
   private final NetEaseMusicApi netEaseApi;
   private final MusicApiManager apiManager;

   private MusicPlayerManager() {
      Playlist pl = null;
      MusicEngine eng = null;
      LoginManager lm = null;
      NetEaseMusicApi api = null;

      try {
         CoverManager.init();
         lm = new LoginManager();
         api = new NetEaseMusicApi(lm);
         pl = new Playlist();
         eng = new MusicEngine(pl);
      } catch (Throwable e) {
         System.err.println("[Elara] Music player init failed: " + e.getMessage());
      }

      this.playlist = pl;
      this.engine = eng;
      this.loginManager = lm;
      this.netEaseApi = api;
      this.apiManager = MusicApiManager.getInstance(lm);
   }

   public static void init() {
      if (instance == null) {
         try {
            instance = new MusicPlayerManager();
            TextInputHandler.init();
         } catch (Throwable e) {
            System.err.println("[Elara] Music player manager init failed: " + e.getMessage());
         }
      }
   }

   public static boolean isInitialized() {
      return instance != null;
   }

   public static MusicPlayerManager getInstance() {
      return instance;
   }

   public static MusicEngine getEngine() {
      return instance != null ? instance.engine : null;
   }

   public static Playlist getPlaylist() {
      return instance != null ? instance.playlist : null;
   }

   public static LoginManager getLoginManager() {
      return instance != null ? instance.loginManager : null;
   }

   public static NetEaseMusicApi getNetEaseApi() {
      return instance != null ? instance.netEaseApi : null;
   }

   public static MusicApiManager getApiManager() {
      return instance != null ? instance.apiManager : null;
   }

   public static MusicApi getCurrentApi() {
      return instance != null && instance.apiManager != null ? instance.apiManager.getCurrentApi() : null;
   }
}
