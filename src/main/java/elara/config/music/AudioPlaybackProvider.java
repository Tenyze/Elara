package elara.config.music;

/**
 * 统一音频输出抽象层。
 *
 * <p>设计目标：在桌面 JVM（使用 javax.sound）以及完全没有音频设备的环境
 * （使用静音输出）之间提供一套通用接口，屏蔽底层差异。</p>
 *
 * <p>全线通用原则：音频输出统一使用 <b>16-bit 小端 PCM</b> 写入，
 * 由各 Provider 在内部转换成系统需要的格式。</p>
 *
 * <p>noverify 基础：所有依赖特定平台 API 的类都在独立的 .java 文件中定义，
 * 由 {@link AudioProviderFactory} 在运行时做存在性探测并实例化；
 * 即使在 {@code -Xverify:none} 模式下，某一 Provider 的字节码因引用
 * 缺失类型导致异常时，Factory 也会安全 fallback 到下一个实现。</p>
 */
public interface AudioPlaybackProvider {

    /**
     * 打开音频设备。
     *
     * @param sampleRate    采样率，常见值：44100、48000、22050
     * @param channels      声道数：1=单声道，2=立体声
     * @param bitsPerSample 位深：目前固定使用 16
     * @throws Exception 打开失败（设备不支持、平台 API 不存在等）
     */
    void open(int sampleRate, int channels, int bitsPerSample) throws Exception;

    /**
     * 写入 16-bit 小端 PCM 数据。阻塞直到数据被设备/缓冲接受。
     *
     * @param pcm 字节数组，小端顺序 16-bit PCM
     * @param off 起始偏移
     * @param len 字节长度（必须是 2*channels 的整数倍）
     */
    void write(byte[] pcm, int off, int len);

    /**
     * 写入 16-bit PCM short 样本（方便调用方）。默认实现会在内部转换为小端字节后调用
     * {@link #write(byte[], int, int)}。
     */
    default void writeShorts(short[] samples, int offs, int len) {
        byte[] buf = new byte[len * 2];
        for (int i = 0; i < len; i++) {
            short s = samples[offs + i];
            buf[i * 2] = (byte) (s & 0xFF);
            buf[i * 2 + 1] = (byte) ((s >> 8) & 0xFF);
        }
        write(buf, 0, buf.length);
    }

    /**
     * 阻塞等待内部缓冲全部播放完毕。
     */
    void drain();

    /**
     * 暂停输出（暂停时 write 会阻塞或忽略，取决于实现；推荐上层自己同步）。
     */
    void setPaused(boolean paused);

    /**
     * 调节音量（0..1）。实现层面可以做软件乘法（静默 fallback），
     * 或者在支持时走系统硬件音量。
     */
    void setVolume(float volume);

    /**
     * 停止并关闭音频设备。调用后再次使用应重新 {@link #open}。
     */
    void stopAndClose();

    /**
     * @return true 表示已经成功 open 且可用。
     */
    boolean isOpen();
}
