package ru.alex.crystalmod;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    public MarketStallBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH)); }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) { b.add(FACING); }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) { return defaultBlockState().setValue(FACING,ctx.getHorizontalDirection().getOpposite()); }
    @Override protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) { return state.setValue(FACING,rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    private static final net.minecraft.world.phys.shapes.VoxelShape SHAPE = net.minecraft.world.phys.shapes.Shapes.or(
            box(0,13,0,16,16,16), box(1,0,1,4,13,4), box(12,0,1,15,13,4),
            box(1,0,12,4,13,15), box(12,0,12,15,13,15));
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
            if(player instanceof net.minecraft.server.level.ServerPlayer sp && !StallAccess.allowed(sp))return InteractionResult.SUCCESS;
            if(player instanceof net.minecraft.server.level.ServerPlayer sp){
                MarketStallMenu.open(sp,pos);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
