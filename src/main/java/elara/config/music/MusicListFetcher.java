/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  com.google.gson.JsonArray
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  net.minecraft.client.Minecraft
 */
package elara.config.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import elara.config.NotificationHelper;
import elara.config.music.LoginManager;
import elara.config.music.LoginResult;
import elara.config.music.MusicApi;
import elara.config.music.MusicCache;
import elara.config.music.MusicPlayerManager;
import elara.config.music.NetEaseMusicApi;
import elara.config.music.Song;
import elara.config.music.SongInfo;
import elara.util.MinecraftUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MusicListFetcher {
   private static MusicListFetcher instance;
   private final ExecutorService threadPool;
   private final Logger logger;
   private final AtomicReference<String> cookie = new AtomicReference<String>("");
   private final AtomicReference<String> nickname = new AtomicReference<String>("");
   private final AtomicBoolean isLoggedIn = new AtomicBoolean(false);
   private final AtomicReference<String> qrKey = new AtomicReference<String>("");
   private final File sessionFile;

   private MusicListFetcher() {
      this.threadPool = Executors.newCachedThreadPool(r -> {
         Thread thread = new Thread(r, "MusicListFetcher-" + UUID.randomUUID().toString().substring(0, 8));
         thread.setDaemon(true);
         return thread;
      });
      this.logger = LoggerFactory.getLogger(MusicListFetcher.class);
      File configDir = new File(MinecraftUtil.getMinecraftDir(), "config/musicplayer");
      if (!configDir.exists()) {
         configDir.mkdirs();
      }
      this.sessionFile = new File(configDir, "session.json");
      this.loadSession();
   }

   public static synchronized MusicListFetcher getInstance() {
      if (instance == null) {
         instance = new MusicListFetcher();
      }
      return instance;
   }

   private void loadSession() {
      if (!this.sessionFile.exists()) {
         return;
      }
      try (InputStreamReader reader = new InputStreamReader((InputStream)new FileInputStream(this.sessionFile), StandardCharsets.UTF_8);){
         JsonObject json = new JsonParser().parse((Reader)reader).getAsJsonObject();
         if (json.has("cookie") && !json.get("cookie").isJsonNull()) {
            this.cookie.set(json.get("cookie").getAsString());
         }
         if (json.has("nickname") && !json.get("nickname").isJsonNull()) {
            this.nickname.set(json.get("nickname").getAsString());
         }
         if (json.has("isLoggedIn")) {
            this.isLoggedIn.set(json.get("isLoggedIn").getAsBoolean());
         }
         this.logger.info("Loaded session, nickname: {}, loggedIn: {}", (Object)this.nickname.get(), (Object)this.isLoggedIn.get());
      }
      catch (Exception e) {
         this.logger.warn("Failed to load session: {}", (Object)e.getMessage());
      }
   }

   private void saveSession() {
      try {
         JsonObject json = new JsonObject();
         json.addProperty("cookie", this.cookie.get());
         json.addProperty("nickname", this.nickname.get());
         json.addProperty("isLoggedIn", Boolean.valueOf(this.isLoggedIn.get()));
         json.addProperty("timestamp", (Number)System.currentTimeMillis());
         try (OutputStreamWriter writer = new OutputStreamWriter((OutputStream)new FileOutputStream(this.sessionFile), StandardCharsets.UTF_8);){
            writer.write(json.toString());
         }
      }
      catch (Exception e) {
         this.logger.warn("Failed to save session: {}", (Object)e.getMessage());
      }
   }

   private MusicApi getApi() {
      return MusicPlayerManager.getCurrentApi();
   }

   public CompletableFuture<String> loginByQR() {
      CompletableFuture<String> future = new CompletableFuture<String>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               this.logger.warn("Music API not available");
               future.complete(null);
               return;
            }
            String key = api.getQrKey();
            if (key == null) {
               this.logger.warn("Failed to get QR key");
               future.complete(null);
               return;
            }
            this.qrKey.set(key);
            byte[] qrImageBytes = api.getQrImage(key);
            if (qrImageBytes == null) {
               this.logger.warn("Failed to get QR image");
               future.complete(null);
               return;
            }
            File tempFile = File.createTempFile("qr_login_", ".png");
            tempFile.deleteOnExit();
            try (FileOutputStream fos = new FileOutputStream(tempFile);){
               fos.write(qrImageBytes);
            }
            future.complete(tempFile.getAbsolutePath());
         }
         catch (Exception e) {
            this.logger.warn("QR login failed: {}", (Object)e.getMessage());
            future.complete(null);
         }
      });
      return future;
   }

   public CompletableFuture<LoginResult> checkQRLogin() {
      CompletableFuture<LoginResult> future = new CompletableFuture<LoginResult>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(LoginResult.failed("API unavailable"));
               return;
            }
            String currentQrKey = this.qrKey.get();
            if (currentQrKey.isEmpty()) {
               future.complete(LoginResult.failed("QR code not generated"));
               return;
            }
            MusicApi.LoginResult result = api.checkQrLogin(currentQrKey);
            if (result.success) {
               this.isLoggedIn.set(true);
               this.nickname.set(result.nickname != null ? result.nickname : "");
               this.saveSession();
               this.scheduleNotify(() -> NotificationHelper.sendLoginSuccess(result.nickname));
               future.complete(LoginResult.success(result.nickname, result.avatarUrl, this.cookie.get()));
            } else {
               int code = this.parseCodeFromMessage(result.message);
               switch (code) {
                  case 800: {
                     future.complete(LoginResult.expired());
                     break;
                  }
                  case 801: {
                     future.complete(LoginResult.waiting());
                     break;
                  }
                  case 802: {
                     future.complete(LoginResult.scanned());
                     break;
                  }
                  default: {
                     future.complete(LoginResult.failed(result.message));
                  }
               }
            }
         }
         catch (Exception e) {
            this.logger.warn("QR check failed: {}", (Object)e.getMessage());
            future.complete(LoginResult.failed("Network error"));
         }
      });
      return future;
   }

   private int parseCodeFromMessage(String message) {
      if (message == null) {
         return -1;
      }
      if (message.contains("expired") || message.contains("expire")) {
         return 800;
      }
      if (message.contains("confirm")) {
         return 802;
      }
      if (message.contains("scan")) {
         return 801;
      }
      return -1;
   }

   public CompletableFuture<LoginResult> loginByPhone(String phone, String password) {
      CompletableFuture<LoginResult> future = new CompletableFuture<LoginResult>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(LoginResult.failed("API unavailable"));
               return;
            }
            MusicApi.LoginResult result = api.loginByPhone(phone, password);
            if (result.success) {
               this.isLoggedIn.set(true);
               this.nickname.set(result.nickname != null ? result.nickname : "");
               this.saveSession();
               this.scheduleNotify(() -> NotificationHelper.sendLoginSuccess(result.nickname));
               future.complete(LoginResult.success(result.nickname, result.avatarUrl, this.cookie.get()));
            } else {
               future.complete(LoginResult.failed(result.message));
            }
         }
         catch (Exception e) {
            this.logger.warn("Phone login failed: {}", (Object)e.getMessage());
            future.complete(LoginResult.failed("Network error"));
         }
      });
      return future;
   }

   public void logout() {
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api != null) {
               api.logout();
            }
         }
         catch (Exception e) {
            this.logger.warn("Logout failed: {}", (Object)e.getMessage());
         }
      });
      this.cookie.set("");
      this.nickname.set("");
      this.isLoggedIn.set(false);
      this.qrKey.set("");
      if (this.sessionFile.exists()) {
         this.sessionFile.delete();
      }
      this.scheduleNotify(NotificationHelper::sendLogout);
   }

   public boolean isLoggedIn() {
      return this.isLoggedIn.get();
   }

   public String getNickname() {
      return this.nickname.get();
   }

   private void scheduleNotify(Runnable action) {
      try {
         Minecraft.getMinecraft().addScheduledTask(action);
      }
      catch (Exception e) {
         this.logger.warn("Failed to schedule notification: {}", (Object)e.getMessage());
      }
   }

   public CompletableFuture<List<SongInfo>> fetchHotSongs(int limit) {
      CompletableFuture<List<SongInfo>> future = new CompletableFuture<List<SongInfo>>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(new ArrayList());
               return;
            }
            JsonObject response = api.getTopListDetail(3778678L);
            future.complete(this.parseTracksFromResponse(response, limit));
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch hot songs: {}", (Object)e.getMessage());
            future.complete(new ArrayList());
         }
      });
      return future;
   }

   public CompletableFuture<List<SongInfo>> fetchPersonalized(int limit) {
      CompletableFuture<List<SongInfo>> future = new CompletableFuture<List<SongInfo>>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(new ArrayList());
               return;
            }
            JsonObject response = api.getTopListDetail(3779282L);
            future.complete(this.parseTracksFromResponse(response, limit));
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch personalized: {}", (Object)e.getMessage());
            future.complete(new ArrayList());
         }
      });
      return future;
   }

   public CompletableFuture<List<SongInfo>> fetchPlaylist(String playlistId) {
      CompletableFuture<List<SongInfo>> future = new CompletableFuture<List<SongInfo>>();
      this.threadPool.submit(() -> {
         try {
            NetEaseMusicApi api = (NetEaseMusicApi)this.getApi();
            if (api == null) {
               future.complete(new ArrayList());
               return;
            }
            JsonObject response = api.getPlaylistDetail(playlistId);
            future.complete(this.parseTracksFromResponse(response, Integer.MAX_VALUE));
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch playlist {}: {}", (Object)playlistId, (Object)e.getMessage());
            future.complete(new ArrayList());
         }
      });
      return future;
   }

   public CompletableFuture<List<MusicApi.PlaylistInfo>> fetchUserPlaylists(String uid) {
      CompletableFuture<List<MusicApi.PlaylistInfo>> future = new CompletableFuture<List<MusicApi.PlaylistInfo>>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(new ArrayList());
               return;
            }
            MusicApi.PlaylistResult result = api.getUserPlaylists(uid);
            if (result.success && result.playlists != null) {
               future.complete(Arrays.asList(result.playlists));
            } else {
               future.complete(new ArrayList());
            }
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch user playlists for {}: {}", (Object)uid, (Object)e.getMessage());
            future.complete(new ArrayList());
         }
      });
      return future;
   }

   public CompletableFuture<MusicApi.UserDetailResult> fetchUserDetail(String uid) {
      CompletableFuture<MusicApi.UserDetailResult> future = new CompletableFuture<MusicApi.UserDetailResult>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(new MusicApi.UserDetailResult(false, "API unavailable"));
               return;
            }
            future.complete(api.getUserDetail(uid));
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch user detail for {}: {}", (Object)uid, (Object)e.getMessage());
            future.complete(new MusicApi.UserDetailResult(false, "Network error"));
         }
      });
      return future;
   }

   public String getUserId() {
      LoginManager lm = MusicPlayerManager.getLoginManager();
      if (lm != null) {
         return lm.getUserId();
      }
      return "";
   }

   public CompletableFuture<String> fetchSongUrl(String songId) {
      CompletableFuture<String> future = new CompletableFuture<String>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(null);
               return;
            }
            String url = api.getPlayUrl(songId);
            future.complete(url);
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch song url for {}: {}", (Object)songId, (Object)e.getMessage());
            future.complete(null);
         }
      });
      return future;
   }

   public CompletableFuture<String> fetchLyrics(String songId) {
      CompletableFuture<String> future = new CompletableFuture<String>();
      this.threadPool.submit(() -> {
         try {
            String cached = MusicCache.getLyrics(songId);
            if (cached != null) {
               future.complete(cached);
               return;
            }
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(null);
               return;
            }
            String lyrics = api.getLyrics(songId);
            if (lyrics != null && !lyrics.isEmpty()) {
               MusicCache.saveLyrics(songId, lyrics);
            }
            future.complete(lyrics);
         }
         catch (Exception e) {
            this.logger.warn("Failed to fetch lyrics for {}: {}", (Object)songId, (Object)e.getMessage());
            future.complete(null);
         }
      });
      return future;
   }

   public CompletableFuture<List<SongInfo>> search(String keyword, int limit) {
      CompletableFuture<List<SongInfo>> future = new CompletableFuture<List<SongInfo>>();
      this.threadPool.submit(() -> {
         try {
            MusicApi api = this.getApi();
            if (api == null) {
               future.complete(new ArrayList());
               return;
            }
            MusicApi.SearchResult result = api.search(keyword, limit);
            ArrayList<SongInfo> songs = new ArrayList<SongInfo>();
            if (result.success && result.songs != null) {
               for (Song song : result.songs) {
                  songs.add(new SongInfo(song.getSongId(), song.getTitle(), song.getArtist(), song.getAlbum(), song.getCoverUrl(), song.getDuration()));
               }
            }
            future.complete(songs);
         }
         catch (Exception e) {
            this.logger.warn("Failed to search for '{}': {}", (Object)keyword, (Object)e.getMessage());
            future.complete(new ArrayList());
         }
      });
      return future;
   }

   private List<SongInfo> parseTracksFromResponse(JsonObject response, int limit) {
      ArrayList<SongInfo> songs = new ArrayList<SongInfo>();
      if (response == null) {
         return songs;
      }
      try {
         if (!response.has("code") || response.get("code").getAsInt() != 200) {
            return songs;
         }
         JsonObject playlist = response.getAsJsonObject("playlist");
         if (playlist == null) {
            return songs;
         }
         JsonArray tracks = playlist.getAsJsonArray("tracks");
         if (tracks == null) {
            return songs;
         }
         int count = Math.min(tracks.size(), limit);
         for (int i = 0; i < count; ++i) {
            SongInfo song = this.parseTrackJson(tracks.get(i).getAsJsonObject());
            if (song == null) continue;
            songs.add(song);
         }
      }
      catch (Exception e) {
         this.logger.warn("Failed to parse tracks: {}", (Object)e.getMessage());
      }
      return songs;
   }

   private SongInfo parseTrackJson(JsonObject track) {
      try {
         String songId = track.has("id") ? String.valueOf(track.get("id").getAsLong()) : "";
         String name = track.has("name") ? track.get("name").getAsString() : "Unknown";
         name = cleanMusicText(name);
         StringBuilder artistBuilder = new StringBuilder();
         // 网易云搜索接口用 "artists"，歌单/热门接口用 "ar"
         String artistsKey = track.has("artists") ? "artists" : (track.has("ar") ? "ar" : null);
         if (artistsKey != null) {
            JsonArray artists = track.getAsJsonArray(artistsKey);
            for (int i = 0; i < artists.size(); ++i) {
               if (i > 0) {
                  artistBuilder.append(", ");
               }
               artistBuilder.append(cleanMusicText(artists.get(i).getAsJsonObject().get("name").getAsString()));
            }
         }
         String artist = artistBuilder.length() > 0 ? artistBuilder.toString() : "Unknown";
         String album = "";
         String coverUrl = "";
         // 网易云搜索接口用 "album"，歌单/热门接口用 "al"
         String albumKey = track.has("album") ? "album" : (track.has("al") ? "al" : null);
         if (albumKey != null) {
            JsonObject albumJson = track.getAsJsonObject(albumKey);
            album = albumJson.has("name") && !albumJson.get("name").isJsonNull()
                    ? cleanMusicText(albumJson.get("name").getAsString()) : "";
            if (albumJson.has("picUrl") && !albumJson.get("picUrl").isJsonNull()) {
               coverUrl = albumJson.get("picUrl").getAsString();
            }
         }
         int duration = track.has("duration") ? track.get("duration").getAsInt() / 1000
                 : (track.has("dt") ? track.get("dt").getAsInt() / 1000 : -1);
         return new SongInfo(songId, name, artist, album, coverUrl, duration);
      }
      catch (Exception e) {
         this.logger.debug("Failed to parse track: {}", (Object)e.getMessage());
         return null;
      }
   }

   /**
    * 清理歌曲/歌手/专辑文本中的常见问题：
    * - 控制字符、BOM、零宽字符、RTL 标记、孤立变音符号（去掉"末尾鬼字符"显示异常）
    * - 去除首尾空白
    * - 合并连续空白
    */
   private static String cleanMusicText(String s) {
      if (s == null) return "";
      char[] ca = s.toCharArray();
      StringBuilder sb = new StringBuilder(ca.length);
      boolean lastSpace = false;
      for (int i = 0; i < ca.length; i++) {
         char c = ca[i];
         int type = Character.getType(c);
         if (c == 0xFEFF || c == 0xFFFE || c == 0xFFFF
                 || c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\u2060'
                 || c == '\u202A' || c == '\u202B' || c == '\u202C' || c == '\u202D' || c == '\u202E'
                 || c == '\u00AD'
                 || type == Character.CONTROL
                 || type == Character.FORMAT
                 || type == Character.PRIVATE_USE
                 || type == Character.UNASSIGNED
                 || type == Character.SURROGATE) {
            continue;
         }
         if (Character.isWhitespace(c) || c == '\u3000') {
            if (!lastSpace && sb.length() > 0) {
               sb.append(' ');
               lastSpace = true;
            }
            continue;
         }
         lastSpace = false;
         sb.append(c);
      }
      // 去掉末尾的空格、标点残留和无意义字符
      while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
         sb.deleteCharAt(sb.length() - 1);
      }
      return sb.toString();
   }

   public void shutdown() {
      this.threadPool.shutdownNow();
      this.logger.info("MusicListFetcher shutdown complete");
   }
}

