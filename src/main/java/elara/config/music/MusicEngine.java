package elara.config.music;

import elara.config.NotificationHelper;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import elara.util.NetworkMusicDownloader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.jflac.FLACDecoder;
import org.jflac.metadata.StreamInfo;

public class MusicEngine {
   private MusicEngine.RepeatMode repeatMode = MusicEngine.RepeatMode.OFF;
   private List<SongInfo> onlineQueue = null;
   private int onlineQueueIndex = -1;
   private final Playlist playlist;
   private SourceDataLine currentLine;
   private final float[] pcmBuffer = new float[1024];
   private int pcmWriteIndex = 0;
   private volatile boolean hasAudioData = false;
   private volatile boolean cancelled = false;
   private FlacPlayer flacPlayer;
   private volatile boolean playing = false;
   private volatile boolean paused = false;
   private final Object pauseLock = new Object();
   private boolean suppressPlayNotification = false;
   private long playStartTime;
   private int elapsedBeforePause;
   private int estimatedDuration;
   private int totalFrames = 0;
   private int samplesPerFrame = 1152;
   private int sampleRate = 44100;
   private float volume = 0.7F;
   private final float[] spectrum = new float[24];
   private final int bands = 24;
   private long lastSpectrumTime = 0L;
   private static final long SPECTRUM_INTERVAL_MS = 33L;
   private final float[] spectrumCache = new float[24];
   private final ExecutorService playbackExecutor = Executors.newCachedThreadPool(r -> {
      Thread t = new Thread(r, "Elara-MusicPlayer");
      t.setDaemon(true);
      return t;
   });
   private Future<?> playFuture;
   private Song currentSong;
   private volatile boolean downloading = false;
   private float downloadProgress = 0.0F;
   private Thread downloadThread = null;
   private String currentDownloadUrl = null;
   private final transient AtomicInteger playGeneration = new AtomicInteger(0);
   private List<MusicEngine.LyricLine> currentLyrics = null;
   private int currentLyricIndex = -1;
   private File currentFile;
   private String currentUrl;
   private transient long lastNoDataTime = 0L;
   private transient boolean noDataTriggered = false;
   private transient String lastProgressKey = null;
   private transient int lastProgressPct = -1;
   private transient long lastProgressPushMs = 0L;

   public MusicEngine(Playlist playlist) {
      this.playlist = playlist;
      MusicCache.init();
   }

   public void play(Song song) {
      if (song != null) {
         this.stop();
         this.playGeneration.incrementAndGet();
         this.currentSong = song;
         this.currentLyrics = null;
         this.currentLyricIndex = -1;
         this.onlineQueue = null;
         this.onlineQueueIndex = -1;
         if (song.isLocal()) {
            this.loadLocalLyrics(song.getFile());
         } else if (song.getSongId() != null && !song.getSongId().isEmpty()) {
            this.loadLyrics(song.getSongId());
         }

         int idx = this.playlist.getSongs().indexOf(song);
         if (idx >= 0) {
            this.playlist.setCurrentIndex(idx);
         }

         if (song.isLocal()) {
            this.playLocal(song);
         } else {
            boolean hasUrl = song.getUrl() != null && !song.getUrl().isEmpty();
            if (hasUrl) {
               File cached = MusicCache.getCachedFile(song.getUrl());
               if (cached != null) {
                  this.playLocal(cached, song);
                  return;
               }
            }

            this.startStreamingPlayback(song);
         }
      }
   }

   private void playLocal(Song song) {
      this.playLocal(song.getFile(), song);
   }

   private void playLocal(File file, Song song) {
      String realFormat = detectRealAudioFormat(file);
      boolean isFlac = "flac".equals(realFormat);
      if (isFlac) {
         this.playLocalFlac(file, song);
      } else {
         MusicEngine.Mp3Info info = this.analyzeMp3(file);
         this.estimatedDuration = info.duration;
         this.totalFrames = info.totalFrames;
         this.samplesPerFrame = info.samplesPerFrame;
         this.sampleRate = info.sampleRate;
         this.elapsedBeforePause = 0;
         this.startPlayback(file, 0);
         if (!this.suppressPlayNotification) {
            NotificationHelper.sendMusicPlay(this.getTitle(), this.getArtist());
         }

         this.suppressPlayNotification = false;
      }
   }

   private void playLocalFlac(File file, Song song) {
      try {
         MusicEngine.FlacInfo info = this.analyzeFlac(file);
         this.estimatedDuration = info.duration;
         this.sampleRate = info.sampleRate;
         this.elapsedBeforePause = 0;
         this.lastNoDataTime = 0L;
         this.noDataTriggered = false;
         final int currentGen = this.playGeneration.get();
         this.flacPlayer = new FlacPlayer(file);
         this.flacPlayer.setVolume(this.volume);
         this.flacPlayer.setPlaybackListener(new FlacPlayer.PlaybackListener() {
            @Override
            public void onStarted() {
               MusicEngine.this.playStartTime = System.currentTimeMillis();
            }

            @Override
            public void onFinished() {
               MusicEngine.this.playing = false;
               if (currentGen == MusicEngine.this.playGeneration.get()) {
                  if (!MusicEngine.this.paused) {
                     MusicEngine.this.next();
                  }
               }
            }
         });
         this.playing = true;
         this.paused = false;
         this.playStartTime = System.currentTimeMillis();
         this.flacPlayer.play();
         if (!this.suppressPlayNotification) {
            NotificationHelper.sendMusicPlay(this.getTitle(), this.getArtist());
         }

         this.suppressPlayNotification = false;
      } catch (Throwable e) {
         System.err.println("[Elara] FLAC play error: " + e.getMessage());
      }
   }

