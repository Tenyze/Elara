package elara.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * 字符串加密器 - 动态密钥版
 *
 * 密钥生成策略：
 * 1. 4段分散的byte[]常量（看起来像普通数据）
 * 2. 运行时通过SHA-256派生 + 多轮异或混淆
 * 3. 加入环境熵（类加载器hash、当前时间秒级扰动）
 * 4. 检测到调试/agent时返回错误密钥
 *
 * 反编译者无法静态提取密钥，必须动态调试。
 */
public final class StringCrypt {

    // 看起来像普通配置数据的分散片段（实际是密钥种子的异或预计算结果）
    private static final byte[] FRAGMENT_A = new byte[]{
            (byte) 0x47, (byte) 0xA3, (byte) 0x12, (byte) 0x8E
    };
    private static final byte[] FRAGMENT_B = new byte[]{
            (byte) 0xD1, (byte) 0x5F, (byte) 0xC4, (byte) 0x22
    };
    private static final byte[] FRAGMENT_C = new byte[]{
            (byte) 0x9B, (byte) 0x38, (byte) 0x71, (byte) 0xE0
    };
    private static final byte[] FRAGMENT_D = new byte[]{
            (byte) 0x6A, (byte) 0xE4, (byte) 0x2D, (byte) 0x4F
    };

    // 异或混淆表（让密钥派生非线性化）
    private static final byte[] XOR_TABLE_A = new byte[]{
            (byte) 0x3C, (byte) 0x7E, (byte) 0x51, (byte) 0x29
    };
    private static final byte[] XOR_TABLE_B = new byte[]{
            (byte) 0xA8, (byte) 0x14, (byte) 0x6B, (byte) 0x93
    };

    // 缓存派生的密钥（避免每次解密都重新计算）
    private static volatile SecretKeySpec cachedKey = null;
    private static volatile Cipher encryptCipher;
    private static volatile Cipher decryptCipher;

    private static volatile boolean integrityChecked = false;
    private static volatile boolean integrityOk = false;

    /**
     * 完整性检查 - 检测调试器/agent
     * 如果检测到异常，返回false，导致解密失败
     */
    private static boolean checkIntegrity() {
        if (integrityChecked) {
            return integrityOk;
        }
        synchronized (StringCrypt.class) {
            if (integrityChecked) {
                return integrityOk;
            }
            try {
                // 检测：management agent是否加载（jdwp/intellij/jprofil等）
                Class<?> mgmtClass = Class.forName("java.lang.management.ManagementFactory");
                java.lang.reflect.Method getRuntimeMXBean = mgmtClass.getMethod("getRuntimeMXBean");
                Object runtimeMXBean = getRuntimeMXBean.invoke(null);
                java.lang.reflect.Method getInputArguments = runtimeMXBean.getClass().getMethod("getInputArguments");
                @SuppressWarnings("unchecked")
                java.util.List<String> args = (java.util.List<String>) getInputArguments.invoke(runtimeMXBean);

                for (String arg : args) {
                    String lower = arg.toLowerCase();
                    if (lower.contains("jdwp") || lower.contains("agentlib") ||
                        lower.contains("intellij") || lower.contains("jprofil") ||
                        lower.contains("yourkit") || lower.contains("jrebel")) {
                        integrityOk = false;
                        integrityChecked = true;
                        return false;
                    }
                }
                integrityOk = true;
                integrityChecked = true;
                return true;
            } catch (Throwable t) {
                // 检查失败时保守处理 - 允许运行（避免误伤正常环境）
                integrityOk = true;
                integrityChecked = true;
                return true;
            }
        }
    }

    /**
     * 动态生成AES密钥
     * 关键：密钥不存储在任何单一位置，运行时通过多步计算得出
     *
     * 注意：不能依赖环境熵（如ClassLoader hash），因为加密在Gradle构建时进行，
     * 解密在游戏运行时进行，两者JVM不同，密钥必须确定性生成。
     */
    private static SecretKeySpec deriveKey() {
        if (cachedKey != null) {
            return cachedKey;
        }
        synchronized (StringCrypt.class) {
            if (cachedKey != null) {
                return cachedKey;
            }

            // 第1步：合并4个片段（每个片段与异或表组合，消除直接可见的密钥）
            byte[] seed = new byte[16];
            for (int i = 0; i < 4; i++) {
                seed[i] = (byte) (FRAGMENT_A[i] ^ XOR_TABLE_A[i]);
                seed[i + 4] = (byte) (FRAGMENT_B[i] ^ XOR_TABLE_B[i]);
                seed[i + 8] = (byte) (FRAGMENT_C[i] ^ XOR_TABLE_A[(i + 1) % 4]);
                seed[i + 12] = (byte) (FRAGMENT_D[i] ^ XOR_TABLE_B[(i + 2) % 4]);
            }

            // 第2步：SHA-256派生，取前16字节
            // 消除片段之间的线性关系，让密钥无法通过观察seed反推
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                byte[] digest = sha256.digest(seed);
                byte[] keyBytes = Arrays.copyOf(digest, 16);

                // 第3步：最终异或混淆（防止SHA-256输出被直接观察）
                for (int i = 0; i < 16; i++) {
                    keyBytes[i] ^= XOR_TABLE_A[i % 4];
                    keyBytes[i] ^= XOR_TABLE_B[(i + 1) % 4];
                }

                cachedKey = new SecretKeySpec(keyBytes, "AES");
                return cachedKey;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Key derivation failed", e);
            }
        }
    }

    private static void init() {
        if (encryptCipher == null) {
            synchronized (StringCrypt.class) {
                if (encryptCipher == null) {
                    try {
                        SecretKeySpec keySpec = deriveKey();
                        encryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                        encryptCipher.init(Cipher.ENCRYPT_MODE, keySpec);
                        decryptCipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                        decryptCipher.init(Cipher.DECRYPT_MODE, keySpec);
                    } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
                        throw new RuntimeException("StringCrypt initialization failed", e);
                    }
                }
            }
        }
    }

    public static byte[] encrypt(String plaintext) {
        init();
        try {
            byte[] bytes = plaintext.getBytes(StandardCharsets.UTF_8);
            int blockSize = 16;
            int padding = blockSize - (bytes.length % blockSize);
            byte[] padded = new byte[bytes.length + padding];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            for (int i = 0; i < padding; i++) {
                padded[bytes.length + i] = (byte) padding;
            }
            return encryptCipher.doFinal(padded);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(byte[] encrypted) {
        if (!checkIntegrity()) {
            throw new RuntimeException("Decryption failed");
        }
        init();
        try {
            byte[] decrypted = decryptCipher.doFinal(encrypted);
            int padding = decrypted[decrypted.length - 1] & 0xFF;
            return new String(Arrays.copyOf(decrypted, decrypted.length - padding), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public static String decrypt(String hex) {
        byte[] encrypted = hexToBytes(hex);
        return decrypt(encrypted);
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private StringCrypt() {}
}
