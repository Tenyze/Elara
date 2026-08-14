package elara.config.music;

import com.google.gson.JsonObject;

public interface MusicApi {
   String getPlatformName();

   String getPlatformIcon();

   MusicApi.SearchResult search(String var1, int var2);

   String getPlayUrl(String var1);

   MusicApi.LoginResult loginByPhone(String var1, String var2);

   String getQrKey();

   byte[] getQrImage(String var1);

   MusicApi.LoginResult checkQrLogin(String var1);

   JsonObject getTopList();

   JsonObject getTopListDetail(long var1);

   MusicApi.PlaylistResult getHotPlaylists(int var1);

   MusicApi.PlaylistResult getUserPlaylists(String var1);

   MusicApi.UserDetailResult getUserDetail(String var1);

   void logout();

   boolean testConnection();

   default String getLyrics(String songId) {
      return null;
   }

   class LoginResult {
      public boolean success;
      public String message;
      public String userId;
      public String nickname;
      public String avatarUrl;

      public LoginResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public LoginResult(boolean success, String message, String userId, String nickname, String avatarUrl) {
         this.success = success;
         this.message = message;
         this.userId = userId;
         this.nickname = nickname;
         this.avatarUrl = avatarUrl;
      }
   }

   class PlaylistInfo {
      public String id;
      public String name;
      public String coverUrl;
      public int trackCount;
      public String creator;

      public PlaylistInfo(String id, String name, String coverUrl, int trackCount, String creator) {
         this.id = id;
         this.name = name;
         this.coverUrl = coverUrl;
         this.trackCount = trackCount;
         this.creator = creator;
      }
   }

   class PlaylistResult {
      public boolean success;
      public String message;
      public MusicApi.PlaylistInfo[] playlists;

      public PlaylistResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public PlaylistResult(boolean success, MusicApi.PlaylistInfo[] playlists) {
         this.success = success;
         this.playlists = playlists;
      }
   }

   class SearchResult {
      public boolean success;
      public String message;
      public Song[] songs;

      public SearchResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }

      public SearchResult(boolean success, Song[] songs) {
         this.success = success;
         this.songs = songs;
      }
   }

   class UserDetailResult {
      public boolean success;
      public String message;
      public String userId;
      public String nickname;
      public String avatarUrl;
      public String backgroundUrl;
      public String signature;
      public int level;
      public int listenSongs;
      public int playlistCount;
      public int follows;
      public int followeds;

      public UserDetailResult(boolean success, String message) {
         this.success = success;
         this.message = message;
      }
   }
}
