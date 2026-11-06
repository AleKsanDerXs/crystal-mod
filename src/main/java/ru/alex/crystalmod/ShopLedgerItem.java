package ru.alex.crystalmod;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.net.ShopResponsePayload;

public class ShopLedgerItem extends Item {
    public ShopLedgerItem(Properties properties){super(properties);}
    @Override public InteractionResultHolder<ItemStack> use(Level level,Player player,InteractionHand hand){
        if(!level.isClientSide && player instanceof ServerPlayer p && StallAccess.allowed(p))
            PacketDistributor.sendToPlayer(p,new ShopResponsePayload(p.containerMenu.containerId,0,"ledger_open","{}"));
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
}
