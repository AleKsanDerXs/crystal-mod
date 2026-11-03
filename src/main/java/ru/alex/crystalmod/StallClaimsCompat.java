package ru.alex.crystalmod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

/** Loaded only when OPAC is installed. No per-tick hooks or global player passes. */
public final class StallClaimsCompat {
    private StallClaimsCompat() {}
    public static void onPublicUse(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || event.getEntity().isShiftKeyDown() || !event.getItemStack().isEmpty()) return;
        var level = event.getLevel();
        var state = level.getBlockState(event.getPos());
        if (!(state.getBlock() instanceof MarketStallBlock stall)) return;
        // OPAC has no public per-block override API. Handle only our safe empty-hand action
        // before its general protection listener; never uncancel events or execute item use.
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        if (!level.isClientSide) stall.useWithoutItem(state, level, event.getPos(), event.getEntity(), event.getHitVec());
    }
    public static boolean canConfigure(ServerPlayer player, BlockPos pos) {
        return !OpenPACServerAPI.get(player.getServer()).getChunkProtection()
                .onEntityPlaceBlock(player, player.serverLevel(), pos);
    }
}