   private MusicEngine.FlacInfo analyzeFlac(File file) {
      MusicEngine.FlacInfo info = new MusicEngine.FlacInfo();

      try {
         FLACDecoder decoder = new FLACDecoder(new BufferedInputStream(new FileInputStream(file)));
         StreamInfo streamInfo = decoder.readStreamInfo();
         if (streamInfo != null) {
            info.sampleRate = streamInfo.getSampleRate();
            info.channels = streamInfo.getChannels();
            info.duration = (int)(streamInfo.getTotalSamples() / streamInfo.getSampleRate());
         }
      } catch (Throwable e) {
         System.err.println("[Elara] FLAC analyze error: " + e.getMessage());
      }

      return info;
   }

   private void startStreamingPlayback(Song song) {
      this.downloading = true;
      this.downloadProgress = 0.0F;
      this.estimatedDuration = song.getDuration() > 0 ? song.getDuration() : 300;
      this.totalFrames = this.estimatedDuration > 0 ? (int)(this.estimatedDuration / 0.026F) : 0;
      this.samplesPerFrame = 1152;
      this.sampleRate = 44100;
      this.elapsedBeforePause = 0;
      final int currentGen = this.playGeneration.get();
      new Thread(new Runnable() {
         @Override
         public void run() {
            String playUrl = song.getUrl();
            if ((playUrl == null || playUrl.isEmpty()) && song.getSongId() != null && !song.getSongId().isEmpty()) {
               try {
                  MusicApi api = MusicApiManager.getInstance() != null ? MusicApiManager.getInstance().getCurrentApi() : null;
                  if (api != null) {
                     playUrl = api.getPlayUrl(song.getSongId());
                     if (playUrl != null) {
                        song.setUrl(playUrl);
                     }
                  }
               } catch (Exception var3) {
               }
            }

            if (playUrl != null && !playUrl.isEmpty() && currentGen == MusicEngine.this.playGeneration.get()) {
               MusicEngine.this.startPlayback(playUrl, 0);
               MusicEngine.this.downloadThread = MusicCache.downloadAsync(playUrl, new MusicCache.DownloadListener() {
                  @Override
                  public void onProgress(float progress) {
                     MusicEngine.this.downloadProgress = progress;
                  }

                  @Override
                  public void onComplete(File cachedFile) {
                     MusicEngine.this.downloading = false;
                     MusicEngine.this.downloadProgress = 1.0F;
                  }

                  @Override
                  public void onError(String error) {
                     MusicEngine.this.downloading = false;
                     MusicEngine.this.downloadProgress = 0.0F;
                  }
               });
               if (!MusicEngine.this.suppressPlayNotification) {
                  NotificationHelper.sendMusicPlay(MusicEngine.this.getTitle(), MusicEngine.this.getArtist());
               }

               MusicEngine.this.suppressPlayNotification = false;
            } else {
               MusicEngine.this.downloading = false;
            }
         }
      }, "Music-StreamInit").start();
   }

   public void play(int index) {
      Song song = this.playlist.getSong(index);
      if (song != null) {
         this.play(song);
      }
   }

   public void togglePlay() {
      if (this.paused) {
         this.resume();
      } else if (this.playing) {
         this.pause();
      } else if (this.currentSong != null) {
         if (this.currentFile != null) {
            this.startPlayback(this.currentFile, 0);
         } else if (this.currentUrl != null) {
            this.startPlayback(this.currentUrl, 0);
         }

         NotificationHelper.sendMusicPlay(this.getTitle(), this.getArtist());
      } else if (this.playlist.size() > 0) {
         this.play(0);
      }
   }

   public void pause() {
      if (!this.paused && this.playing) {
         this.elapsedBeforePause = this.elapsedBeforePause + (int)((System.currentTimeMillis() - this.playStartTime) / 1000L);
         this.paused = true;
         if (this.currentLine != null) {
            try { this.currentLine.stop(); } catch (Throwable ignored) {}
         }

         if (this.flacPlayer != null) {
            this.flacPlayer.pause();
         }

         NotificationHelper.sendMusicPause(this.getTitle(), this.getArtist());
      }
   }

