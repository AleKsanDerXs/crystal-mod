package ru.alex.crystalmod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import ru.alex.crystalmod.MarketStallEntity;

public class MarketStallRenderer implements BlockEntityRenderer<MarketStallEntity> {
    public MarketStallRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(MarketStallEntity stall, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (stall.sample.isEmpty()) return;
        pose.pushPose();
        try {
            pose.translate(0.5, 1.18, 0.5);
            pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-stall.getBlockState().getValue(ru.alex.crystalmod.MarketStallBlock.FACING).toYRot()+180));
            pose.scale(0.6f, 0.6f, 0.6f);
            Minecraft.getInstance().getItemRenderer().renderStatic(stall.sample, ItemDisplayContext.FIXED, light, overlay, pose, buffers, stall.getLevel(), 0);
        } finally { pose.popPose(); }
    }
    @Override public int getViewDistance() { return 24; }
}
