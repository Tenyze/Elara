package elara.config.music;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

public class FlacPlayer {
   private volatile boolean playing = false;
   private volatile boolean paused = false;
   private final Object pauseLock = new Object();
   private volatile boolean stopped = false;
   private float volume = 0.7F;
   private final float[] pcmBuffer = new float[1024];
   private int writeIndex = 0;
   private volatile boolean hasData = false;
   private long playStartTime = 0L;
   private int elapsedBeforePause = 0;
   private int totalSamples = 0;
   private int sampleRate = 44100;
   private int channels = 2;
   private SourceDataLine line;
   private FLACDecoder decoder;
   private StreamInfo streamInfo;
   private Thread playThread;
   private final File file;
   private FlacPlayer.PlaybackListener listener;

   public FlacPlayer(File file) {
      this.file = file;
   }

   public void setVolume(float vol) {
      this.volume = Math.max(0.0F, Math.min(1.0F, vol));
      if (this.line != null) {
         applyGain(this.line, this.volume);
      }
   }

   public void setPlaybackListener(FlacPlayer.PlaybackListener l) {
      this.listener = l;
   }

   public int getSampleRate() {
      return this.sampleRate;
   }

   public int getTotalSamples() {
      return this.totalSamples;
   }

   public int getChannels() {
      return this.channels;
   }

   public boolean hasData() {
      return this.hasData;
   }

   public float[] getPcmBuffer() {
      float[] copy = new float[this.pcmBuffer.length];
      synchronized (this) {
         System.arraycopy(this.pcmBuffer, 0, copy, 0, this.pcmBuffer.length);
         return copy;
      }
   }

   public int getPosition() {
      if (!this.playing) {
         return 0;
      } else {
         return this.paused ? this.elapsedBeforePause : this.elapsedBeforePause + (int)((System.currentTimeMillis() - this.playStartTime) / 1000L);
      }
   }

   public void play() {
      this.stopped = false;
      this.paused = false;
      this.playing = true;
      this.elapsedBeforePause = 0;
      this.startDecodeThread(0L);
   }

   public void seekTo(int targetSeconds) {
      this.stopPlaybackInternals();
      this.stopped = false;
      this.paused = false;
      this.playing = true;
      this.elapsedBeforePause = Math.max(0, targetSeconds);
      this.startDecodeThread(targetSeconds);
   }

   private void stopPlaybackInternals() {
      this.stopped = true;
      this.paused = false;
      this.hasData = false;
      synchronized (this.pauseLock) {
         this.pauseLock.notifyAll();
      }
      SourceDataLine l = this.line;
      this.line = null;
      if (l != null) {
         try { l.stop(); l.close(); } catch (Throwable ignored) {}
      }
      Thread t = this.playThread;
      if (t != null && t != Thread.currentThread()) {
         t.interrupt();
         try { t.join(500L); } catch (InterruptedException ignored) {}
      }
      this.playing = false;
   }

   private void startDecodeThread(long skipSeconds) {
      Thread t = new Thread(() -> this.decodeLoop(skipSeconds), "Elara-FlacPlayer");
      t.setDaemon(true);
      this.playThread = t;
      t.start();
   }

