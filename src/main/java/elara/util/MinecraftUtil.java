/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 */
package elara.util;

import java.io.File;
import net.minecraft.client.Minecraft;

public class MinecraftUtil {
    public static File getMinecraftDir() {
        return Minecraft.getMinecraft().mcDataDir;
    }
}

