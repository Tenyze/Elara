package elara.config.music;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public class Song {
   private final File file;
   private String url;
   private final String songId;
   private final String title;
   private final String artist;
   private final String album;
   private final int duration;
   private String coverUrl = null;
   private byte[] coverImage = null;
   private String coverMime = "";
   private boolean coverLoaded = false;

   public Song(File file) {
      this.file = file;
      this.url = null;
      this.songId = null;
      String name = file.getName();
      // 去除常见音频扩展名
      int dotIdx = name.lastIndexOf('.');
      if (dotIdx > 0) {
         String ext = name.substring(dotIdx + 1).toLowerCase();
         if (ext.equals("mp3") || ext.equals("wav") || ext.equals("ogg") || ext.equals("m4a") || ext.equals("aac") || ext.equals("wma") || ext.equals("flac")) {
            name = name.substring(0, dotIdx);
         }
      }

      String id3Title = "";
      String id3Artist = "";
      String id3Album = "";
      byte[] id3Cover = null;
      String id3CoverMime = "";

      try {
         ID3Extractor.ID3Data id3 = ID3Extractor.extract(file);
         id3Title = id3.title != null ? elara.config.gui.MusicLayout.fullClean(id3.title) : "";
         id3Artist = id3.artist != null ? elara.config.gui.MusicLayout.fullClean(id3.artist) : "";
         id3Album = id3.album != null ? elara.config.gui.MusicLayout.fullClean(id3.album) : "";
         id3Cover = id3.coverImage;
         id3CoverMime = id3.coverMime != null ? id3.coverMime : "";
      } catch (Throwable var10) {
      }

      int dash;
      this.title = !id3Title.isEmpty() ? id3Title : ((dash = name.indexOf(" - ")) > 0 ? elara.config.gui.MusicLayout.fullClean(name.substring(dash + 3).trim()) : elara.config.gui.MusicLayout.fullClean(name));
      this.artist = !id3Artist.isEmpty() ? id3Artist : ((dash = name.indexOf(" - ")) > 0 ? elara.config.gui.MusicLayout.fullClean(name.substring(0, dash).trim()) : "Unknown");
      this.album = id3Album;
      this.coverImage = id3Cover;
      this.coverMime = id3CoverMime != null ? id3CoverMime : "";
      this.coverLoaded = this.coverImage != null && this.coverImage.length > 0;
      if (!this.coverLoaded && this.file != null) {
         this.applyFolderCoverFallback();
      }

      this.duration = -1;
   }

   private void applyFolderCoverFallback() {
      File folder = this.file.getParentFile();
      if (folder != null) {
         String[] candidates = new String[]{
            "cover.jpg", "cover.jpeg", "cover.png", "folder.jpg", "folder.png", "album.jpg", "album.png", "front.jpg", "front.png"
         };

         for (String name : candidates) {
            File img = new File(folder, name);
            if (img.isFile() && img.length() > 0L) {
               byte[] data = readBytes(img);
               if (data != null && data.length != 0) {
                  this.coverImage = data;
                  String lower = name.toLowerCase();
                  if (lower.endsWith(".png")) {
                     this.coverMime = "image/png";
                  } else {
                     this.coverMime = "image/jpeg";
                  }

                  this.coverLoaded = true;
                  return;
               }
            }
         }
      }
   }

   private static byte[] readBytes(File file) {
      try {
         FileInputStream fis = new FileInputStream(file);

         byte[] var5;
         try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            try {
               byte[] buf = new byte[8192];

               int n;
               while ((n = fis.read(buf)) != -1) {
                  bos.write(buf, 0, n);
               }

               var5 = bos.toByteArray();
            } catch (Throwable var8) {
               try {
                  bos.close();
               } catch (Throwable var7) {
                  var8.addSuppressed(var7);
               }

               throw var8;
            }

            bos.close();
         } catch (Throwable var9) {
            try {
               fis.close();
            } catch (Throwable var6) {
               var9.addSuppressed(var6);
            }

            throw var9;
         }

         fis.close();
         return var5;
      } catch (Exception e) {
         return null;
      }
   }

   public Song(String songId, String url, String title, String artist, String album, int duration) {
      this(songId, url, title, artist, album, duration, null);
   }

   public Song(String songId, String url, String title, String artist, String album, int duration, String coverUrl) {
      this.file = null;
      this.songId = songId;
      this.url = url;
      this.title = title != null ? title : "Unknown";
      this.artist = artist != null ? artist : "Unknown";
      this.album = album != null ? album : "";
      this.duration = duration;
      this.coverUrl = coverUrl;
   }

   public Song(String url, String title, String artist, String album, int duration) {
      this(null, url, title, artist, album, duration, null);
   }

   public boolean isLocal() {
      return this.file != null;
   }

   public boolean isOnline() {
      return this.url != null;
   }

   public File getFile() {
      return this.file;
   }

   public String getUrl() {
      return this.url;
   }

   public void setUrl(String url) {
      this.url = url;
   }

   public String getSongId() {
      return this.songId;
   }

   public String getTitle() {
      return this.title;
   }

   public String getArtist() {
      return this.artist;
   }

   public String getAlbum() {
      return this.album;
   }

   public int getDuration() {
      return this.duration;
   }

   public boolean hasCover() {
      return this.coverLoaded;
   }

   public String getCoverUrl() {
      return this.coverUrl;
   }

   public void setCoverUrl(String url) {
      this.coverUrl = url;
   }

   public byte[] getCoverImage() {
      return this.coverImage;
   }

   public String getCoverMime() {
      return this.coverMime;
   }

   public File getPlayableFile() {
      if (this.file != null) {
         return this.file;
      } else {
         return this.url != null ? MusicCache.getCachedFile(this.url) : null;
      }
   }

   public boolean isCached() {
      return this.file != null ? true : this.url != null && MusicCache.getCachedFile(this.url) != null;
   }

   public String getFormattedDuration() {
      return this.duration < 0 ? "--:--" : formatTime(this.duration);
   }

   public static String formatTime(int seconds) {
      int m = seconds / 60;
      int s = seconds % 60;
      return String.format("%d:%02d", m, s);
   }
}