   private void decodeLoop(long skipSeconds) {
      try {
         FileInputStream fis = new FileInputStream(this.file);
         BufferedInputStream bis = new BufferedInputStream(fis, 32768);
         this.decoder = new FLACDecoder(bis);
         this.streamInfo = this.decoder.readStreamInfo();
         this.sampleRate = this.streamInfo.getSampleRate();
         this.channels = this.streamInfo.getChannels();
         this.totalSamples = (int)this.streamInfo.getTotalSamples();
         int bitsPerSample = this.streamInfo.getBitsPerSample();
         if (bitsPerSample <= 0) {
            bitsPerSample = 16;
         }

         // 直接用 SourceDataLine 输出 PCM（与 CloudMusic 一致）
         AudioFormat format = new AudioFormat((float) this.sampleRate, 16, this.channels, true, false);
         DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
         if (!AudioSystem.isLineSupported(info)) {
            format = new AudioFormat((float) this.sampleRate, 16, this.channels, true, true);
            info = new DataLine.Info(SourceDataLine.class, format);
         }
         this.line = (SourceDataLine) AudioSystem.getLine(info);
         this.line.open(format, Math.max(this.line.getBufferSize(), 8192));
         applyGain(this.line, this.volume);
         this.line.start();

         long targetSample = skipSeconds * this.sampleRate;
         long samplesSkipped = 0L;
         boolean reachedTarget = targetSample <= 0L;
         boolean started = false;
         int bytesPerSample = (bitsPerSample + 7) / 8;
         int maxInputBytes = 4096 * this.channels * bytesPerSample;
         byte[] rawPcm = new byte[maxInputBytes];
         byte[] outPcm = new byte[4096 * this.channels * 2];

         Frame frame;
         while ((frame = this.decoder.readNextFrame()) != null && !this.stopped) {
            int blockSize = frame.header.blockSize;
            if (!reachedTarget) {
               samplesSkipped += blockSize;
               if (samplesSkipped < targetSample) {
                  continue;
               }
               reachedTarget = true;
            }

            if (!started) {
               started = true;
               this.playStartTime = System.currentTimeMillis();
               if (this.listener != null) {
                  this.listener.onStarted();
               }
            }

            synchronized (this.pauseLock) {
               while (this.paused && !this.stopped) {
                  try {
                     this.pauseLock.wait();
                  } catch (InterruptedException e) {
                     if (this.stopped) {
                        break;
                     }
                  }
               }
            }

            if (this.stopped) {
               break;
            }

            int frameBytes = blockSize * this.channels * bytesPerSample;
            if (rawPcm.length < frameBytes) {
               rawPcm = new byte[frameBytes];
            }

            ByteData bd = this.decoder.decodeFrame(frame, new ByteData(frameBytes));
            int len = bd.getLen();
            byte[] data = bd.getData();
            int outLen = blockSize * this.channels * 2;
            this.convertTo16Bit(data, len, outPcm, outLen, bitsPerSample, this.channels);
            this.feedPcmBuffer(outPcm, outLen);

            if (this.line != null) {
               this.line.write(outPcm, 0, outLen);
            }
         }

         if (this.line != null) {
            this.line.drain();
            this.line.close();
         }
      } catch (Exception e) {
         System.err.println("[Elara] FLAC playback error: " + e.getMessage());
      } finally {
         this.playing = false;
         this.hasData = false;
         if (this.line != null) {
            try { this.line.close(); } catch (Throwable ignored) {}
            this.line = null;
         }
         if (this.listener != null && !this.stopped) {
            this.listener.onFinished();
         }
      }
   }

   private void convertTo16Bit(byte[] in, int inLen, byte[] out, int outLen, int bitsPerSample, int channels) {
      int bytesPerSample = (bitsPerSample + 7) / 8;
      int totalSamples = inLen / bytesPerSample;

      for (int i = 0; i < totalSamples; i++) {
         int sample = 0;
         if (bytesPerSample == 1) {
            sample = (in[i] & 255) << 8;
         } else if (bytesPerSample == 2) {
            sample = in[i * 2 + 1] << 8 | in[i * 2] & 255;
         } else if (bytesPerSample == 3) {
            sample = in[i * 3 + 2] << 16 | (in[i * 3 + 1] & 255) << 8 | in[i * 3] & 255;
            sample >>= 8;
         } else if (bytesPerSample >= 4) {
            sample = in[i * 4 + 3] << 24 | (in[i * 4 + 2] & 255) << 16 | (in[i * 4 + 1] & 255) << 8 | in[i * 4] & 255;
            sample >>= 16;
         }

         short s = (short)sample;
         out[i * 2] = (byte)(s & 0xFF);
         out[i * 2 + 1] = (byte)(s >> 8 & 0xFF);
      }
   }

   private void feedPcmBuffer(byte[] data, int len) {
      int samples = len / 2;

      for (int i = 0; i < samples; i++) {
         int sample = data[i * 2 + 1] << 8 | data[i * 2] & 255;
         short s = (short)sample;
         this.pcmBuffer[this.writeIndex] = s / 32768.0F;
         this.writeIndex = (this.writeIndex + 1) % this.pcmBuffer.length;
      }

      this.hasData = true;
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

   public void pause() {
      if (!this.paused && this.playing) {
         this.elapsedBeforePause = this.elapsedBeforePause + (int)((System.currentTimeMillis() - this.playStartTime) / 1000L);
         this.paused = true;
         if (this.line != null) {
            try { this.line.stop(); } catch (Throwable ignored) {}
         }
      }
   }

   public void resume() {
      if (this.paused) {
         this.paused = false;
         this.playStartTime = System.currentTimeMillis();
         if (this.line != null) {
            try { this.line.start(); } catch (Throwable ignored) {}
         }
         synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
         }
      }
   }

   public void stop() {
      this.stopPlaybackInternals();
      this.elapsedBeforePause = 0;
   }

   public boolean isPlaying() {
      return this.playing && !this.paused;
   }

   public boolean isPaused() {
      return this.paused;
   }

   public interface PlaybackListener {
      void onStarted();
      void onFinished();
   }
}
