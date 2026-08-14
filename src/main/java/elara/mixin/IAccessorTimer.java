//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package elara.mixin;

import net.minecraft.util.Timer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({Timer.class})
public interface IAccessorTimer {
    @Accessor("timerSpeed")
    float getTimerSpeed();

    @Accessor("timerSpeed")
    void setTimerSpeed(float var1);
}
