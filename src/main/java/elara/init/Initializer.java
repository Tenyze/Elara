package elara.init;

public class Initializer {
    public Initializer() {
        try {
            installLogSpamFilterOnce();
        } catch (Throwable ignored) {
            // 过滤器安装失败时默认行为也正常，不影响游戏启动
        }
        System.out.println("Meow!");
    }

    private static volatile boolean filterInstalled = false;

    /**
     * 只在第一次调用时把 System.out 包一层 {@link LogSpamFilterStream}。
     * 幂等：反复调用不会出现多层包装。
     */
    private static void installLogSpamFilterOnce() {
        if (filterInstalled) return;
        synchronized (Initializer.class) {
            if (filterInstalled) return;
            try {
                java.io.PrintStream current = System.out;
                if (current instanceof LogSpamFilterStream) {
                    filterInstalled = true;
                    return;
                }
                LogSpamFilterStream filtered = new LogSpamFilterStream(current);
                System.setOut(filtered);
                filterInstalled = true;
                // 安装成功后也用 filtered 打一条，方便确认过滤器已就位
                filtered.println("[Elara-LogFilter] Installed. OneConfig Notification Bloom/Blur startup spam will be silenced.");
            } catch (Throwable t) {
                System.err.println("[Elara-LogFilter] Install failed: " + t.getMessage());
            }
        }
    }
}
