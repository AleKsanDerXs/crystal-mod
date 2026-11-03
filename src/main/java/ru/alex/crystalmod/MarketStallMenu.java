package ru.alex.crystalmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Only vanilla menu buttons cross the network; the server owns price, quantity and stock. */
public class MarketStallMenu extends AbstractContainerMenu {
    public final BlockPos pos;
    public final Inventory inventory;
    public final ItemStack offeredSample;
    public final ContainerData data = new SimpleContainerData(6);
    public MarketStallMenu(int id, Inventory inventory, BlockPos pos) {
        super(CrystalMenus.MARKET_STALL_MENU.get(), id);
        this.inventory = inventory;
        this.pos = pos;
        offeredSample=stall()==null?ItemStack.EMPTY:stall().sample.copy();
        addDataSlots(data);
        refresh();
    }
    public MarketStallEntity stall() {
        return inventory.player.level().getBlockEntity(pos) instanceof MarketStallEntity s ? s : null;
    }
    void refresh() {
        MarketStallEntity s = stall();
        if (s == null || inventory.player.level().isClientSide) return;
        data.set(0, s.price); data.set(1, s.partySize);
        data.set(2, stock(s)); data.set(3, inventory.player.getUUID().equals(s.owner) ? 1 : 0);
    }
    static Container container(MarketStallEntity s) {
        return s.getLevel().getBlockEntity(s.getBlockPos().below()) instanceof Container c ? c : null;
    }
    static int stock(MarketStallEntity s) {
        Container c = container(s);
        if (c == null || s.sample.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack item = c.getItem(i);
            if (ItemStack.isSameItemSameComponents(item, s.sample)) count += item.getCount();
        }
        return Math.min(count, 32767);
    }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockState(pos).is(CrystalItems.MARKET_STALL.get())
                && player.distanceToSqr(pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) { return ItemStack.EMPTY; }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer sp) || !stillValid(player)) return false;
        MarketStallEntity s = stall();
        if (s == null) return false;
        if (button == 0) { StallSales.buy(sp, this); return true; }
        if (!player.getUUID().equals(s.owner) || !StallSales.canEdit(s)
                || (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims") && !StallClaimsCompat.canConfigure(sp,pos))) return false;
        switch (button) {
            case 1 -> s.price = Math.max(1, s.price - 1);
            case 2 -> s.price = Math.min(30000, s.price + 1);
            case 3 -> s.price = Math.max(1, s.price - 10);
            case 4 -> s.price = Math.min(30000, s.price + 10);
            case 5 -> s.partySize = 1;
            case 6 -> s.partySize = 8;
            case 7 -> s.partySize = 32;
            case 8 -> s.partySize = 64;
            default -> { return false; }
        }
        s.setChanged(); refresh(); broadcastChanges();
        return true;
    }
    void message(ServerPlayer player, String key) { player.sendSystemMessage(Component.translatable("crystalmod.stall." + key)); }
}
