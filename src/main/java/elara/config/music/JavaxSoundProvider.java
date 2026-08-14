package elara.config.music;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.List;

/**
 * 标准 JVM 桌面环境音频输出实现：基于 {@code javax.sound.sampled.SourceDataLine}。
 *
 * <p>加固后的版本：</p>
 * <ol>
 *   <li>枚举系统所有 {@link Mixer}，逐个尝试；</li>
 *   <li>对每个 Mixer 枚举多种 {@link AudioFormat} 变体（采样率 / 端序 / 位深 / 声道）；</li>
 *   <li>以 {@code line.open(format)} 真正成功作为"可用"的唯一依据，
 *       避免 isLineSupported 静态查询在 Windows 上与实际行为不一致。</li>
 * </ol>
 */
public class JavaxSoundProvider implements AudioPlaybackProvider {

    private static final String TAG = "[Elara-Javax]";

    private volatile SourceDataLine line;
    private AudioFormat activeFormat;
    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private float volume = 1.0F;
    private String selectedMixerName = "";

    /**
     * 生成一组按优先级排序的 AudioFormat 候选变体，以匹配各种声卡的原生格式。
     */
    private static List<AudioFormat> candidateFormats(int desiredSampleRate, int desiredChannels, int desiredBits) {
        List<AudioFormat> result = new ArrayList<>(16);
        int[] sampleRates = unique(desiredSampleRate, 44100, 48000, 22050, 32000, 16000);
        int[] channels = unique(desiredChannels, 2, 1);
        int[] bits = unique(desiredBits, 16, 8);
        boolean[] endians = new boolean[] { false, true }; // little first (Windows 主流)
        for (int sr : sampleRates) {
            for (int ch : channels) {
                for (int b : bits) {
                    for (boolean big : endians) {
                        result.add(new AudioFormat(sr, b, ch, true, big));
                        // signed 是 PCM_SIGNED；FLAC/MP3 解码出的都是 signed，不需要 unsigned 变体
                    }
                }
            }
        }
        return result;
    }

    private static int[] unique(int... values) {
        List<Integer> list = new ArrayList<>(values.length);
        for (int v : values) {
            if (v > 0 && !list.contains(v)) list.add(v);
        }
        int[] res = new int[list.size()];
        for (int i = 0; i < list.size(); i++) res[i] = list.get(i);
        return res;
    }

    @Override
    public void open(int sampleRate, int channels, int bitsPerSample) throws Exception {
        List<AudioFormat> formats = candidateFormats(sampleRate, channels, bitsPerSample);

        // 1. 收集所有 Mixer（包括默认）
        Mixer.Info[] mixerInfos;
        try {
            mixerInfos = AudioSystem.getMixerInfo();
        } catch (Throwable t) {
            mixerInfos = new Mixer.Info[0];
        }
        // 把"默认 Mixer（AudioSystem.getLine 选的那个）"放在遍历首位，作为首选尝试
        List<Mixer.Info> mixers = new ArrayList<>(mixerInfos.length + 1);
        mixers.add(null); // null 代表使用默认策略 (AudioSystem.getLine)
        for (Mixer.Info mi : mixerInfos) mixers.add(mi);

        Exception lastException = null;
        StringBuilder tryLog = new StringBuilder();
        tryLog.append(TAG).append(" Trying ").append(mixers.size()).append(" mixers x ")
                .append(formats.size()).append(" formats.");

        for (Mixer.Info mi : mixers) {
            String mixerName = (mi == null) ? "[Default AudioSystem Mixer]" : mi.getName();
            for (AudioFormat fmt : formats) {
                try {
                    DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
                    SourceDataLine candidate;
                    if (mi == null) {
                        candidate = (SourceDataLine) AudioSystem.getLine(info);
                    } else {
                        Mixer m = AudioSystem.getMixer(mi);
                        if (!m.isLineSupported(info)) {
                            continue;
                        }
                        candidate = (SourceDataLine) m.getLine(info);
                    }
                    candidate.open(fmt);
                    // 用 open 成功作为判断依据（经验 1199639）
                    candidate.start();
                    this.line = candidate;
                    this.activeFormat = fmt;
                    this.selectedMixerName = mixerName;
                    applyHardwareVolume();
                    System.out.println(TAG + " OK: mixer=\"" + mixerName
                            + "\" sr=" + fmt.getSampleRate() + " ch=" + fmt.getChannels()
                            + " bits=" + fmt.getSampleSizeInBits()
                            + (fmt.isBigEndian() ? " BE" : " LE"));
                    return;
                } catch (Exception e) {
                    lastException = e;
                }
            }
        }

        tryLog.append(" All combinations failed.");
        System.out.println(tryLog.toString());
        if (lastException != null) {
            throw lastException;
        }
        throw new RuntimeException("No supported SourceDataLine found on any mixer with any PCM format.");
    }

    @Override
    public void write(byte[] pcm, int off, int len) {
        synchronized (this.pauseLock) {
            while (this.paused && this.line != null) {
                try {
                    this.pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        SourceDataLine l = this.line;
        if (l == null) return;

        if (this.volume != 1.0F) {
            applySoftwareVolume(pcm, off, len);
        }

        try {
            int written = 0;
            while (written < len) {
                int n = l.write(pcm, off + written, len - written);
                if (n <= 0) break;
                written += n;
            }
        } catch (Throwable ignored) {
            // 避免任何音频底层崩溃影响上层解码线程
        }
    }

    private void applySoftwareVolume(byte[] pcm, int off, int len) {
        float v = this.volume;
        for (int i = 0; i < len; i += 2) {
            int sample = (pcm[off + i + 1] << 8) | (pcm[off + i] & 0xFF);
            short s = (short) sample;
            s = (short) (s * v);
            pcm[off + i] = (byte) (s & 0xFF);
            pcm[off + i + 1] = (byte) ((s >> 8) & 0xFF);
        }
    }

    private void applyHardwareVolume() {
        SourceDataLine l = this.line;
        if (l == null) return;
        try {
            if (l.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) l.getControl(FloatControl.Type.MASTER_GAIN);
                float db = this.volume <= 0.001F
                        ? gain.getMinimum()
                        : (float) (Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), 20.0 * Math.log10(this.volume))));
                gain.setValue(db);
            }
        } catch (Throwable ignored) {
            // 某些设备不支持 gain control，忽略，走软件音量
        }
    }

    @Override
    public void drain() {
        SourceDataLine l = this.line;
        if (l != null) {
            try { l.drain(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused) {
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
        }
        SourceDataLine l = this.line;
        if (l != null) {
            try {
                if (paused) l.stop(); else l.start();
            } catch (Throwable ignored) {}
        }
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
        applyHardwareVolume();
    }

    @Override
    public void stopAndClose() {
        this.paused = false;
        synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
        }
        SourceDataLine l = this.line;
        this.line = null;
        if (l != null) {
            try { l.stop(); } catch (Throwable ignored) {}
            try { l.close(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public boolean isOpen() {
        SourceDataLine l = this.line;
        return l != null && l.isOpen();
    }
}
