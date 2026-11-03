package ru.alex.crystalmod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Money is held remotely; stock remains physical until the validated delivery step. */
final class StallSales {
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof MarketStallEntity stall && !canEdit(stall)) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.translatable("crystalmod.stall.busy"));
        }
    }
    static boolean canEdit(MarketStallEntity stall) {
        if (ACTIVE.contains(stall.stallId)) return false;
        try {
            var op = StallJournal.read(stall.getLevel().getServer(), stall);
            return op == null || "complete".equals(op.phase);
        } catch (Exception e) { return false; }
    }
    static int capacity(ServerPlayer player, ItemStack sample) {
        int result = 0;
        for (int i=0; i<36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) result += Math.min(sample.getMaxStackSize(), player.getInventory().getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(slot,sample)) result += Math.max(0,slot.getMaxStackSize()-slot.getCount());
        }
        return result;
    }
    static void buy(ServerPlayer player, MarketStallMenu menu) {
        var stall = menu.stall();
        if (stall == null || !menu.stillValid(player) || !ACTIVE.add(stall.stallId)) return;
        var server = player.getServer();
        try {
            var op = StallJournal.read(server,stall);
            if (op != null && "applying".equals(op.phase)) {
                menu.message(player,"review"); ACTIVE.remove(stall.stallId); return;
            }
            if (op == null || "complete".equals(op.phase)) {
                if (menu.data.get(0)!=stall.price || menu.data.get(1)!=stall.partySize
                        || !ItemStack.isSameItemSameComponents(menu.offeredSample,stall.sample)) {
                    player.closeContainer(); menu.message(player,"changed"); ACTIVE.remove(stall.stallId); return;
                }
                if (player.getUUID().equals(stall.owner)) { menu.message(player,"own"); ACTIVE.remove(stall.stallId); return; }
                if (stall.owner == null || stall.ownerName.isEmpty() || stall.sample.isEmpty() || MarketStallMenu.stock(stall)<stall.partySize) {
                    menu.message(player,"no_stock"); ACTIVE.remove(stall.stallId); return;
                }
                if (capacity(player,stall.sample)<stall.partySize) { menu.message(player,"full"); ACTIVE.remove(stall.stallId); return; }
                op = new StallJournal.Operation();
                op.txId=UUID.randomUUID().toString(); op.buyer=player.getGameProfile().getName(); op.seller=stall.ownerName;
                op.buyerId=player.getUUID().toString(); op.quantity=stall.partySize; op.price=stall.price;
                op.itemId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stall.sample.getItem()).toString();
                op.dimension=player.level().dimension().location().toString();
                op.x=menu.pos.getX();op.y=menu.pos.getY();op.z=menu.pos.getZ();
                StallJournal.write(server,stall,op);
            }
            if ("request".equals(op.phase) && !player.getUUID().toString().equals(op.buyerId)) {
                menu.message(player,"busy"); ACTIVE.remove(stall.stallId); return;
            }
            menu.data.set(5,0); menu.data.set(4,1); menu.broadcastChanges();
            run(player,menu,stall,op);
        } catch (Exception e) {
            ACTIVE.remove(stall.stallId); menu.message(player,"review");
            CrystalMod.LOGGER.error("Stall journal failed; sale stopped",e);
        }
    }
    private static void run(ServerPlayer player, MarketStallMenu menu, MarketStallEntity stall, StallJournal.Operation op) {
        String action = switch(op.phase) { case "delivered" -> "commit"; case "refunding" -> "refund"; default -> "reserve"; };
        CrystalAPI.stallPaymentAsync(action,stall.stallId.toString(),op).whenComplete((result,error) -> player.getServer().execute(() -> {
            try {
                if (error != null) { menu.message(player,"retry"); return; }
                if (!result.get("ok").getAsBoolean()) {
                    String reason=result.has("error")?result.get("error").getAsString():"unknown";
                    if (action.equals("reserve") && Set.of("insufficient_funds","own_stall","player_not_found").contains(reason)) {
                        op.phase="complete"; StallJournal.write(player.getServer(),stall,op);
                    }
                    menu.message(player,reason.equals("insufficient_funds")?"funds":"review"); return;
                }
                String status=result.get("status").getAsString();
                if (action.equals("reserve")) {
                    if (!status.equals("reserved")) { menu.message(player,"review"); return; }
                    // A closed menu, unloaded/replaced block, changed stock or full inventory cancels before delivery.
                    if (player.getServer().getPlayerList().getPlayer(player.getUUID())!=player || player.containerMenu!=menu
                            || !menu.stillValid(player) || menu.stall()!=stall || MarketStallMenu.stock(stall)<op.quantity
                            || capacity(player,stall.sample)<op.quantity) {
                        op.phase="refunding"; StallJournal.write(player.getServer(),stall,op);
                        run(player,menu,stall,op); return;
                    }
                    op.phase="applying"; StallJournal.write(player.getServer(),stall,op);
                    Container stock=MarketStallMenu.container(stall);
                    int remaining=op.quantity;
                    for(int i=0; i<stock.getContainerSize() && remaining>0; i++) {
                        ItemStack item=stock.getItem(i);
                        if(ItemStack.isSameItemSameComponents(item,stall.sample)) {
                            int n=Math.min(remaining,item.getCount()); stock.removeItem(i,n); remaining-=n;
                        }
                    }
                    stock.setChanged();
                    ItemStack goods=stall.sample.copyWithCount(op.quantity);
                    player.getInventory().add(goods);
                    if (!goods.isEmpty() || remaining!=0) throw new IllegalStateException("Unexpected partial delivery; review required");
                    player.getInventory().setChanged();
                    op.phase="delivered"; StallJournal.write(player.getServer(),stall,op);
                    run(player,menu,stall,op); return;
                }
                if (!status.equals(action.equals("commit")?"committed":"refunded")) throw new IllegalStateException("Unexpected payment state");
                op.phase="complete"; StallJournal.write(player.getServer(),stall,op);
                menu.data.set(5,action.equals("commit")?1:2);
                menu.message(player,action.equals("commit")?"bought":"cancelled");
            } catch(Exception e) {
                menu.message(player,"review"); CrystalMod.LOGGER.error("Stall transaction {} needs review",op.txId,e);
            } finally {
                // Chained reserve -> commit/refund keeps the same stall locked until the last response.
                if (!action.equals("reserve") || !(op.phase.equals("delivered") || op.phase.equals("refunding"))) {
                    ACTIVE.remove(stall.stallId); menu.data.set(4,0); menu.refresh(); menu.broadcastChanges();
                    if(menu.stall()==stall)StallShops.publish(stall);
                }
            }
        }));
    }
}
