package ru.alex.crystalmod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Buttons and validated configuration payloads; the server owns price, quantity and stock. */
public class MarketStallMenu extends AbstractContainerMenu {
    private static final RateLimiter ACTIONS=new RateLimiter(20,1000);
    public final BlockPos pos;
    public final Inventory inventory;
    public final ItemStack offeredSample;
    public final ContainerData data = new SimpleContainerData(6);
    public MarketStallMenu(int id, Inventory inventory, BlockPos pos) { this(id,inventory,pos,null); }
    public MarketStallMenu(int id, Inventory inventory, BlockPos pos, ItemStack sample) {
        super(CrystalMenus.MARKET_STALL_MENU.get(), id);
        this.inventory = inventory;
        this.pos = pos;
        offeredSample=sample!=null?sample:(stall()==null?ItemStack.EMPTY:stall().sample.copy());
        addDataSlots(data);
        refresh();
        if(!inventory.player.level().isClientSide && stall()!=null)StallShops.publish(stall());
    }
    public MarketStallEntity stall() {
        return inventory.player.level().hasChunkAt(pos) && inventory.player.level().getBlockEntity(pos) instanceof MarketStallEntity s ? s : null;
    }
    void refresh() {
        MarketStallEntity s = stall();
        if (s == null || inventory.player.level().isClientSide) return;
        data.set(0, s.price); data.set(1, s.partySize);
        data.set(4,StallSales.busy(s)?1:0);
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
        if (s == null || ACTIONS.isLimited(player.getUUID())) return false;
        if (button == 0) { StallSales.buy(sp, this); return true; }
        if(button==9){refresh();broadcastChanges();StallShops.publish(s);return true;}
        return false;
    }
    public boolean configure(ServerPlayer player,int price,int quantity,boolean perUnit){
        MarketStallEntity s=stall();
        if(!StallAccess.allowed(player) || s==null || ACTIONS.isLimited(player.getUUID()) || !stillValid(player) || !player.getUUID().equals(s.owner) || !StallSales.canEdit(s)
                || (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims") && !StallClaimsCompat.canConfigure(player,pos)))return false;
        long total=perUnit?(long)price*quantity:price;
        if(price<1 || quantity<1 || quantity>64 || total<1 || total>30000){message(player,"invalid_offer");return false;}
        if(s.price!=(int)total || s.partySize!=quantity){s.price=(int)total;s.partySize=quantity;s.setChanged();StallShops.publish(s,true);}
        data.set(5,3);refresh();broadcastChanges();return true;
    }
    public boolean selectItem(ServerPlayer player,int slot){
        var s=stall();
        if(!StallAccess.allowed(player) || s==null || slot<0 || slot>=36 || !stillValid(player) || !player.getUUID().equals(s.owner)
                || !StallSales.canEdit(s) || (net.neoforged.fml.ModList.get().isLoaded("openpartiesandclaims") && !StallClaimsCompat.canConfigure(player,pos)))return false;
        ItemStack item=player.getInventory().getItem(slot),sample=new ItemStack(item.getItem());
        if(item.isEmpty() || !ItemStack.isSameItemSameComponents(sample,item)){message(player,"unsupported");return false;}
        s.sample=sample;s.setChanged();player.level().sendBlockUpdated(pos,s.getBlockState(),s.getBlockState(),3);StallShops.publish(s,true);
        open(player,pos);return true;
    }
    public static void open(ServerPlayer player,BlockPos pos){
        var stall=(MarketStallEntity)player.level().getBlockEntity(pos);
        player.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,p)->new MarketStallMenu(id,inv,pos),Component.translatable("block.crystalmod.market_stall")),buf->{
            buf.writeBlockPos(pos);ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,stall.sample);
        });
    }
    void message(ServerPlayer player, String key) { player.sendSystemMessage(Component.translatable("crystalmod.stall." + key)); }
}
