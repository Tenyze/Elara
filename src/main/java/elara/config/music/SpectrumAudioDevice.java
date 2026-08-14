package elara.config.music;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.player.AudioDeviceBase;

/**
 * 频谱采集 + 平台无关输出的 MP3 播放设备。
 *
 * <p>继承 {@link AudioDeviceBase}（JLayer 官方抽象基类），这样
 * 我们只需要重写 4 个 XxxImpl 钩子，就能满足整个 {@code AudioDevice} 接口契约。
 * 音频输出交给 {@link AudioPlaybackProvider}（Android AudioTrack / Javax Sound / Silent）。</p>
 */
public class SpectrumAudioDevice extends AudioDeviceBase {

    private final float[] pcmBuffer = new float[1024];
    private int writeIndex = 0;
    private volatile boolean hasData = false;
    private float volume = 0.7F;
    private volatile boolean paused = false;
    private final Object pauseLock = new Object();

    private AudioPlaybackProvider provider;
    private int position = 0;

    @Override
    protected void openImpl() throws JavaLayerException {
        try {
            Decoder dec = this.getDecoder();
            int sampleRate = dec != null ? dec.getOutputFrequency() : 44100;
            int channels = dec != null ? dec.getOutputChannels() : 2;
            if (sampleRate <= 0) sampleRate = 44100;
            if (channels <= 0) channels = 2;
            this.provider = AudioProviderFactory.newProvider();
            this.provider.open(sampleRate, channels, 16);
            this.provider.setVolume(this.volume);
        } catch (Throwable t) {
            throw new JavaLayerException("SpectrumAudioDevice open failed", t);
        }
    }

    @Override
    protected void writeImpl(short[] samples, int offs, int len) throws JavaLayerException {
        synchronized (this.pauseLock) {
            while (this.paused && this.isOpen()) {
                try {
                    this.pauseLock.wait();
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        // 1) 采集 PCM 到环形缓冲（供 FFT 频谱）
        for (int i = 0; i < len; i++) {
            this.pcmBuffer[this.writeIndex] = samples[offs + i] / 32768.0F;
            this.writeIndex = (this.writeIndex + 1) % this.pcmBuffer.length;
        }
        this.hasData = true;

        // 2) 软件音量
        if (this.volume != 1.0F) {
            for (int i = 0; i < len; i++) {
                samples[offs + i] = (short) (samples[offs + i] * this.volume);
            }
        }

        // 3) 交给平台无关 provider 输出
        try {
            AudioPlaybackProvider p = this.provider;
            if (p != null) {
                p.writeShorts(samples, offs, len);
            }
        } catch (Throwable t) {
            // 不要把播放异常抛给 JLayer，否则它会停止整个解码线程
        }

        this.position += len * 1000 / 44100;
    }

    @Override
    protected void closeImpl() {
        this.position = 0;
        AudioPlaybackProvider p = this.provider;
        this.provider = null;
        if (p != null) {
            try { p.stopAndClose(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void flushImpl() {
        AudioPlaybackProvider p = this.provider;
        if (p != null) {
            try { p.drain(); } catch (Throwable ignored) {}
        }
    }

    @Override
    public int getPosition() {
        return this.position;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused) {
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
        }
        AudioPlaybackProvider p = this.provider;
        if (p != null) p.setPaused(paused);
    }

    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
        AudioPlaybackProvider p = this.provider;
        if (p != null) p.setVolume(this.volume);
    }

    public float getVolume() {
        return this.volume;
    }

    public float[] getPcmBuffer() {
        float[] copy = new float[this.pcmBuffer.length];
        synchronized (this) {
            System.arraycopy(this.pcmBuffer, 0, copy, 0, this.pcmBuffer.length);
            return copy;
        }
    }

    public boolean hasData() {
        return this.hasData;
    }
}
