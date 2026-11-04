package ru.alex.crystalmod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** World changes stay on the server thread; journal and HTTP completions never block it. */
final class StallSales {
    private static final Set<UUID> ACTIVE=ConcurrentHashMap.newKeySet();
    static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){ACTIVE.clear();}
    static void onBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event){
        if(!event.getLevel().isClientSide() && event.getLevel().getBlockEntity(event.getPos()) instanceof MarketStallEntity s && !canEdit(s)){
            event.setCanceled(true);event.getPlayer().sendSystemMessage(net.minecraft.network.chat.Component.translatable("crystalmod.stall.busy"));
        }
    }
    static void prepare(MarketStallEntity s){
        if(s.journalLoaded || s.journalLoading || ACTIVE.contains(s.stallId) || s.getLevel()==null || s.getLevel().isClientSide)return;
        s.journalLoading=true;MinecraftServer server=s.getLevel().getServer();
        StallJournal.readAsync(server,s).whenComplete((op,error)->onServer(server,()->{
            s.journalLoading=false;
            if(ACTIVE.contains(s.stallId))return;
            s.journalLoaded=true;s.journalError=error!=null;s.operation=op;
            if(error!=null)CrystalMod.LOGGER.error("Stall journal load failed {}",s.stallId,error);
            for(var p:server.getPlayerList().getPlayers())if(p.containerMenu instanceof MarketStallMenu m && m.stall()==s){m.refresh();m.broadcastChanges();}
        }));
    }
    static boolean busy(MarketStallEntity s){prepare(s);return !s.journalLoaded || ACTIVE.contains(s.stallId);}
    static boolean canEdit(MarketStallEntity s){prepare(s);return s.journalLoaded && !s.journalError && !ACTIVE.contains(s.stallId) && (s.operation==null || s.operation.phase.equals("complete"));}
    static int capacity(ServerPlayer p,ItemStack sample){
        int n=0;for(int i=0;i<36;i++){ItemStack item=p.getInventory().getItem(i);
            if(item.isEmpty())n+=Math.min(sample.getMaxStackSize(),p.getInventory().getMaxStackSize());
            else if(ItemStack.isSameItemSameComponents(item,sample))n+=Math.max(0,item.getMaxStackSize()-item.getCount());
        }return n;
    }
    static void buy(ServerPlayer p,MarketStallMenu m){
        MarketStallEntity s=m.stall();if(!StallAccess.allowed(p) || s==null || !m.stillValid(p))return;prepare(s);
        if(!s.journalLoaded){m.message(p,"loading");return;}
        if(s.journalError || s.operation!=null && s.operation.phase.equals("applying")){m.message(p,"review");return;}
        if(!ACTIVE.add(s.stallId))return;
        try{
            var op=s.operation;
            if(op==null || op.phase.equals("complete")){
                if(m.data.get(0)!=s.price || m.data.get(1)!=s.partySize || !ItemStack.isSameItemSameComponents(m.offeredSample,s.sample)){
                    p.closeContainer();m.message(p,"changed");release(m,s);return;
                }
                if(p.getUUID().equals(s.owner)){m.message(p,"own");release(m,s);return;}
                if(s.owner==null || s.ownerName.isEmpty() || s.sample.isEmpty() || MarketStallMenu.stock(s)<s.partySize){m.message(p,"no_stock");release(m,s);return;}
                if(capacity(p,s.sample)<s.partySize){m.message(p,"full");release(m,s);return;}
                op=new StallJournal.Operation();op.txId=UUID.randomUUID().toString();op.buyer=p.getGameProfile().getName();op.seller=s.ownerName;
                op.buyerId=p.getUUID().toString();op.quantity=s.partySize;op.price=s.price;
                op.itemId=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.sample.getItem()).toString();op.dimension=p.level().dimension().location().toString();
                op.x=m.pos.getX();op.y=m.pos.getY();op.z=m.pos.getZ();s.operation=op;
                m.data.set(5,0);m.data.set(4,1);m.broadcastChanges();
                var created=op;persist(p,m,s,op,"request",()->request(p,m,s,created));return;
            }
            if(op.phase.equals("request") && !p.getUUID().toString().equals(op.buyerId)){m.message(p,"busy");release(m,s);return;}
            m.data.set(5,0);m.data.set(4,1);m.broadcastChanges();request(p,m,s,op);
        }catch(Exception e){fail(p,m,s,e);}
    }
    private static void onServer(MinecraftServer server,Runnable work){if(!server.isStopped())server.execute(work);}
    private static void persist(ServerPlayer p,MarketStallMenu m,MarketStallEntity s,StallJournal.Operation op,String phase,Runnable next){
        op.phase=phase;
        StallJournal.writeAsync(p.getServer(),s,op).whenComplete((nothing,error)->onServer(p.getServer(),()->{
            if(error!=null){s.journalError=true;fail(p,m,s,error);return;}
            try{next.run();}catch(Exception e){fail(p,m,s,e);}
        }));
    }
    private static boolean validDelivery(ServerPlayer p,MarketStallMenu m,MarketStallEntity s,StallJournal.Operation op){
        return StallAccess.allowed(p) && p.getServer().getPlayerList().getPlayer(p.getUUID())==p && p.containerMenu==m && m.stillValid(p) && m.stall()==s
                && p.getUUID().toString().equals(op.buyerId) && s.ownerName.equals(op.seller) && s.price==op.price && s.partySize==op.quantity
                && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.sample.getItem()).toString().equals(op.itemId)
                && ItemStack.isSameItemSameComponents(m.offeredSample,s.sample) && MarketStallMenu.stock(s)>=op.quantity && capacity(p,s.sample)>=op.quantity;
    }
    private static void refund(ServerPlayer p,MarketStallMenu m,MarketStallEntity s,StallJournal.Operation op){persist(p,m,s,op,"refunding",()->request(p,m,s,op));}
    private static void request(ServerPlayer p,MarketStallMenu m,MarketStallEntity s,StallJournal.Operation op){
        String action=switch(op.phase){case "delivered"->"commit";case "refunding"->"refund";default->"reserve";};
        CrystalAPI.stallPaymentAsync(action,s.stallId.toString(),op).whenComplete((result,error)->onServer(p.getServer(),()->{
            try{
                if(error!=null){m.message(p,"retry");release(m,s);return;}
                if(!result.get("ok").getAsBoolean()){
                    String reason=result.has("error")?result.get("error").getAsString():"unknown";
                    if(action.equals("reserve") && Set.of("insufficient_funds","own_stall","player_not_found").contains(reason)){
                        persist(p,m,s,op,"complete",()->{m.message(p,reason.equals("insufficient_funds")?"funds":"review");release(m,s);});
                    }else{m.message(p,"review");release(m,s);}return;
                }
                String status=result.get("status").getAsString();
                if(action.equals("reserve")){
                    if(!status.equals("reserved")){m.message(p,"review");release(m,s);return;}
                    if(!validDelivery(p,m,s,op)){refund(p,m,s,op);return;}
                    persist(p,m,s,op,"applying",()->{
                        // Stock and menu may change while the durable marker is being flushed.
                        if(!validDelivery(p,m,s,op)){refund(p,m,s,op);return;}
                        Container stock=MarketStallMenu.container(s);int remaining=op.quantity;
                        for(int i=0;i<stock.getContainerSize() && remaining>0;i++){
                            ItemStack item=stock.getItem(i);if(ItemStack.isSameItemSameComponents(item,s.sample)){
                                int n=Math.min(remaining,item.getCount());stock.removeItem(i,n);remaining-=n;
                            }
                        }
                        stock.setChanged();ItemStack goods=s.sample.copyWithCount(op.quantity);p.getInventory().add(goods);
                        if(!goods.isEmpty() || remaining!=0)throw new IllegalStateException("Partial delivery requires review");
                        p.getInventory().setChanged();persist(p,m,s,op,"delivered",()->request(p,m,s,op));
                    });return;
                }
                if(!status.equals(action.equals("commit")?"committed":"refunded"))throw new IllegalStateException("Unexpected payment state");
                persist(p,m,s,op,"complete",()->{m.data.set(5,action.equals("commit")?1:2);m.message(p,action.equals("commit")?"bought":"cancelled");release(m,s);});
            }catch(Exception e){fail(p,m,s,e);}
        }));
    }
    private static void release(MarketStallMenu m,MarketStallEntity s){
        ACTIVE.remove(s.stallId);m.refresh();m.broadcastChanges();
        // A reloaded/moved block must read the final durable state, never cache an in-flight phase.
        if(m.stall()!=s)for(var p:s.getLevel().getServer().getPlayerList().getPlayers())if(p.containerMenu instanceof MarketStallMenu current && current!=m){
            var live=current.stall();if(live!=null && live.stallId.equals(s.stallId)){current.refresh();current.broadcastChanges();}
        }
        if(m.stall()==s)StallShops.publish(s);
    }
    private static void fail(ServerPlayer p,MarketStallMenu m,MarketStallEntity s,Throwable error){
        // Any interrupted delivery stays quarantined by the durable applying phase.
        m.message(p,"review");CrystalMod.LOGGER.error("Stall {} requires review",s.stallId,error);release(m,s);
    }
}
