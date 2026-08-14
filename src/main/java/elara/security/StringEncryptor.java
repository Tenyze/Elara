package elara.security;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

import org.objectweb.asm.*;

public final class StringEncryptor {
    
    private static final String DECRYPT_METHOD_DESC = "(Ljava/lang/String;)Ljava/lang/String;";
    private static final String DECRYPT_METHOD_OWNER = "elara/security/StringCrypt";
    private static final String DECRYPT_METHOD_NAME = "decrypt";
    
    private static final Set<String> EXCLUDE_PACKAGES = new HashSet<String>(Arrays.asList(
            "elara/deps/",
            "elara/mixin/",
            "elara/init/",
            "elara/security/",
            "elara/module/",
            "elara/config/music/",
            "elara/config/gui/MusicPlayerPage",
            "elara/config/gui/MusicHud",
            "elara/config/gui/MusicApiOption",
            "elara/util/NetworkMusicDownloader"
    ));
    
    private static final Set<String> EXCLUDE_STRINGS = new HashSet<String>(Arrays.asList(
            "elara",
            "Elara",
            "Mixin",
            "@Mixin",
            "@Inject",
            "@Shadow",
            "@Overwrite",
            "@EventTarget",
            "@Config",
            "@Color",
            "@Slider",
            "@Switch",
            "@Text",
            "@Dropdown",
            "@Button",
            "@HUD",
            "@Page"
    ));
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... StringEncryptor <classesDir>");
            System.exit(1);
        }
        
        Path classesDir = Paths.get(args[0]);
        if (!Files.exists(classesDir) || !Files.isDirectory(classesDir)) {
            System.err.println("Invalid classes directory: " + classesDir);
            System.exit(1);
        }
        
        int encryptedCount = 0;
        int fileCount = 0;
        
        try (Stream<Path> walk = Files.walk(classesDir)) {
            List<Path> classFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .collect(Collectors.toList());
            
            for (Path classFile : classFiles) {
                String relativePath = classesDir.relativize(classFile).toString().replace("\\", "/");
                
                boolean skip = false;
                for (String pkg : EXCLUDE_PACKAGES) {
                    if (relativePath.startsWith(pkg)) {
                        skip = true;
                        break;
                    }
                }
                
                if (skip || relativePath.contains("$")) {
                    continue;
                }
                
                try {
                    byte[] bytes = Files.readAllBytes(classFile);
                    byte[] transformed = transformClass(bytes);
                    
                    if (!Arrays.equals(bytes, transformed)) {
                        Files.write(classFile, transformed);
                        encryptedCount++;
                    }
                    fileCount++;
                } catch (Exception e) {
                    System.err.println("Failed to process: " + relativePath);
                    e.printStackTrace();
                }
            }
        }
        
        System.out.println("[StringEncryptor] Processed " + fileCount + " class files");
        System.out.println("[StringEncryptor] Encrypted strings in " + encryptedCount + " class files");
    }
    
    private static byte[] transformClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
        
        cr.accept(new ClassVisitor(Opcodes.ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                
                return new MethodVisitor(Opcodes.ASM8, mv) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String && shouldEncrypt((String) value)) {
                            String encryptedHex = StringCrypt.bytesToHex(StringCrypt.encrypt((String) value));
                            super.visitLdcInsn(encryptedHex);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, DECRYPT_METHOD_OWNER, DECRYPT_METHOD_NAME, DECRYPT_METHOD_DESC, false);
                        } else {
                            super.visitLdcInsn(value);
                        }
                    }
                };
            }
        }, 0);
        
        return cw.toByteArray();
    }
    
    private static boolean shouldEncrypt(String str) {
        if (str.length() < 3) {
            return false;
        }
        
        for (String exclude : EXCLUDE_STRINGS) {
            if (str.equals(exclude) || str.contains(exclude)) {
                return false;
            }
        }
        
        if (str.matches("^[a-zA-Z0-9._-]+$") && str.length() < 6) {
            return false;
        }
        
        return true;
    }
    
    private StringEncryptor() {}
}
