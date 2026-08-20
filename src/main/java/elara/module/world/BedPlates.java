package elara.module.world;

import elara.event.EventTarget;
import elara.events.Render3DEvent;
import elara.mixin.IAccessorEntityRenderer;
import elara.mixin.IAccessorRenderManager;
import elara.module.Module;
import elara.property.properties.IntProperty;
import elara.util.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import javax.vecmath.Vector4d;
import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedPlates extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty range = new IntProperty("Range", 1, 1, 7);

    public BedPlates() {
        super("BedPlates", false);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;

        IntBuffer viewport = GLAllocation.createDirectIntBuffer(16);
        FloatBuffer modelView = GLAllocation.createDirectFloatBuffer(16);
        FloatBuffer projection = GLAllocation.createDirectFloatBuffer(16);

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        double renderPosX = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
        double renderPosY = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
        double renderPosZ = ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();

        List<BedRenderData> renderDataList = new ArrayList<>();

        // Scan nearby beds by ourselves instead of relying on BedESP being enabled.
        // This makes BedPlates work even when BedESP is off.
        List<BlockPos> bedHeads = findBedHeadsNearPlayer();

        for (BlockPos bedPos : bedHeads) {
            IBlockState state = mc.theWorld.getBlockState(bedPos);
            if (!(state.getBlock() instanceof BlockBed)) continue;
            if (state.getValue(BlockBed.PART) != EnumPartType.HEAD) continue;

            BlockPos footPos = bedPos.offset(state.getValue(BlockBed.FACING).getOpposite());
            IBlockState footState = mc.theWorld.getBlockState(footPos);
            if (!(footState.getBlock() instanceof BlockBed)) continue;

            double minX = Math.min(bedPos.getX(), footPos.getX());
            double minY = bedPos.getY();
            double minZ = Math.min(bedPos.getZ(), footPos.getZ());
            double maxX = Math.max(bedPos.getX(), footPos.getX()) + 1.0;
            double maxY = bedPos.getY() + 1.0;
            double maxZ = Math.max(bedPos.getZ(), footPos.getZ()) + 1.0;

            Vector4d pos = projectToScreenOptimized(minX, minY, minZ, maxX, maxY, maxZ, viewport, modelView, projection, renderPosX, renderPosY, renderPosZ);

            if (pos == null) continue;

            float screenX = (float) ((pos.x + pos.z) / 2.0);
            float screenY = (float) pos.y - 30;

            List<BlockEntry> blocks = collectProtectionBlocks(bedPos, footPos);
            if (blocks.isEmpty()) continue;

            blocks.sort((a, b) -> Float.compare(b.hardness, a.hardness));

            float itemSize = 16;
            float padding = 2;
            float totalWidth = blocks.size() * (itemSize + padding) + padding;
            float bgHeight = itemSize + padding * 2;

            double centerX = (bedPos.getX() + footPos.getX()) / 2.0 + 0.5;
            double centerY = bedPos.getY() + 0.5;
            double centerZ = (bedPos.getZ() + footPos.getZ()) / 2.0 + 0.5;
            float dist = (float) mc.thePlayer.getDistance(centerX, centerY, centerZ);

            renderDataList.add(new BedRenderData(screenX - totalWidth / 2, screenY - bgHeight / 2, totalWidth, bgHeight, blocks, dist));
        }

        if (renderDataList.isEmpty()) return;

        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT
                | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.disableLighting();
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
            GlStateManager.enableAlpha();

            for (BedRenderData data : renderDataList) {
                float dist = data.dist;
                float scale = MathHelper.clamp_float(1.0f / (1.0f + dist * 0.08f) * 1.5f, 0.4f, 2.0f);

                float cx = data.bgX + data.totalWidth / 2;
                float cy = data.bgY + data.bgHeight / 2;

                GlStateManager.pushMatrix();
                GlStateManager.translate(cx, cy, 0);
                GlStateManager.scale(scale, scale, 1);
                GlStateManager.translate(-cx, -cy, 0);

                // 用原生 Gui.drawRect 拼接 9-slice 斜角伪圆角 (切 2px)，避免立即模式状态污染
                drawCardBg(data.bgX, data.bgY, data.totalWidth, data.bgHeight, new Color(0, 0, 0, 110).getRGB());

                float itemX = data.bgX + 2;
                float itemY = data.bgY + 2;

                for (BlockEntry entry : data.blocks) {
                    ItemStack stack = new ItemStack(Item.getItemFromBlock(entry.block));

                    GlStateManager.pushMatrix();
                    RenderHelper.enableGUIStandardItemLighting();

                    GlStateManager.translate(itemX + 8, itemY + 8, 0);
                    GlStateManager.scale(1.0F, -1.0F, 1.0F);
                    GlStateManager.translate(-(itemX + 8), -(itemY + 8), 0);

                    RenderUtil.renderItemInGUI(stack, (int) itemX, (int) itemY);

                    RenderHelper.disableStandardItemLighting();
                    GlStateManager.popMatrix();

                    // 方块数显示（大于 1 时才显示，避免视觉噪声）
                    if (entry.count > 1 && mc.fontRendererObj != null) {
                        String label = entry.count >= 64 ? "64+" : String.valueOf(entry.count);
                        int labelW = mc.fontRendererObj.getStringWidth(label);
                        int labelX = (int) (itemX + 16.0F - labelW - 1.0F);
                        int labelY = (int) (itemY + 16.0F - 9.0F);
                        GlStateManager.disableDepth();
                        GlStateManager.pushMatrix();
                        GlStateManager.translate(0.0F, 0.0F, 200.0F);
                        mc.fontRendererObj.drawString(label, labelX + 1, labelY + 1, 0xFF000000);
                        mc.fontRendererObj.drawString(label, labelX, labelY, 0xFFFFFFFF);
                        GlStateManager.popMatrix();
                        GlStateManager.enableDepth();
                    }

                    itemX += 18;
                }

                GlStateManager.popMatrix();
            }
        } finally {
            GL11.glPopAttrib();
            GlStateManager.popMatrix();
        }
        ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 0);
    }

    /**
     * 原生 Gui.drawRect 拼接的 9-slice 伪圆角卡片（切 2px 倒角）。
     * 完全不使用 glBegin/glEnd 立即模式，避免与 setupOverlayRendering 的 Tessellator 状态冲突。
     */
    private static void drawCardBg(float x, float y, float w, float h, int color) {
        final int c = 2; // 倒角像素
        int xi = (int) Math.ceil(x);
        int yi = (int) Math.ceil(y);
        int wi = Math.max(1, (int) Math.floor(w));
        int hi = Math.max(1, (int) Math.floor(h));

        // 中心大矩形 (覆盖主体)
        Gui.drawRect(xi, yi + c, xi + wi, yi + hi - c, color);
        // 上中条（左倒角后）
        Gui.drawRect(xi + c, yi, xi + wi - c, yi + c, color);
        // 下中条
        Gui.drawRect(xi + c, yi + hi - c, xi + wi - c, yi + hi, color);
        // 左中条（已被中心覆盖 -> 中心缺上下c）这里实际只需补齐上下倒角缺口的两侧
        // 左中条: y in (c, hi - c), x in (0, c) — 但中心已经覆盖 x(0,w) y(c,hi-c)，所以左上/右上/左下/右下用小方块
        // 简单做法：四角用 1px 过渡（倒角视觉近似）— 只需要在中心 + 上中 + 下中 之外补 4 条小方，
        // 由于四角是 2px 倒角，我们直接不画 2x2 角块即可，不需要额外矩形。
    }

    private List<BlockPos> findBedHeadsNearPlayer() {
        List<BlockPos> heads = new ArrayList<>();
        BlockPos playerPos = mc.thePlayer.getPosition();
        int r = 16; // scan within 16 block radius, enough for any practical bed location
        int minY = Math.max(0, playerPos.getY() - 8);
        int maxY = Math.min(255, playerPos.getY() + 8);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(playerPos.getX() + dx, y, playerPos.getZ() + dz);
                    IBlockState s = mc.theWorld.getBlockState(pos);
                    if (s.getBlock() instanceof BlockBed && s.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                        heads.add(pos);
                    }
                }
            }
        }
        return heads;
    }

    private Vector4d projectToScreenOptimized(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, IntBuffer viewport, FloatBuffer modelView, FloatBuffer projection, double renderPosX, double renderPosY, double renderPosZ) {
        FloatBuffer coords = GLAllocation.createDirectFloatBuffer(4);
        Vector4d result = null;
        double screenScale = new net.minecraft.client.gui.ScaledResolution(mc).getScaleFactor();

        double[][] corners = {
                {minX, minY, minZ}, {minX, maxY, minZ}, {maxX, minY, minZ}, {maxX, maxY, minZ},
                {minX, minY, maxZ}, {minX, maxY, maxZ}, {maxX, minY, maxZ}, {maxX, maxY, maxZ}
        };

        for (double[] corner : corners) {
            float x = (float) (corner[0] - renderPosX);
            float y = (float) (corner[1] - renderPosY);
            float z = (float) (corner[2] - renderPosZ);

            if (GLU.gluProject(x, y, z, modelView, projection, viewport, coords)) {
                double screenX = coords.get(0) / screenScale;
                double screenY = (mc.displayHeight - coords.get(1)) / screenScale;
                double depth = coords.get(2);

                if (depth < 0.0 || depth >= 1.0) continue;

                if (result == null) {
                    result = new Vector4d(screenX, screenY, screenX, screenY);
                } else {
                    result.x = Math.min(screenX, result.x);
                    result.y = Math.min(screenY, result.y);
                    result.z = Math.max(screenX, result.z);
                    result.w = Math.max(screenY, result.w);
                }
            }
        }
        return result;
    }

    private static final Block[] ALLOWED_BLOCKS = {
            Blocks.wool,
            Blocks.hardened_clay,
            Blocks.glass,
            Blocks.stained_glass,
            Blocks.end_stone,
            Blocks.ladder,
            Blocks.planks,
            Blocks.log,
            Blocks.obsidian,
            Blocks.packed_ice
    };

    private boolean isBlockAllowed(Block block) {
        for (Block allowed : ALLOWED_BLOCKS) {
            if (block == allowed) {
                return true;
            }
        }
        return false;
    }

    private List<BlockEntry> collectProtectionBlocks(BlockPos head, BlockPos foot) {
        Map<Block, BlockEntry> blockMap = new LinkedHashMap<>();
        int centerX = (head.getX() + foot.getX()) / 2;
        int centerY = head.getY();
        int centerZ = (head.getZ() + foot.getZ()) / 2;
        int r = range.getValue();

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = 0; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (r == 1 && Math.abs(dx) + Math.abs(dz) > 1) continue;

                    BlockPos pos = new BlockPos(centerX + dx, centerY + dy, centerZ + dz);
                    Block block = mc.theWorld.getBlockState(pos).getBlock();

                    if (!isBlockAllowed(block)) continue;

                    Block displayBlock = block instanceof BlockStainedGlass ? Blocks.glass : block;
                    BlockEntry existing = blockMap.get(displayBlock);
                    float hardness = block.getBlockHardness(mc.theWorld, pos);
                    hardness = hardness < 0 ? 100 : hardness;
                    if (existing == null) {
                        blockMap.put(displayBlock, new BlockEntry(displayBlock, hardness, 1));
                    } else {
                        existing.count += 1;
                        if (hardness > existing.hardness) existing.hardness = hardness;
                    }
                }
            }
        }
        return new ArrayList<>(blockMap.values());
    }

    private static class BlockEntry {
        final Block block;
        float hardness;
        int count;
        BlockEntry(Block block, float hardness, int count) { this.block = block; this.hardness = hardness; this.count = count; }
    }

    private static class BedRenderData {
        float bgX, bgY, totalWidth, bgHeight;
        List<BlockEntry> blocks;
        float dist;
        BedRenderData(float x, float y, float w, float h, List<BlockEntry> b, float d) {
            this.bgX = x; this.bgY = y; this.totalWidth = w; this.bgHeight = h; this.blocks = b; this.dist = d;
        }
    }
}
