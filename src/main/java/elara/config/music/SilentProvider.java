package elara.config.music;

/**
 * 静默 / 无头环境的 fallback Provider。
 *
 * <p>不向任何音频设备输出数据，但照常接收 PCM，保证上层的 {@code SpectrumAudioDevice}
 * 环形缓冲、进度计算、歌词滚动等逻辑不因为"没有音频系统"而报错。</p>
 *
 * <p>当 JavaxSoundProvider 初始化失败时，
 * 就会由 {@link AudioProviderFactory} 回退到本类。</p>
 */
public class SilentProvider implements AudioPlaybackProvider {

    private final Object pauseLock = new Object();
    private volatile boolean paused = false;
    private volatile boolean open = false;
    public float volume = 1.0F;

    @Override
    public void open(int sampleRate, int channels, int bitsPerSample) throws Exception {
        // 没有需要打开的硬件，直接标记成功
        this.open = true;
    }

    @Override
    public void write(byte[] pcm, int off, int len) {
        synchronized (this.pauseLock) {
            while (this.paused) {
                try {
                    this.pauseLock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        // 静默模式：把写进来的数据"吃"掉但不做任何事。
        // 这里不 sleep，故意让解码线程跑满（实际应用层会做 tick 节流）
    }

    @Override
    public void drain() {
        // no-op
    }

    @Override
    public void setPaused(boolean paused) {
        this.paused = paused;
        if (!paused) {
            synchronized (this.pauseLock) {
                this.pauseLock.notifyAll();
            }
        }
    }

    @Override
    public void setVolume(float volume) {
        this.volume = Math.max(0.0F, Math.min(1.0F, volume));
    }

    @Override
    public void stopAndClose() {
        this.paused = false;
        synchronized (this.pauseLock) {
            this.pauseLock.notifyAll();
        }
        this.open = false;
    }

    @Override
    public boolean isOpen() {
        return this.open;
    }
}
