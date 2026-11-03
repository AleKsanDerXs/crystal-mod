package ru.alex.crystalmod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Stock remains in the real container below; the stall has no ticking inventory. */
public class MarketStallBlock extends BaseEntityBlock {
    public static final MapCodec<MarketStallBlock> CODEC = simpleCodec(MarketStallBlock::new);
    public MarketStallBlock(Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            box(0,13,0,16,16,16), box(1,0,1,4,13,4), box(12,0,1,15,13,4),
            box(1,0,12,4,13,15), box(12,0,12,15,13,15), box(1,5,7,15,8,9));
    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext context) { return SHAPE; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MarketStallEntity(pos, state); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player && level.getBlockEntity(pos) instanceof MarketStallEntity stall) {
            stall.owner = player.getUUID();
            stall.ownerName = player.getGameProfile().getName();
            stall.setChanged();
        }
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!level.isClientSide && !state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof MarketStallEntity stall)StallShops.removed(stall);
        super.onRemove(state,level,pos,next,moving);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Sneaking is reserved for Carry On / other tools. Never configure through that gesture.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MarketStallEntity stall) {
            if (!player.getOffhandItem().isEmpty() && player.getUUID().equals(stall.owner)) {
                if (!StallSales.canEdit(stall) || net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims")
                        && player instanceof net.minecraft.server.level.ServerPlayer sp
                        && !StallClaimsCompat.canConfigure(sp, pos)) {
                    player.displayClientMessage(Component.translatable("crystalmod.stall.not_owner"), true);
                } else {
                    // Keep only the cosmetic item id, never a copy of a backpack's contents.
                    ItemStack sample = new ItemStack(player.getOffhandItem().getItem());
                    if (!ItemStack.isSameItemSameComponents(sample,player.getOffhandItem())) {
                        player.displayClientMessage(Component.translatable("crystalmod.stall.unsupported"),true);
                        return InteractionResult.SUCCESS;
                    }
                    if (!ItemStack.isSameItemSameComponents(sample, stall.sample)) {
                        stall.sample = sample;
                        stall.setChanged();
                        level.sendBlockUpdated(pos, state, state, 3);
                    }
                    player.displayClientMessage(Component.translatable("crystalmod.stall.selected"), true);
                }
            } else {
                if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                    sp.openMenu(new net.minecraft.world.SimpleMenuProvider(
                            (id,inv,p) -> new MarketStallMenu(id,inv,pos),
                            Component.translatable("block.crystalmod.market_stall")), pos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
