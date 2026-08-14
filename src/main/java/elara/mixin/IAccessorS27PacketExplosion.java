package elara.mixin;

import net.minecraft.network.play.server.S27PacketExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S27PacketExplosion.class)
public interface IAccessorS27PacketExplosion {
    @Accessor("field_149152_f")
    float getMotionX();

    @Accessor("field_149153_g")
    float getMotionY();

    @Accessor("field_149159_h")
    float getMotionZ();

    @Accessor("field_149152_f")
    void setMotionX(float value);

    @Accessor("field_149153_g")
    void setMotionY(float value);

    @Accessor("field_149159_h")
    void setMotionZ(float value);
}