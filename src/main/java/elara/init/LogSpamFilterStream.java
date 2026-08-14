package elara.init;

import java.io.PrintStream;

/**
 * 在不修改 OneConfig 源码的前提下，用标准 JDK {@link java.io.FilterOutputStream} 思路
 * 包一层 System.out，抑制启动前帧缓冲未就绪阶段 Notification 类 Bloom/Blur 刷屏的错误日志。
 * 其他日志原样转发，不吞任何正常错误。
 *
 * <p>过滤器生效后，以下两种内容会被静默丢弃：</p>
 * <ul>
 *   <li>{@code [Notification] Bloom failed: framebufferWidth}</li>
 *   <li>{@code [Notification] Blur failed: framebufferWidth}</li>
 * </ul>
 * 命中条件宽松写："[Notification]" + ("Bloom failed" 或 "Blur failed") 两个子串同时出现即拦截，
 * 即便 OneConfig 后续版本消息文字略有调整（如冒号、错误描述）也能命中。
 */
public class LogSpamFilterStream extends PrintStream {

    private static final String MARKER_NOTIFICATION = "[Notification]";
    private static final String MARKER_BLOOM        = "Bloom failed";
    private static final String MARKER_BLUR         = "Blur failed";

    LogSpamFilterStream(PrintStream delegate) {
        super(delegate, true);
    }

    private static boolean isSilenced(String s) {
        if (s == null) return false;
        return s.contains(MARKER_NOTIFICATION)
                && (s.contains(MARKER_BLOOM) || s.contains(MARKER_BLUR));
    }

    @Override public void println(String x) { if (!isSilenced(x)) super.println(x); }
    @Override public void println(Object x) {
        if (x == null) { super.println((String) null); return; }
        String s = x.toString();
        if (!isSilenced(s)) super.println(x);
    }
    @Override public void println(char[] x) {
        if (x == null) { super.println((String) null); return; }
        String s = new String(x);
        if (!isSilenced(s)) super.println(x);
    }

    @Override public void print(String s) { if (!isSilenced(s)) super.print(s); }
    @Override public void print(Object obj) {
        if (obj == null) { super.print((String) null); return; }
        String s = obj.toString();
        if (!isSilenced(s)) super.print(obj);
    }
    @Override public void print(char[] s) {
        if (s == null) { super.print((String) null); return; }
        String str = new String(s);
        if (!isSilenced(str)) super.print(s);
    }

    @Override public PrintStream printf(String format, Object... args) {
        if (format == null) return super.printf(null, args);
        if (isSilenced(format)) return this;
        return super.printf(format, args);
    }
}