   public void resume() {
      if (this.paused) {
         this.paused = false;
         this.playStartTime = System.currentTimeMillis();
         if (this.currentLine != null) {
            try { this.currentLine.start(); } catch (Throwable ignored) {}
         }

         if (this.flacPlayer != null) {
            this.flacPlayer.resume();
         }

         synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
         }

         NotificationHelper.sendMusicPlay(this.getTitle(), this.getArtist());
      }
   }

   public void stop() {
      this.playGeneration.incrementAndGet();
      this.cancelled = true;
      this.playing = false;
      this.paused = false;
      this.elapsedBeforePause = 0;
      this.downloading = false;
      this.downloadProgress = 0.0F;
      this.currentLyrics = null;
      this.currentLyricIndex = -1;
      this.lastNoDataTime = 0L;
      this.noDataTriggered = false;
      if (this.downloadThread != null) {
         try {
            this.downloadThread.interrupt();
         } catch (Throwable var7) {
         }

         this.downloadThread = null;
      }

      this.cancelCurrentDownload();
      if (this.flacPlayer != null) {
         this.flacPlayer.stop();
         this.flacPlayer = null;
      }

      synchronized (this.pauseLock) {
         this.pauseLock.notifyAll();
      }

      if (this.currentLine != null) {
         try { this.currentLine.stop(); this.currentLine.close(); } catch (Throwable ignored) {}
         this.currentLine = null;
      }

      if (this.playFuture != null) {
         try {
            this.playFuture.cancel(true);
         } catch (Throwable var4) {
         }

         this.playFuture = null;
      }
   }

   public void playNetwork(String url) {
      if (url != null && !url.isEmpty()) {
         this.stop();
         final int gen = this.playGeneration.incrementAndGet();
         this.currentDownloadUrl = url;
         this.downloading = true;
         this.downloadProgress = 0.0F;
         NetworkMusicDownloader.getInstance().download(url, new NetworkMusicDownloader.ProgressListener() {
            @Override
            public void onProgress(int percent) {
               if (gen == MusicEngine.this.playGeneration.get()) {
                  MusicEngine.this.downloadProgress = percent / 100.0F;
                  MusicEngine.this.showActionBarProgress("Loading audio... " + percent + "%");
               }
            }

            @Override
            public void onComplete(File file) {
               if (gen == MusicEngine.this.playGeneration.get()) {
                  MusicEngine.this.downloading = false;
                  MusicEngine.this.downloadProgress = 1.0F;
                  MusicEngine.this.currentDownloadUrl = null;
                  MusicEngine.this.clearActionBar();
                  MusicEngine.this.suppressPlayNotification = true;
                  MusicEngine.this.playLocal(file, MusicEngine.this.currentSong);
                  NotificationHelper.sendMusicPlay("Network Audio", "");
               }
            }

            @Override
            public void onError(String error) {
               if (gen == MusicEngine.this.playGeneration.get()) {
                  MusicEngine.this.downloading = false;
                  MusicEngine.this.downloadProgress = 0.0F;
                  MusicEngine.this.currentDownloadUrl = null;
                  MusicEngine.this.clearActionBar();
                  NotificationHelper.sendMusicError(error);
               }
            }
         });
      } else {
         NotificationHelper.sendMusicError("URL is empty");
      }
   }

   public void playNetwork(SongInfo song) {
      if (song != null && song.getSongId() != null && !song.getSongId().isEmpty()) {
         this.stop();
         int gen = this.playGeneration.incrementAndGet();
         this.currentSong = song.toSong();
         this.downloading = true;
         this.downloadProgress = 0.0F;
         this.showActionBarProgress("Fetching song info...");
         String coverUrl = song.getCoverUrl();
         if (coverUrl != null && !coverUrl.isEmpty()) {
            CoverManager.preloadCover(coverUrl, null);
         }

         this.loadLyrics(song.getSongId());
         MusicListFetcher.getInstance().fetchSongUrl(song.getSongId()).thenAccept(playUrl -> {
            if (gen == this.playGeneration.get()) {
               if (playUrl != null && !playUrl.isEmpty()) {
                  this.currentDownloadUrl = playUrl;
                  this.currentSong.setUrl(playUrl);
                  if (coverUrl != null && !coverUrl.isEmpty()) {
                     CoverManager.preloadCover(coverUrl, playUrl);
                  }

                  NetworkMusicDownloader.getInstance().download(playUrl, new NetworkMusicDownloader.ProgressListener() {
                     @Override
                     public void onProgress(int percent) {
                        if (gen == MusicEngine.this.playGeneration.get()) {
                           MusicEngine.this.downloadProgress = percent / 100.0F;
                           MusicEngine.this.showActionBarProgress("Loading " + song.getName() + "... " + percent + "%");
                        }
                     }

                     @Override
                     public void onComplete(File file) {
                        if (gen == MusicEngine.this.playGeneration.get()) {
                           MusicEngine.this.downloading = false;
                           MusicEngine.this.downloadProgress = 1.0F;
                           MusicEngine.this.currentDownloadUrl = null;
                           MusicEngine.this.clearActionBar();
                           MusicEngine.this.suppressPlayNotification = true;
                           MusicEngine.this.playLocal(file, song.toSong());
                           if (MusicEngine.this.estimatedDuration <= 0 && song.getDuration() > 0) {
                              MusicEngine.this.estimatedDuration = song.getDuration();
                           }
                           NotificationHelper.sendMusicPlay(song.getName(), song.getArtist());
                        }
                     }

                     @Override
                     public void onError(String error) {
                        if (gen == MusicEngine.this.playGeneration.get()) {
                           MusicEngine.this.downloading = false;
                           MusicEngine.this.downloadProgress = 0.0F;
                           MusicEngine.this.currentDownloadUrl = null;
                           MusicEngine.this.clearActionBar();
                           NotificationHelper.sendMusicError(error);
                        }
                     }
                  });
               } else {
                  this.downloading = false;
                  this.downloadProgress = 0.0F;
                  this.clearActionBar();
                  NotificationHelper.sendMusicError("Cannot get play URL");
               }
            }
         }).exceptionally(e -> {
            if (gen != this.playGeneration.get()) {
               return null;
            }

            this.downloading = false;
            this.downloadProgress = 0.0F;
            this.clearActionBar();
            NotificationHelper.sendMusicError(e.getMessage());
            return null;
         });
      } else {
         NotificationHelper.sendMusicError("Invalid song info");
      }
   }

   public void cancelCurrentDownload() {
      if (this.currentDownloadUrl != null) {
         NetworkMusicDownloader.getInstance().cancelDownload(this.currentDownloadUrl);
         this.downloading = false;
         this.downloadProgress = 0.0F;
         this.currentDownloadUrl = null;
         this.clearActionBar();
      }
   }

   private void showActionBarProgress(String message) {
      // 禁止 sendChatMessage 发包（/actionbar 不是原版指令，会被服务器当作聊天刷屏踢出）
      // 改为本地 ChatComponent，同时做内容和百分比去重 + 节流 1s，避免刷屏
      try {
         Minecraft mc = Minecraft.getMinecraft();
         if (mc.thePlayer == null) return;
         long now = System.currentTimeMillis();
         String key = message != null ? message : "";
         // 提取百分比（形如 "Loading... 33%"），按百分比去重
         int pct = -1;
         java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)%").matcher(key);
         if (m.find()) {
            try { pct = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
         }
         boolean pctChanged = pct >= 0 && pct != this.lastProgressPct;
         boolean keyChanged = !key.equals(this.lastProgressKey);
         boolean timeOk = now - this.lastProgressPushMs >= 1000L;
         if (!keyChanged && !pctChanged) return;
         if (!timeOk && pct >= 0 && Math.abs(pct - this.lastProgressPct) < 10) return;
         this.lastProgressKey = key;
         this.lastProgressPct = pct;
         this.lastProgressPushMs = now;
         mc.thePlayer.addChatComponentMessage(new ChatComponentText(message));
      } catch (Throwable ignored) {
      }
   }

   private void clearActionBar() {
      // 清除时重置进度状态即可，不发包
      this.lastProgressKey = null;
      this.lastProgressPct = -1;
      this.lastProgressPushMs = 0L;
   }

   public void next() {
      if (this.repeatMode == MusicEngine.RepeatMode.ONE && this.currentSong != null) {
         this.suppressPlayNotification = true;
         if (this.onlineQueue != null && this.onlineQueueIndex >= 0 && this.onlineQueueIndex < this.onlineQueue.size()) {
            this.playNetwork(this.onlineQueue.get(this.onlineQueueIndex));
         } else {
            this.play(this.currentSong);
         }
      } else if (this.onlineQueue != null && !this.onlineQueue.isEmpty()) {
         if (this.repeatMode == MusicEngine.RepeatMode.SHUFFLE) {
            this.onlineQueueIndex = (int)(Math.random() * this.onlineQueue.size());
         } else {
            this.onlineQueueIndex++;
            if (this.onlineQueueIndex >= this.onlineQueue.size()) {
               if (this.repeatMode != MusicEngine.RepeatMode.ALL) {
                  this.onlineQueue = null;
                  this.onlineQueueIndex = -1;
                  this.stop();
                  return;
               }

               this.onlineQueueIndex = 0;
            }
         }

         this.suppressPlayNotification = true;
         this.playNetwork(this.onlineQueue.get(this.onlineQueueIndex));
         NotificationHelper.sendMusicNext(this.getTitle(), this.getArtist());
      } else {
         Song song = this.playlist.next();
         if (song != null) {
            if (this.repeatMode == MusicEngine.RepeatMode.SHUFFLE && this.playlist.size() > 1) {
               int randIdx;
               while ((randIdx = (int)(Math.random() * this.playlist.size())) == this.playlist.getCurrentIndex()) {
               }

               song = this.playlist.getSong(randIdx);
               this.playlist.setCurrentIndex(randIdx);
            }

            this.suppressPlayNotification = true;
            this.play(song);
            NotificationHelper.sendMusicNext(this.getTitle(), this.getArtist());
         }
      }
   }

   public void previous() {
      if (this.onlineQueue != null && !this.onlineQueue.isEmpty()) {
         if (this.repeatMode == MusicEngine.RepeatMode.SHUFFLE) {
            this.onlineQueueIndex = (int)(Math.random() * this.onlineQueue.size());
         } else {
            this.onlineQueueIndex--;
            if (this.onlineQueueIndex < 0) {
               this.onlineQueueIndex = this.repeatMode == MusicEngine.RepeatMode.ALL ? this.onlineQueue.size() - 1 : 0;
            }
         }

         this.suppressPlayNotification = true;
         this.playNetwork(this.onlineQueue.get(this.onlineQueueIndex));
      } else {
         Song song = this.playlist.previous();
         if (song != null) {
            this.play(song);
         }
      }
   }

   public void seek(float progress) {
      progress = Math.max(0.0F, Math.min(1.0F, progress));
      if (this.flacPlayer != null) {
         int duration = this.estimatedDuration;
         if (duration > 0) {
            int targetSeconds = (int)(duration * progress);
            this.elapsedBeforePause = targetSeconds;
            this.playStartTime = System.currentTimeMillis();
            this.playing = true;
            this.paused = false;
            this.flacPlayer.seekTo(targetSeconds);
         }
      } else if ((this.currentFile != null || this.currentUrl != null) && this.totalFrames > 0 && this.sampleRate > 0) {
         int targetFrame = (int)(this.totalFrames * progress);
         int targetSeconds = (int)((long)targetFrame * this.samplesPerFrame / this.sampleRate);
         List<MusicEngine.LyricLine> savedLyrics = this.currentLyrics;
         this.stop();
         this.currentLyrics = savedLyrics;
         this.currentLyricIndex = -1;
         this.elapsedBeforePause = targetSeconds;
         if (this.currentFile != null) {
            this.startPlayback(this.currentFile, targetFrame);
         } else if (this.currentUrl != null) {
            this.startPlayback(this.currentUrl, targetFrame);
         }
      }
   }

   private void startPlayback(File file, int startFrame) {
      if (file == null) return;
      this.currentFile = file;
      this.currentUrl = null;
      this.cancelled = false;
      final int gen = this.playGeneration.get();
      this.playFuture = this.playbackExecutor.submit(() -> {
         FileInputStream fis = null;
         SourceDataLine line = null;
         Bitstream bs = null;
         try {
            fis = new FileInputStream(file);
            BufferedInputStream bis = new BufferedInputStream(fis);
            bs = new Bitstream(bis);
            Decoder decoder = new Decoder();

            Header firstHeader = bs.readFrame();
            if (firstHeader == null) return;

            int freq = firstHeader.frequency();
            int channels = (firstHeader.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;

            // Seek by decoding and discarding frames
            int skipped = 0;
            while (skipped < startFrame && !this.cancelled) {
               Header h = bs.readFrame();
               if (h == null) break;
               decoder.decodeFrame(h, bs);
               bs.closeFrame();
               skipped++;
            }

            if (this.cancelled || gen != this.playGeneration.get()) return;

            // Open SourceDataLine: 16-bit signed PCM, little-endian first, big-endian fallback
            AudioFormat format = new AudioFormat((float) freq, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
               format = new AudioFormat((float) freq, 16, channels, true, true);
               info = new DataLine.Info(SourceDataLine.class, format);
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, Math.max(line.getBufferSize(), 8192));
            applyGain(line, this.volume);
            this.currentLine = line;
            this.playing = true;
            this.paused = false;
            this.playStartTime = System.currentTimeMillis();
            line.start();

            // Main decode loop
            while (!this.cancelled && gen == this.playGeneration.get()) {
               // Handle pause
               while (this.paused && !this.cancelled && gen == this.playGeneration.get()) {
                  synchronized (this.pauseLock) {
                     try { this.pauseLock.wait(50); } catch (InterruptedException e) { break; }
                  }
               }
               if (this.cancelled || gen != this.playGeneration.get()) break;

               Header h = bs.readFrame();
               if (h == null) break;
               SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bs);
               if (sb != null) {
                  short[] samples = sb.getBuffer();
                  int len = sb.getBufferLength();

                  // Pack shorts to little-endian bytes
                  byte[] bytes = new byte[len * 2];
                  for (int i = 0; i < len; i++) {
                     bytes[i * 2] = (byte) (samples[i] & 0xFF);
                     bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                  }

                  // Feed PCM ring buffer for spectrum
                  synchronized (this.pcmBuffer) {
                     for (int i = 0; i < len; i++) {
                        this.pcmBuffer[this.pcmWriteIndex] = samples[i] / 32768.0F;
                        this.pcmWriteIndex = (this.pcmWriteIndex + 1) & (this.pcmBuffer.length - 1);
                     }
                     this.hasAudioData = true;
                  }

                  line.write(bytes, 0, bytes.length);
               }
               bs.closeFrame();
            }
            line.drain();

            // Auto-advance if finished naturally
            if (!this.cancelled && gen == this.playGeneration.get() && !this.paused) {
               this.next();
            }
         } catch (Throwable e) {
            System.err.println("[Elara] MP3 playback error: " + e.getMessage());
         } finally {
            this.playing = false;
            if (line != null) { try { line.close(); } catch (Throwable ignored) {} }
            if (this.currentLine == line) this.currentLine = null;
            try { if (bs != null) bs.close(); } catch (Throwable ignored) {}
            try { if (fis != null) fis.close(); } catch (Throwable ignored) {}
         }
      });
   }

   private void startPlayback(String url, int startFrame) {
      if (url == null || url.isEmpty()) return;
      this.currentUrl = url;
      this.currentFile = null;
      this.cancelled = false;
      final int gen = this.playGeneration.get();
      this.playFuture = this.playbackExecutor.submit(() -> {
         InputStream is = null;
         SourceDataLine line = null;
         Bitstream bs = null;
         try {
            URL urlObj = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            if (startFrame > 0 && this.totalFrames > 0 && this.samplesPerFrame > 0) {
               int bytesPerFrame = this.sampleRate == 44100 ? 417 : 252;
               long startByte = (long) startFrame * bytesPerFrame;
               conn.setRequestProperty("Range", "bytes=" + startByte + "-");
            }
            is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is, 32768);
            bs = new Bitstream(bis);
            Decoder decoder = new Decoder();

            Header firstHeader = bs.readFrame();
            if (firstHeader == null) return;

            int freq = firstHeader.frequency();
            int channels = (firstHeader.mode() == Header.SINGLE_CHANNEL) ? 1 : 2;

            AudioFormat format = new AudioFormat((float) freq, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
               format = new AudioFormat((float) freq, 16, channels, true, true);
               info = new DataLine.Info(SourceDataLine.class, format);
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, Math.max(line.getBufferSize(), 8192));
            applyGain(line, this.volume);
            this.currentLine = line;
            this.playing = true;
            this.paused = false;
            this.playStartTime = System.currentTimeMillis();
            this.elapsedBeforePause = startFrame > 0 ? (int)((long)startFrame * this.samplesPerFrame / this.sampleRate) : 0;
            line.start();

            while (!this.cancelled && gen == this.playGeneration.get()) {
               while (this.paused && !this.cancelled && gen == this.playGeneration.get()) {
                  synchronized (this.pauseLock) {
                     try { this.pauseLock.wait(50); } catch (InterruptedException e) { break; }
                  }
               }
               if (this.cancelled || gen != this.playGeneration.get()) break;

               Header h = bs.readFrame();
               if (h == null) break;
               SampleBuffer sb = (SampleBuffer) decoder.decodeFrame(h, bs);
               if (sb != null) {
                  short[] samples = sb.getBuffer();
                  int len = sb.getBufferLength();
                  byte[] bytes = new byte[len * 2];
                  for (int i = 0; i < len; i++) {
                     bytes[i * 2] = (byte) (samples[i] & 0xFF);
                     bytes[i * 2 + 1] = (byte) ((samples[i] >> 8) & 0xFF);
                  }
                  synchronized (this.pcmBuffer) {
                     for (int i = 0; i < len; i++) {
                        this.pcmBuffer[this.pcmWriteIndex] = samples[i] / 32768.0F;
                        this.pcmWriteIndex = (this.pcmWriteIndex + 1) & (this.pcmBuffer.length - 1);
                     }
                     this.hasAudioData = true;
                  }
                  line.write(bytes, 0, bytes.length);
               }
               bs.closeFrame();
            }
            line.drain();
            if (!this.cancelled && gen == this.playGeneration.get() && !this.paused) {
               this.next();
            }
         } catch (Throwable e) {
            System.err.println("[Elara] MP3 stream error: " + e.getMessage());
         } finally {
            this.playing = false;
            this.currentUrl = null;
            if (line != null) { try { line.close(); } catch (Throwable ignored) {} }
            if (this.currentLine == line) this.currentLine = null;
            try { if (bs != null) bs.close(); } catch (Throwable ignored) {}
            try { if (is != null) is.close(); } catch (Throwable ignored) {}
         }
      });
   }

   /**
    * 通过魔术字节检测音频文件的真实格式。
    * 来自 CloudMusic 项目的 detectRealAudioFormat 方法。
    *
    * @return "flac", "mp3", 或 null（无法识别）
    */
   private static String detectRealAudioFormat(File file) {
      if (file == null || file.length() < 4) return null;
      FileInputStream fis = null;
      try {
         fis = new FileInputStream(file);
         byte[] header = new byte[4];
         int read = fis.read(header);
         if (read < 4) return null;
         if (header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C') {
            return "flac";
         }
         if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            return "mp3";
         }
         if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) {
            return "mp3";
         }
         return null;
      } catch (Exception e) {
         return null;
      } finally {
         if (fis != null) try { fis.close(); } catch (Exception e) {}
      }
   }

   private MusicEngine.Mp3Info analyzeMp3(File file) {
      MusicEngine.Mp3Info info = new MusicEngine.Mp3Info();

      try {
         long fileSize = file.length();
         if (fileSize <= 0L) {
            return info;
         }

         byte[] buffer = new byte[4096];
         FileInputStream fis = new FileInputStream(file);

         int read;
         try {
            read = fis.read(buffer);
         } catch (Throwable var40) {
            try {
               fis.close();
            } catch (Throwable var37) {
               var40.addSuppressed(var37);
            }

            throw var40;
         }

         fis.close();
         if (read < 4) {
            return info;
         }

         int pos = 0;
         if (buffer[0] == 73 && buffer[1] == 68 && buffer[2] == 51) {
            if (read < 10) {
               return info;
            }

            int tagSize = (buffer[6] & 127) << 21 | (buffer[7] & 127) << 14 | (buffer[8] & 127) << 7 | buffer[9] & 127;
            pos = 10 + tagSize;
            if (pos + 4 > read) {
               RandomAccessFile raf = new RandomAccessFile(file, "r");

               try {
                  raf.seek(pos);
                  read = raf.read(buffer);
               } catch (Throwable var39) {
                  try {
                     raf.close();
                  } catch (Throwable var36) {
                     var39.addSuppressed(var36);
                  }

                  throw var39;
               }

               raf.close();
               pos = 0;
               if (read < 4) {
                  return info;
               }
            }
         }

         int frameHeaderPos = -1;

         for (int i = pos; i < read - 3; i++) {
            if ((buffer[i] & 255) == 255 && (buffer[i + 1] & 224) == 224) {
               int versionBits = buffer[i + 1] >> 3 & 3;
               int layerBits = buffer[i + 1] >> 1 & 3;
               int bitrateIndex = buffer[i + 2] >> 4 & 15;
               int sampleRateIndex = buffer[i + 2] >> 2 & 3;
               if (versionBits != 1 && layerBits == 1 && bitrateIndex != 0 && bitrateIndex != 15 && sampleRateIndex != 3) {
                  frameHeaderPos = i;
                  break;
               }
            }
         }

         if (frameHeaderPos < 0) {
            return info;
         }

         byte b1 = buffer[frameHeaderPos + 1];
         byte b2 = buffer[frameHeaderPos + 2];
         int versionBits = b1 >> 3 & 3;
         int bitrateIndex = b2 >> 4 & 15;
         int sampleRateIndex = b2 >> 2 & 3;
         int padding = b2 >> 1 & 1;
         int mpegVersion;
         if (versionBits == 3) {
            mpegVersion = 1;
         } else if (versionBits == 2) {
            mpegVersion = 2;
         } else {
            if (versionBits != 0) {
               return info;
            }

            mpegVersion = 3;
         }

         int[][] bitrateTable = new int[][]{
            {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1},
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1},
            {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1}
         };
         int bitrate = bitrateTable[mpegVersion - 1][bitrateIndex];
         if (bitrate <= 0) {
            return info;
         }

         int[][] sampleRateTable = new int[][]{{44100, 48000, 32000, -1}, {22050, 24000, 16000, -1}, {11025, 12000, 8000, -1}};
         int sr = sampleRateTable[mpegVersion - 1][sampleRateIndex];
         if (sr <= 0) {
            return info;
         }

         int spf = mpegVersion == 1 ? 1152 : 576;
         int coeff = mpegVersion == 1 ? 144 : 72;
         int frameSize = coeff * bitrate * 1000 / sr + padding;
         if (frameSize <= 0) {
            return info;
         }

         int channelMode = buffer[frameHeaderPos + 3] >> 6 & 3;
         boolean mono = channelMode == 3;
         int sideInfoSize;
         if (mpegVersion == 1) {
            sideInfoSize = mono ? 17 : 32;
         } else {
            sideInfoSize = mono ? 9 : 17;
         }

         int xingOffset = frameHeaderPos + 4 + sideInfoSize;
         boolean xingFound = false;
         if (xingOffset + 12 <= read) {
            if (buffer[xingOffset] == 88 && buffer[xingOffset + 1] == 105 && buffer[xingOffset + 2] == 110 && buffer[xingOffset + 3] == 103) {
               xingFound = true;
            } else if (buffer[xingOffset] == 73 && buffer[xingOffset + 1] == 110 && buffer[xingOffset + 2] == 102 && buffer[xingOffset + 3] == 111) {
               xingFound = true;
            }
         }

         if (xingFound) {
            int flags = (buffer[xingOffset + 4] & 255) << 24
               | (buffer[xingOffset + 5] & 255) << 16
               | (buffer[xingOffset + 6] & 255) << 8
               | buffer[xingOffset + 7] & 255;
            if ((flags & 1) != 0) {
               long xingFrames = (buffer[xingOffset + 8] & 255L) << 24
                  | (buffer[xingOffset + 9] & 255L) << 16
                  | (buffer[xingOffset + 10] & 255L) << 8
                  | buffer[xingOffset + 11] & 255L;
               if (xingFrames > 0L) {
                  double duration = (double)xingFrames * spf / sr;
                  info.totalFrames = (int)xingFrames;
                  info.duration = (int)duration;
                  info.samplesPerFrame = spf;
                  info.sampleRate = sr;
                  return info;
               }
            }
         }

         long audioStart = frameHeaderPos;
         long audioSize = fileSize - audioStart;
         if (fileSize >= 128L) {
            try {
               RandomAccessFile raf2 = new RandomAccessFile(file, "r");

               try {
                  raf2.seek(fileSize - 128L);
                  byte[] tag = new byte[3];
                  raf2.read(tag);
                  if (tag[0] == 84 && tag[1] == 65 && tag[2] == 71) {
                     audioSize -= 128L;
                  }
               } catch (Throwable var41) {
                  try {
                     raf2.close();
                  } catch (Throwable var38) {
                     var41.addSuppressed(var38);
                  }

                  throw var41;
               }

               raf2.close();
            } catch (Exception var42) {
            }
         }

         long numFrames = audioSize / frameSize;
         double duration = (double)numFrames * spf / sr;
         info.totalFrames = (int)numFrames;
         info.duration = (int)duration;
         info.samplesPerFrame = spf;
         info.sampleRate = sr;
      } catch (Exception var43) {
      }

      return info;
   }

   public float[] getSpectrum() {
      long now = System.currentTimeMillis();
      if (now - this.lastSpectrumTime < 33L) {
         return this.spectrumCache;
      }

      this.lastSpectrumTime = now;
      boolean hasAudioData = this.hasAudioData || this.flacPlayer != null && this.flacPlayer.hasData();
      if (!hasAudioData) {
         if (this.flacPlayer != null && this.isPlaying() && !this.isPaused() && this.estimatedDuration > 0) {
            int pos = this.elapsedBeforePause + (int)((now - this.playStartTime) / 1000L);
            if (pos >= this.estimatedDuration - 1) {
               if (this.lastNoDataTime == 0L) {
                  this.lastNoDataTime = now;
               } else if (now - this.lastNoDataTime > 500L && !this.noDataTriggered) {
                  this.noDataTriggered = true;
                  this.playing = false;
                  int genSnapshot = this.playGeneration.get();
                  new Thread(() -> {
                     if (genSnapshot == this.playGeneration.get()) {
                        this.next();
                     }
                  }, "Elara-FlacFallbackNext").start();
               }
            }
         }

         for (int i = 0; i < 24; i++) {
            int n = i;
            this.spectrum[n] = this.spectrum[n] * 0.85F;
            this.spectrumCache[i] = this.spectrum[i];
         }

         return this.spectrumCache;
      } else {
         this.lastNoDataTime = 0L;
         this.noDataTriggered = false;
         float[] pcm;
         if (this.flacPlayer != null) {
            pcm = this.flacPlayer.getPcmBuffer();
         } else {
            pcm = this.getPcmBufferCopy();
         }

         float[] raw = FFT.compute(pcm, 24);

         for (int i = 0; i < 24; i++) {
            this.spectrum[i] = Math.max(raw[i], this.spectrum[i] * 0.8F);
            this.spectrumCache[i] = this.spectrum[i];
         }

         return this.spectrumCache;
      }
   }

   public void setVolume(float vol) {
      this.volume = Math.max(0.0F, Math.min(1.0F, vol));
      if (this.currentLine != null) {
         applyGain(this.currentLine, this.volume);
      }

      if (this.flacPlayer != null) {
         this.flacPlayer.setVolume(this.volume);
      }
   }

   private static void applyGain(SourceDataLine line, float vol) {
      try {
         if (line != null && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            float min = control.getMinimum();
            float max = control.getMaximum();
            float target;
            if (vol >= 1.0F) {
               target = max;
            } else if (vol <= 0.0F) {
               target = min;
            } else {
               float gain = (float) (20.0 * Math.log10(vol));
               target = max + gain;
               if (target < min) target = min;
            }
            control.setValue(target);
         }
      } catch (Exception e) { /* ignore */ }
   }

   private float[] getPcmBufferCopy() {
      float[] copy = new float[pcmBuffer.length];
      synchronized (pcmBuffer) {
         System.arraycopy(pcmBuffer, 0, copy, 0, pcmBuffer.length);
      }
      return copy;
   }

   public float getVolume() {
      return this.volume;
   }

   public boolean isPlaying() {
      return this.flacPlayer != null ? this.flacPlayer.isPlaying() : this.playing && !this.paused;
   }

   public boolean isPaused() {
      return this.flacPlayer != null ? this.flacPlayer.isPaused() : this.paused;
   }

   public Song getCurrentSong() {
      return this.currentSong;
   }

   public String getTitle() {
      return this.currentSong != null ? this.currentSong.getTitle() : "No song";
   }

   public String getArtist() {
      return this.currentSong != null ? this.currentSong.getArtist() : "—";
   }

   public float getProgress() {
      if (this.downloading) {
         return this.downloadProgress;
      }

      int dur = this.estimatedDuration;
      return dur <= 0 ? 0.0F : (float)this.getPosition() / dur;
   }

   public int getPosition() {
      if (!this.isPlaying()) {
         if (this.flacPlayer != null && this.flacPlayer.isPaused()) {
            return this.elapsedBeforePause;
         } else {
            return this.paused ? this.elapsedBeforePause : 0;
         }
      } else {
         if (this.isPaused()) {
            return this.elapsedBeforePause;
         }

         int pos = this.elapsedBeforePause + (int)((System.currentTimeMillis() - this.playStartTime) / 1000L);
         int dur = this.estimatedDuration;
         return dur > 0 && pos > dur + 2 ? dur : pos;
      }
   }

   public int getDuration() {
      return this.estimatedDuration;
   }

   public boolean isDownloading() {
      return this.downloading;
   }

   public float getDownloadProgress() {
      return this.downloadProgress;
   }

   public Playlist getPlaylist() {
      return this.playlist;
   }

   public MusicEngine.RepeatMode getRepeatMode() {
      return this.repeatMode;
   }

   public void setRepeatMode(MusicEngine.RepeatMode mode) {
      this.repeatMode = mode;
   }

   public void cycleRepeatMode() {
      MusicEngine.RepeatMode[] modes = MusicEngine.RepeatMode.values();
      this.repeatMode = modes[(this.repeatMode.ordinal() + 1) % modes.length];
   }

   public void setOnlineQueue(List<SongInfo> songs, int currentIndex) {
      this.onlineQueue = songs;
      this.onlineQueueIndex = currentIndex;
   }

   public boolean isOnlineQueueActive() {
      return this.onlineQueue != null && !this.onlineQueue.isEmpty();
   }

   public void loadLyrics(String songId) {
      this.currentLyrics = null;
      this.currentLyricIndex = -1;
      if (songId != null && !songId.isEmpty()) {
         MusicListFetcher.getInstance().fetchLyrics(songId).thenAccept(lrc -> {
            if (lrc != null && !lrc.isEmpty()) {
               this.currentLyrics = this.parseLrc(lrc);
            }
         });
      }
   }

   public void loadLocalLyrics(File audioFile) {
      this.currentLyrics = null;
      this.currentLyricIndex = -1;
      if (audioFile != null) {
         String baseName = audioFile.getName();
         int dot = baseName.lastIndexOf(46);
         if (dot > 0) {
            baseName = baseName.substring(0, dot);
         }

         File folder = audioFile.getParentFile();
         File[] candidates;
         if (folder != null) {
            candidates = new File[]{
               new File(folder, baseName + ".lrc"),
               new File(folder, baseName + ".LRC"),
               new File(folder, "lyrics.lrc"),
               new File(folder, "lyric.lrc"),
               new File(folder, "song.lrc")
            };
         } else {
            candidates = new File[0];
         }

         for (File lrc : candidates) {
            if (lrc.isFile() && lrc.length() > 0L) {
               String content = this.readLyricsFile(lrc);
               if (content != null && !content.isEmpty()) {
                  List<MusicEngine.LyricLine> parsed = this.parseLrc(content);
                  if (parsed != null && !parsed.isEmpty()) {
                     this.currentLyrics = parsed;
                     return;
                  }
               }
            }
         }
      }
   }

   private String readLyricsFile(File file) {
      StringBuilder sb = new StringBuilder();

      try {
         InputStreamReader reader = new InputStreamReader(new FileInputStream(file), "UTF-8");

         String var6;
         try {
            char[] buf = new char[8192];

            int n;
            while ((n = reader.read(buf)) != -1) {
               sb.append(buf, 0, n);
            }

            var6 = sb.toString();
         } catch (Throwable var8) {
            try {
               reader.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }

            throw var8;
         }

         reader.close();
         return var6;
      } catch (Throwable e) {
         System.err.println("[Elara] Failed to read lyrics file " + file + ": " + e.getMessage());
         return null;
      }
   }

   private List<MusicEngine.LyricLine> parseLrc(String lrc) {
      ArrayList<MusicEngine.LyricLine> lines = new ArrayList<>();
      String[] rawLines = lrc.split("\n");
      Pattern lrcPattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

      for (String raw : rawLines) {
         Matcher m = lrcPattern.matcher(raw.trim());
         if (m.matches()) {
            int min = Integer.parseInt(m.group(1));
            int sec = Integer.parseInt(m.group(2));
            String msStr = m.group(3);
            int ms = msStr.length() == 2 ? Integer.parseInt(msStr) * 10 : Integer.parseInt(msStr);
            int timeMs = (min * 60 + sec) * 1000 + ms;
            String text = m.group(4).trim();
            if (!text.isEmpty()) {
               lines.add(new MusicEngine.LyricLine(timeMs, text));
            }
         }
      }

      return lines.isEmpty() ? null : lines;
   }

   public List<MusicEngine.LyricLine> getLyrics() {
      return this.currentLyrics;
   }

   public int getCurrentLyricIndex() {
      if (this.currentLyrics != null && !this.currentLyrics.isEmpty()) {
         int posMs = this.getPosition() * 1000;
         int idx = -1;
         int i = 0;

         while (i < this.currentLyrics.size() && this.currentLyrics.get(i).timeMs <= posMs) {
            idx = i++;
         }

         this.currentLyricIndex = idx;
         return idx;
      } else {
         return -1;
      }
   }

   private static class FlacInfo {
      int duration = 0;
      int sampleRate = 44100;
      int channels = 2;

      private FlacInfo() {
      }
   }

   public static class LyricLine {
      public final int timeMs;
      public final String text;

      public LyricLine(int timeMs, String text) {
         this.timeMs = timeMs;
         this.text = text;
      }
   }

   private static class Mp3Info {
      int duration = 0;
      int totalFrames = 0;
      int samplesPerFrame = 1152;
      int sampleRate = 44100;

      private Mp3Info() {
      }
   }

   public enum RepeatMode {
      OFF,
      ALL,
      ONE,
      SHUFFLE;
   }
}
