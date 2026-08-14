package elara.config.music;

/**
 * 运行时探测当前 JVM 环境可用的 {@link AudioPlaybackProvider}。
 *
 * <p>仅支持 PC 标准桌面环境（Windows / Linux / macOS）：</p>
 * <ol>
 *   <li><b>Javax Sound</b> —— 标准 Java SE {@code javax.sound.sampled.SourceDataLine}，
 *       遍历系统 Mixer 尝试多种 AudioFormat 变体打开。</li>
 *   <li><b>Silent</b> —— 兜底，始终可用，保证音乐引擎线程能正常跑（只是没有声音）。</li>
 * </ol>
 */
public final class AudioProviderFactory {

    static final String TAG = "[Elara-AudioFactory]";

    private AudioProviderFactory() {}

    /**
     * 探测并返回一个可用的 Provider 实例。本方法永不返回 null。
     */
    public static AudioPlaybackProvider createDefault() {
        // 1) 尝试 JavaxSound
        AudioPlaybackProvider probe = tryCreate("elara.config.music.JavaxSoundProvider");
        if (probe != null) {
            System.out.println(TAG + " Selected audio provider: JavaxSoundProvider");
            try {
                return new JavaxSoundProvider();
            } catch (Throwable t) {
                return probe;
            }
        }
        // 2) Silent fallback
        System.out.println(TAG + " Falling back to SilentProvider (no audio output).");
        SilentProvider s = new SilentProvider();
        try { s.open(44100, 2, 16); } catch (Throwable ignored) {}
        return s;
    }

    /**
     * 生产一个干净的 Provider 实例。
     */
    public static AudioPlaybackProvider newProvider() {
        return createDefault();
    }

    private static AudioPlaybackProvider tryCreate(String className) {
        AudioPlaybackProvider inst = null;
        try {
            Class<?> cls = Class.forName(className);
            Object raw = cls.getDeclaredConstructor().newInstance();
            if (!(raw instanceof AudioPlaybackProvider)) {
                return null;
            }
            inst = (AudioPlaybackProvider) raw;
            inst.open(44100, 2, 16);
            inst.stopAndClose();
            return inst;
        } catch (Throwable t) {
            StringBuilder sb = new StringBuilder();
            sb.append(TAG).append(" Provider ").append(className).append(" unavailable (")
              .append(t.getClass().getSimpleName()).append(": ").append(t.getMessage());
            Throwable c = t.getCause();
            int depth = 0;
            while (c != null && depth++ < 5) {
                sb.append("; caused by ").append(c.getClass().getSimpleName())
                  .append(": ").append(c.getMessage());
                c = c.getCause();
            }
            sb.append(")");
            System.out.println(sb.toString());
            if (inst != null) {
                try { inst.stopAndClose(); } catch (Throwable ignored) {}
            }
            return null;
        }
    }
}
