package elara.config.music;

public class SongInfo {
   private final String songId;
   private final String name;
   private final String artist;
   private final String album;
   private final String coverUrl;
   private final int duration;

   public SongInfo(String songId, String name, String artist, String album, String coverUrl, int duration) {
      this.songId = songId != null ? songId : "";
      // 统一在构造阶段做文本清洗，避免后续 UI 渲染/截断时被控制字符或
      // 零宽字符干扰（之前出现过 decaying -> decayin、Fading Wind -> Fading Win）。
      String cleanedName = elara.config.gui.MusicLayout.fullClean(name);
      String cleanedArtist = elara.config.gui.MusicLayout.fullClean(artist);
      String cleanedAlbum = elara.config.gui.MusicLayout.fullClean(album);
      this.name = !cleanedName.isEmpty() ? cleanedName : "Unknown";
      this.artist = !cleanedArtist.isEmpty() ? cleanedArtist : "Unknown";
      this.album = cleanedAlbum;
      this.coverUrl = coverUrl != null ? coverUrl : "";
      this.duration = duration;
   }

   public String getSongId() {
      return this.songId;
   }

   public String getName() {
      return this.name;
   }

   public String getArtist() {
      return this.artist;
   }

   public String getAlbum() {
      return this.album;
   }

   public String getCoverUrl() {
      return this.coverUrl;
   }

   public int getDuration() {
      return this.duration;
   }

   public String getFormattedDuration() {
      if (this.duration < 0) {
         return "--:--";
      }

      int minutes = this.duration / 60;
      int seconds = this.duration % 60;
      return String.format("%d:%02d", minutes, seconds);
   }

   @Override
   public String toString() {
      return "SongInfo{songId='"
         + this.songId
         + '\''
         + ", name='"
         + this.name
         + '\''
         + ", artist='"
         + this.artist
         + '\''
         + ", album='"
         + this.album
         + '\''
         + ", coverUrl='"
         + this.coverUrl
         + '\''
         + ", duration="
         + this.duration
         + '}';
   }

   public Song toSong() {
      return new Song(this.songId, null, this.name, this.artist, this.album, this.duration, this.coverUrl);
   }
}
