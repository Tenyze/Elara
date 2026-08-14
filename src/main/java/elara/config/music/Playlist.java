/*
 * Decompiled with CFR 0.152.
 */
package elara.config.music;

import elara.config.music.Song;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class Playlist {
   public static final File MUSIC_DIR = new File("./config/Elara/music/");
   public static final File LOCAL_DIR = new File(MUSIC_DIR, "Local");
   private final List<Song> songs = new ArrayList<Song>();
   private int currentIndex = -1;

   public Playlist() {
      this.refresh();
   }

   public void refresh() {
      this.songs.clear();
      if (!MUSIC_DIR.exists()) {
         MUSIC_DIR.mkdirs();
      }
      if (!LOCAL_DIR.exists()) {
         LOCAL_DIR.mkdirs();
      }
      // Local music is organised one song per folder under Local/
      // (Local/Music1/<song>.mp3, Local/Music2/<song>.flac, ...). Flat audio
      // files placed directly in Local/ are also picked up.
      this.scanLocalDir(LOCAL_DIR);
      // Backward compatibility: still scan the legacy music/ root for audio
      // files that are not under Local/, so existing layouts keep working.
      // Skip Local/ (handled above) and .cache (online song caches).
      this.scanLegacyDir(MUSIC_DIR);
   }

   private void scanLocalDir(File dir) {
      File[] files = dir.listFiles();
      if (files == null) {
         return;
      }
      Arrays.sort(files, Playlist.NATURAL_ORDER);
      for (File f : files) {
         if (f.isDirectory()) {
            // One song per folder: take the first audio file inside.
            File audio = this.findFirstAudio(f);
            if (audio != null) {
               this.addLocalSong(audio);
            }
         } else if (this.isAudioFile(f)) {
            this.addLocalSong(f);
         }
      }
   }

   private void scanLegacyDir(File dir) {
      File[] files = dir.listFiles();
      if (files == null) {
         return;
      }
      for (File f : files) {
         if (f.isDirectory()) {
            String name = f.getName();
            if (name.equals(".cache") || name.equals("Local")) continue;
            this.scanLegacyDir(f);
            continue;
         }
         if (!this.isAudioFile(f)) continue;
         this.addLocalSong(f);
      }
   }

   private File findFirstAudio(File folder) {
      File[] files = folder.listFiles();
      if (files == null) {
         return null;
      }
      Arrays.sort(files, Playlist.NATURAL_ORDER);
      for (File f : files) {
         if (f.isFile() && this.isAudioFile(f)) {
            return f;
         }
      }
      return null;
   }

   private void addLocalSong(File f) {
      try {
         this.songs.add(new Song(f));
      } catch (Throwable throwable) {
         // unreadable / unsupported file — skip
      }
   }

   private boolean isAudioFile(File f) {
      String name = f.getName().toLowerCase();
      return name.endsWith(".mp3") || name.endsWith(".flac");
   }

   private static final Comparator<File> NATURAL_ORDER = Playlist::naturalCompare;

   private static int naturalCompare(File a, File b) {
      return Playlist.naturalCompare(a.getName(), b.getName());
   }

   private static int naturalCompare(String a, String b) {
      int i = 0;
      int j = 0;
      int la = a.length();
      int lb = b.length();
      while (i < la && j < lb) {
         char ca = a.charAt(i);
         char cb = b.charAt(j);
         if (Character.isDigit(ca) && Character.isDigit(cb)) {
            int si = i;
            int sj = j;
            while (i < la && Character.isDigit(a.charAt(i))) i++;
            while (j < lb && Character.isDigit(b.charAt(j))) j++;
            long na = Long.parseLong(a.substring(si, i));
            long nb = Long.parseLong(b.substring(sj, j));
            if (na != nb) {
               return Long.compare(na, nb);
            }
         } else {
            if (ca != cb) {
               return ca - cb;
            }
            i++;
            j++;
         }
      }
      return la - lb;
   }

   public Song addOnlineSong(String url, String title, String artist, String album, int duration) {
      Song song = new Song(url, title, artist, album, duration);
      this.songs.add(song);
      return song;
   }

   public void addOnlineSongs(List<Song> onlineSongs) {
      if (onlineSongs != null) {
         this.songs.addAll(onlineSongs);
      }
   }

   public void removeSong(Song song) {
      int idx = this.songs.indexOf(song);
      if (idx >= 0) {
         this.songs.remove(idx);
         if (idx < this.currentIndex) {
            --this.currentIndex;
         } else if (idx == this.currentIndex) {
            this.currentIndex = -1;
         }
      }
   }

   public List<Song> getSongs() {
      return this.songs;
   }

   public Song getSong(int index) {
      if (index < 0 || index >= this.songs.size()) {
         return null;
      }
      return this.songs.get(index);
   }

   public int size() {
      return this.songs.size();
   }

   public int getCurrentIndex() {
      return this.currentIndex;
   }

   public void setCurrentIndex(int index) {
      this.currentIndex = index;
   }

   public Song getCurrent() {
      return this.getSong(this.currentIndex);
   }

   public Song next() {
      if (this.songs.isEmpty()) {
         return null;
      }
      this.currentIndex = (this.currentIndex + 1) % this.songs.size();
      return this.getCurrent();
   }

   public Song previous() {
      if (this.songs.isEmpty()) {
         return null;
      }
      this.currentIndex = this.currentIndex <= 0 ? this.songs.size() - 1 : this.currentIndex - 1;
      return this.getCurrent();
   }

   public void clear() {
      this.songs.clear();
      this.currentIndex = -1;
   }
}

