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

/** Терминал рынка — выставление лота (см. {@link MarketListingMenu}). Тоже без BlockEntity. */
public class MarketTerminalBlock extends Block {

    public MarketTerminalBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            MenuProvider provider = new SimpleMenuProvider(
                    (id, inv, p) -> new MarketListingMenu(id, inv),
                    Component.translatable("crystalmod.market_block.title")
            );
            player.openMenu(provider);
        }
        return InteractionResult.SUCCESS;
    }
}
