package elara.config.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class NetEaseMusicApi implements MusicApi {
   private final LoginManager loginManager;

   public NetEaseMusicApi(LoginManager loginManager) {
      this.loginManager = loginManager;
   }

   private String getBaseUrl() {
      return MusicPlayerConfig.getApiUrl();
   }

   @Override
   public String getPlatformName() {
      return "NetEase Cloud Music";
   }

   @Override
   public String getPlatformIcon() {
      return "\ud83c\udfb5";
   }

   private String requestGet(String path, Map<String, String> params) throws Exception {
      StringBuilder urlBuilder = new StringBuilder(this.getBaseUrl());
      if (!this.getBaseUrl().endsWith("/") && !path.startsWith("/")) {
         urlBuilder.append("/");
      }

      urlBuilder.append(path);
      if (params != null && !params.isEmpty()) {
         urlBuilder.append("?");
         boolean first = true;

         for (Entry<String, String> entry : params.entrySet()) {
            if (!first) {
               urlBuilder.append("&");
            }

            urlBuilder.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            first = false;
         }
      }

      String fullUrl = urlBuilder.toString();
      URL url = new URL(fullUrl);
      HttpURLConnection conn = (HttpURLConnection)url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(15000);
      conn.setReadTimeout(30000);
      conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
      conn.setRequestProperty("Accept", "application/json");
      String cookie = this.loginManager.getCookie();
      if (cookie != null && !cookie.isEmpty()) {
         conn.setRequestProperty("Cookie", cookie);
      }

      int responseCode;
      InputStream is = (responseCode = conn.getResponseCode()) == 200 ? conn.getInputStream() : conn.getErrorStream();
      if (is == null) {
         throw new IOException("HTTP " + responseCode + " with no error stream");
      }

      Map<String, List<String>> headers = conn.getHeaderFields();
      if (headers != null && headers.containsKey("Set-Cookie")) {
         for (String setCookie : headers.get("Set-Cookie")) {
            this.loginManager.updateCookie(setCookie);
         }
      }

      BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      StringBuilder sb = new StringBuilder();

      String line;
      while ((line = reader.readLine()) != null) {
         sb.append(line);
      }

      reader.close();
      conn.disconnect();
      return sb.toString();
   }

   @Override
   public MusicApi.SearchResult search(String keyword, int limit) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("keywords", keyword);
         params.put("limit", String.valueOf(limit));
         params.put("offset", "0");
         params.put("type", "1");
         String response = this.requestGet("/search", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code != 200) {
            return new MusicApi.SearchResult(false, "Search failed: " + code);
         }

         JsonObject result = json.getAsJsonObject("result");
         JsonArray songsJson = result.getAsJsonArray("songs");
         Song[] songs = new Song[songsJson.size()];

         for (int i = 0; i < songsJson.size(); i++) {
            JsonObject songJson = songsJson.get(i).getAsJsonObject();
            String id = songJson.has("id") ? songJson.get("id").getAsString() : "";
            String name = songJson.has("name") ? songJson.get("name").getAsString() : "Unknown";
            StringBuilder artist = new StringBuilder();
            if (songJson.has("artists")) {
               JsonArray artists = songJson.getAsJsonArray("artists");

               for (int j = 0; j < artists.size(); j++) {
                  if (j > 0) {
                     artist.append(", ");
                  }

                  artist.append(artists.get(j).getAsJsonObject().get("name").getAsString());
               }
            }

            String album = "";
            String coverUrl = "";
            if (songJson.has("album")) {
               JsonObject albumObj = songJson.getAsJsonObject("album");
               album = albumObj.has("name") ? albumObj.get("name").getAsString() : "";
               if (albumObj.has("picUrl") && !albumObj.get("picUrl").isJsonNull()) {
                  coverUrl = albumObj.get("picUrl").getAsString();
               }
            }

            int duration = songJson.has("duration") ? songJson.get("duration").getAsInt() / 1000 : -1;
            songs[i] = new Song(id, null, name, artist.toString(), album, duration, coverUrl);
         }

         return new MusicApi.SearchResult(true, songs);
      } catch (Exception e) {
         return new MusicApi.SearchResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public String getPlayUrl(String songId) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("id", songId);
         params.put("br", String.valueOf(MusicPlayerConfig.getDefaultBitrate()));
         String response = this.requestGet("/song/url", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         JsonObject songData;
         JsonArray data;
         return code == 200
               && (data = json.getAsJsonArray("data")).size() > 0
               && (songData = data.get(0).getAsJsonObject()).has("url")
               && !songData.get("url").isJsonNull()
            ? songData.get("url").getAsString()
            : null;
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public MusicApi.LoginResult loginByPhone(String phone, String password) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("phone", phone);
         params.put("password", password);
         String response = this.requestGet("/login/cellphone", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code == 200) {
            JsonObject profile = json.getAsJsonObject("profile");
            String userId = profile.has("userId") ? profile.get("userId").getAsString() : "";
            String nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
            String avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
            this.loginManager.setLoggedIn(true);
            this.loginManager.setUserId(userId);
            this.loginManager.setNickname(nickname);
            this.loginManager.setAvatarUrl(avatarUrl);
            return new MusicApi.LoginResult(true, "Login successful", userId, nickname, avatarUrl);
         } else {
            return new MusicApi.LoginResult(false, "Login failed: " + code);
         }
      } catch (Exception e) {
         return new MusicApi.LoginResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public String getQrKey() {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/login/qr/key", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         return code == 200 ? json.getAsJsonObject("data").get("unikey").getAsString() : null;
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public byte[] getQrImage(String key) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("key", key);
         params.put("qrimg", "true");
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/login/qr/create", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         String qrImg;
         if (code == 200 && (qrImg = json.getAsJsonObject("data").get("qrimg").getAsString()).startsWith("data:image/png;base64,")) {
            qrImg = qrImg.substring(22);
            return Base64.getDecoder().decode(qrImg);
         } else {
            return null;
         }
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public MusicApi.LoginResult checkQrLogin(String key) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("key", key);
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/login/qr/check", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code == 800) {
            return new MusicApi.LoginResult(false, "QR code expired");
         }

         if (code == 801) {
            return new MusicApi.LoginResult(false, "Please scan the QR code");
         }

         if (code == 802) {
            return new MusicApi.LoginResult(false, "Please confirm login");
         }

         if (code != 803 && code != 200) {
            return new MusicApi.LoginResult(false, "Login failed: " + code);
         }

         String userId = "";
         String nickname = "";
         String avatarUrl = "";
         if (json.has("profile") && !json.get("profile").isJsonNull()) {
            JsonObject profile = json.getAsJsonObject("profile");
            userId = profile.has("userId") ? profile.get("userId").getAsString() : "";
            nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
            avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
         }

         if (nickname.isEmpty()) {
            try {
               HashMap<String, String> acctParams = new HashMap<>();
               acctParams.put("timestamp", String.valueOf(System.currentTimeMillis()));
               String acctResponse = this.requestGet("/user/account", acctParams);
               JsonObject acctJson = new JsonParser().parse(acctResponse).getAsJsonObject();
               if (acctJson.has("code") && acctJson.get("code").getAsInt() == 200) {
                  JsonObject account = acctJson.getAsJsonObject("account");
                  if (account != null) {
                     userId = account.has("id") ? account.get("id").getAsString() : "";
                  }

                  JsonObject profile;
                  if ((profile = acctJson.getAsJsonObject("profile")) != null) {
                     nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
                     avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
                  }
               }
            } catch (Exception var16) {
            }
         }

         this.loginManager.setLoggedIn(true);
         this.loginManager.setUserId(userId);
         this.loginManager.setNickname(nickname);
         this.loginManager.setAvatarUrl(avatarUrl);
         return new MusicApi.LoginResult(true, "Login successful", userId, nickname, avatarUrl);
      } catch (Exception e) {
         return new MusicApi.LoginResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public JsonObject getTopList() {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/toplist", params);
         return new JsonParser().parse(response).getAsJsonObject();
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public JsonObject getTopListDetail(long topListId) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("id", String.valueOf(topListId));
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/toplist/detail", params);
         return new JsonParser().parse(response).getAsJsonObject();
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public MusicApi.PlaylistResult getHotPlaylists(int limit) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("limit", String.valueOf(limit));
         params.put("offset", "0");
         params.put("order", "hot");
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/top/playlist", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code == 200) {
            JsonArray playlistsJson = json.getAsJsonArray("playlists");
            MusicApi.PlaylistInfo[] playlists = new MusicApi.PlaylistInfo[playlistsJson.size()];

            for (int i = 0; i < playlistsJson.size(); i++) {
               JsonObject playlistJson = playlistsJson.get(i).getAsJsonObject();
               String id = playlistJson.has("id") ? playlistJson.get("id").getAsString() : "";
               String name = playlistJson.has("name") ? playlistJson.get("name").getAsString() : "Unknown";
               String coverUrl = playlistJson.has("coverImgUrl") ? playlistJson.get("coverImgUrl").getAsString() : "";
               int trackCount = playlistJson.has("trackCount") ? playlistJson.get("trackCount").getAsInt() : 0;
               String creator = "";
               if (playlistJson.has("creator")) {
                  JsonObject creatorJson = playlistJson.getAsJsonObject("creator");
                  creator = creatorJson.has("nickname") ? creatorJson.get("nickname").getAsString() : "";
               }

               playlists[i] = new MusicApi.PlaylistInfo(id, name, coverUrl, trackCount, creator);
            }

            return new MusicApi.PlaylistResult(true, playlists);
         } else {
            return new MusicApi.PlaylistResult(false, "Failed: " + code);
         }
      } catch (Exception e) {
         return new MusicApi.PlaylistResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public MusicApi.PlaylistResult getUserPlaylists(String uid) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("uid", uid);
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/user/playlist", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code == 200) {
            JsonArray playlistsJson = json.getAsJsonArray("playlist");
            MusicApi.PlaylistInfo[] playlists = new MusicApi.PlaylistInfo[playlistsJson.size()];

            for (int i = 0; i < playlistsJson.size(); i++) {
               JsonObject playlistJson = playlistsJson.get(i).getAsJsonObject();
               String id = playlistJson.has("id") ? playlistJson.get("id").getAsString() : "";
               String name = playlistJson.has("name") ? playlistJson.get("name").getAsString() : "Unknown";
               String coverUrl = playlistJson.has("coverImgUrl") ? playlistJson.get("coverImgUrl").getAsString() : "";
               int trackCount = playlistJson.has("trackCount") ? playlistJson.get("trackCount").getAsInt() : 0;
               String creator = "";
               if (playlistJson.has("creator")) {
                  JsonObject creatorJson = playlistJson.getAsJsonObject("creator");
                  creator = creatorJson.has("nickname") ? creatorJson.get("nickname").getAsString() : "";
               }

               playlists[i] = new MusicApi.PlaylistInfo(id, name, coverUrl, trackCount, creator);
            }

            return new MusicApi.PlaylistResult(true, playlists);
         } else {
            return new MusicApi.PlaylistResult(false, "Failed: " + code);
         }
      } catch (Exception e) {
         return new MusicApi.PlaylistResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public MusicApi.UserDetailResult getUserDetail(String uid) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("uid", uid);
         params.put("timestamp", String.valueOf(System.currentTimeMillis()));
         String response = this.requestGet("/user/detail", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         int code = json.has("code") ? json.get("code").getAsInt() : -1;
         if (code == 200) {
            MusicApi.UserDetailResult result = new MusicApi.UserDetailResult(true, "Success");
            result.userId = uid;
            if (json.has("profile") && !json.get("profile").isJsonNull()) {
               JsonObject profile = json.getAsJsonObject("profile");
               result.nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
               result.avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
               result.backgroundUrl = profile.has("backgroundUrl") ? profile.get("backgroundUrl").getAsString() : "";
               result.signature = profile.has("signature") ? profile.get("signature").getAsString() : "";
               result.level = profile.has("level") ? profile.get("level").getAsInt() : 0;
               result.listenSongs = json.has("listenSongs") ? json.get("listenSongs").getAsInt() : 0;
               result.follows = profile.has("follows") ? profile.get("follows").getAsInt() : 0;
               result.followeds = profile.has("followeds") ? profile.get("followeds").getAsInt() : 0;
            }

            return result;
         } else {
            return new MusicApi.UserDetailResult(false, "Failed: " + code);
         }
      } catch (Exception e) {
         return new MusicApi.UserDetailResult(false, "Network error: " + e.getMessage());
      }
   }

   @Override
   public void logout() {
      try {
         this.requestGet("/logout", null);
         this.loginManager.setLoggedIn(false);
         this.loginManager.clearCookie();
      } catch (Exception e) {
         this.loginManager.setLoggedIn(false);
         this.loginManager.clearCookie();
      }
   }

   @Override
   public boolean testConnection() {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("keywords", "test");
         params.put("limit", "1");
         String response = this.requestGet("/search", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         return json.has("code") && json.get("code").getAsInt() == 200;
      } catch (Exception e) {
         return false;
      }
   }

   public JsonObject getPlaylistDetail(String playlistId) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("id", playlistId);
         params.put("n", "100");
         String response = this.requestGet("/playlist/detail", params);
         return new JsonParser().parse(response).getAsJsonObject();
      } catch (Exception e) {
         return null;
      }
   }

   @Override
   public String getLyrics(String songId) {
      try {
         HashMap<String, String> params = new HashMap<>();
         params.put("id", songId);
         String response = this.requestGet("/lyric", params);
         JsonObject json = new JsonParser().parse(response).getAsJsonObject();
         JsonObject lrc;
         return json.has("lrc") && (lrc = json.getAsJsonObject("lrc")).has("lyric") ? lrc.get("lyric").getAsString() : null;
      } catch (Exception e) {
         return null;
      }
   }
}
