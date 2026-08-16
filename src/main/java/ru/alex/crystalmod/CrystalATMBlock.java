package ru.alex.crystalmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Банкомат. Обычный {@link Block} без BlockEntity — состояние операции целиком
 * живёт в {@link CrystalATMMenu} на время, пока меню открыто (см. её javadoc
 * про то, почему тикер тут не нужен).
 */
public class CrystalATMBlock extends Block {

    public CrystalATMBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new CrystalATMMenu(id, inv),
                    Component.translatable("crystalmod.atm.title")
            );
            player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }
}
