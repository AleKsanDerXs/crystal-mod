package ru.alex.crystalmod;

import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in, release-excluded end-to-end purchase driven through vanilla menu packets. */
@EventBusSubscriber(modid="crystalmod",value=Dist.CLIENT)
public final class StallSaleClientCheck {
    private static int ticks, age, doneTicks;
    private static boolean requested, setup, configured, bought, checked;
    private static volatile boolean refunded;
    private static String saleTx, refillTx;
    private static volatile int refillStage;
    private static boolean partyConfigured;
    private static volatile boolean sceneReady, done, failed;
    private static final BlockPos POS=new BlockPos(0,-59,0);
    private static void require(boolean value,String what) {
        if(!value) throw new AssertionError(what);
        CrystalMod.LOGGER.info("STALLSALE PASS: {}",what);
    }
    private static void shot(Minecraft mc,String name) {
        Screenshot.grab(mc.gameDirectory,name+".png",mc.getMainRenderTarget(),m->CrystalMod.LOGGER.info("STALLSALE screenshot {}",name));
    }
    private static void click(Minecraft mc,String key){
        String label=Component.translatable(key).getString();
        for(var child:mc.screen.children()) if(child instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals(label)){b.onPress();return;}
        throw new AssertionError("Missing button "+key);
    }
    private static void fields(Minecraft mc,String price,String quantity){
        var fields=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).toList();
        fields.get(0).setValue(price);fields.get(1).setValue(quantity);
    }
    private interface CheckedAction { void run() throws Exception; }
    private static void server(Minecraft mc,CheckedAction action) {
        mc.getSingleplayerServer().execute(()->{
            try { action.run(); }
            catch(Throwable e) { failed=true;CrystalMod.LOGGER.error("STALLSALE RESULT: FAIL",e);mc.execute(mc::stop); }
        });
    }
    private static boolean refilledDue(){return refillStage>=2 && refillStage<6 && age%10==0;}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("crystalmod.stallSaleTest")) return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        if(mc.level==null) {
            if(++ticks>100 && mc.getOverlay()==null && !requested) {
                requested=true;
                var settings=new LevelSettings("StallSales"+System.currentTimeMillis(),GameType.CREATIVE,false,
                        Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(),settings,new WorldOptions(0L,false,false),
                        access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            return;
        }
        if(mc.player==null || mc.getSingleplayerServer()==null || failed) return;
        mc.player.setYRot(StallCreateCheck.ENABLED && refillStage==6?145:180);mc.player.setXRot(StallCreateCheck.ENABLED && refillStage==6?25:10);
        if(!setup) {
            setup=true;mc.setScreen(null);
            server(mc,()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                var level=player.serverLevel();
                level.setBlockAndUpdate(POS.below(),Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(POS,CrystalItems.MARKET_STALL.get().defaultBlockState());
                var block=CrystalItems.MARKET_STALL.get();var state=level.getBlockState(POS);
                block.setPlacedBy(level,POS,state,player,new ItemStack(CrystalItems.MARKET_STALL_ITEM.get()));
                player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.IRON_INGOT));
                var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(POS),Direction.UP,POS,false);
                block.useWithoutItem(state,level,POS,player,hit);
                ItemStack custom=new ItemStack(Items.DIAMOND);
                custom.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal("Do not strip my data"));
                player.setItemInHand(InteractionHand.OFF_HAND,custom);
                block.useWithoutItem(state,level,POS,player,hit);
                require(((MarketStallEntity)level.getBlockEntity(POS)).sample.is(Items.IRON_INGOT),"custom item data is rejected instead of silently stripped");
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                ((net.minecraft.world.Container)level.getBlockEntity(POS.below())).setItem(0,new ItemStack(Items.IRON_INGOT,64));
                level.setBlockAndUpdate(new BlockPos(0,-60,3),Blocks.STONE.defaultBlockState());
                player.setYRot(180);player.setXRot(10);player.teleportTo(0.5,-59,3.5);
                level.setDayTime(6000);
                require(block.getTicker(level,state,CrystalItems.MARKET_STALL_ENTITY.get())==null,"shop integration adds no block ticker");
                require(!tschipp.carryon.common.config.ListHandler.isPermitted(block),"Carry On still refuses the stall");
                var commandPos=POS.east(2);
                level.setBlockAndUpdate(commandPos.below(),Blocks.BARREL.defaultBlockState());
                level.setBlockAndUpdate(commandPos,state);block.setPlacedBy(level,commandPos,state,player,new ItemStack(CrystalItems.MARKET_STALL_ITEM.get()));
                var commandStall=(MarketStallEntity)level.getBlockEntity(commandPos);commandStall.sample=new ItemStack(Items.COPPER_INGOT);commandStall.setChanged();
                player.teleportTo(2.5,-56,0.5);player.setXRot(90);player.setYRot(180);
                CrystalMod.LOGGER.info("STALLSALE AIM eye={} view={} hit={}",player.getEyePosition(),player.getViewVector(1),player.pick(6,1,false).getLocation());
                var commands=player.getServer().getCommands();
                require(commands.getDispatcher().execute("crystal shop apply \"Harness Workshop "+System.currentTimeMillis()+"\" Create components",player.createCommandSourceStack())==1,"shop registration command accepts a quoted name");
                require(commands.getDispatcher().execute("crystal shop list",player.createCommandSourceStack())==1,"shop list command dispatches");
                require(commands.getDispatcher().execute("crystal shop link 1",player.createCommandSourceStack())==1,"shop link command targets the owner's configured stall");
                CrystalMod.LOGGER.info("STALLSALE COMMAND STALL {}",commandStall.stallId);
                player.teleportTo(0.5,-59,3.5);player.setXRot(10);
                sceneReady=true;
            });
        }
        if(!sceneReady) return;
        age++;
        if(age==20) mc.options.hideGui=true;
        if(age==40) {shot(mc,"market-01-counter");mc.options.hideGui=false;}
        if(age==60) server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,x)->new MarketStallMenu(id,inv,POS),Component.translatable("block.crystalmod.market_stall")),POS);
        });
        if(!configured && age>80 && mc.player.containerMenu instanceof MarketStallMenu) {
            configured=true;
            fields(mc,"24","8");click(mc,"crystalmod.stall.per_party");
            click(mc,"crystalmod.stall.save");
        }
        if(age==95){shot(mc,"market-06-unit-price");server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var menu=(MarketStallMenu)p.containerMenu;var s=menu.stall();
            require(s.price==24 && s.partySize==8,"numeric unit price 3 times 8 saves party total 24 through real screen and payload");
            require(!menu.configure(p,Integer.MAX_VALUE,64,true) && s.price==24,"overflowing unit-price payload is rejected");
            require(!menu.configure(p,10,0,false) && s.partySize==8,"zero-sized party is rejected");
        });}
        if(age>=100 && !partyConfigured && mc.screen instanceof ru.alex.crystalmod.client.MarketStallScreen){
            partyConfigured=true;click(mc,"crystalmod.stall.per_unit");fields(mc,"10","8");click(mc,"crystalmod.stall.save");
        }
        if(age==120) {shot(mc,"market-02-owner");server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            var s=(MarketStallEntity)p.level().getBlockEntity(POS);
            require(s.price==10 && s.partySize==8,"owner enters party of 8 for 10 through real numeric fields");
            p.closeContainer();
            s.owner=UUID.fromString("2429cce2-fd10-4367-abaa-f14545bac013");s.ownerName="MarketSeller";s.setChanged();
            xaero.pac.common.server.api.OpenPACServerAPI.get(p.getServer()).getServerClaimsManager()
                    .claim(p.level().dimension().location(),s.owner,0,0,0,false);
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,x)->new MarketStallMenu(id,inv,POS),Component.translatable("block.crystalmod.market_stall")),POS);
        });}
        if(age==135) server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            for(int i=0;i<36;i++) p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var menu=(MarketStallMenu)p.containerMenu;
            menu.clickMenuButton(p,0);
            require(StallJournal.read(p.getServer(),menu.stall())==null,"full inventory rejects purchase before reserving money");
            p.getInventory().clearContent();
            var stall=menu.stall();stall.price=11;
            menu.clickMenuButton(p,0);
            require(StallJournal.read(p.getServer(),stall)==null,"changed price rejects stale offer before reserving money");
            stall.price=10;
            p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,x)->new MarketStallMenu(id,inv,POS),Component.translatable("block.crystalmod.market_stall")),POS);
        });
        if(age==160) shot(mc,"market-03-buy");
        if(!bought && age>=180 && mc.player.containerMenu instanceof MarketStallMenu m && m.data.get(3)==0) {
            bought=true;mc.gameMode.handleInventoryButtonClick(m.containerId,0);
            mc.gameMode.handleInventoryButtonClick(m.containerId,0);
        }
        if(bought && !checked && age>200 && age%20==0) server(mc,()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
            var s=(MarketStallEntity)p.level().getBlockEntity(POS);
            var op=StallJournal.read(p.getServer(),s);
            if(op==null || !op.phase.equals("complete")) return;
            int count=0;
            for(int i=0;i<36;i++) if(p.getInventory().getItem(i).is(Items.IRON_INGOT)) count+=p.getInventory().getItem(i).getCount();
            require(count==8,"buyer receives exactly 8 iron ingots");
            require(MarketStallMenu.stock(s)==56,"physical barrel stock falls from 64 to 56");
            require(op.quantity==8 && op.price==10,"durable transaction matches offer");
            saleTx=op.txId;checked=true;done=true;
            CrystalMod.LOGGER.info("STALLSALE RESULT: PASS tx={}",op.txId);
        });
        if(done) {
            doneTicks++;
            if(doneTicks==20) {
                shot(mc,"market-04-purchased");
                server(mc,()->{
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                    p.closeContainer();p.setGameMode(GameType.SURVIVAL);
                });
            }
            if(doneTicks==35) mc.setScreen(new InventoryScreen(mc.player));
            if(doneTicks==55) shot(mc,"market-05-inventory");
            if(doneTicks==65) server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,x)->new MarketStallMenu(id,inv,POS),Component.translatable("block.crystalmod.market_stall")),POS);
                xaero.pac.common.server.api.OpenPACServerAPI.get(p.getServer()).getServerClaimsManager()
                        .unclaim(p.level().dimension().location(),0,0);
                ((MarketStallMenu)p.containerMenu).clickMenuButton(p,0);
                require(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                        new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(p.serverLevel(),POS,p.level().getBlockState(POS),p)).isCanceled(),
                        "pending transaction prevents breaking the stall even outside a claim");
                p.closeContainer();
            });
            if(doneTicks>85 && doneTicks%20==0 && !refunded) server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                var s=(MarketStallEntity)p.level().getBlockEntity(POS);
                var op=StallJournal.read(p.getServer(),s);
                if(op==null || op.txId.equals(saleTx) || !op.phase.equals("complete")) return;
                require(MarketStallMenu.stock(s)==56,"closing menu before payment response refunds without taking stock");
                int count=0;
                for(int i=0;i<36;i++) if(p.getInventory().getItem(i).is(Items.IRON_INGOT)) count+=p.getInventory().getItem(i).getCount();
                require(count==8,"cancelled second purchase gives no extra items");
                refunded=true;CrystalMod.LOGGER.info("STALLSALE REFUND PASS tx={}",op.txId);
            });
            if(refunded && refillStage==0) {refillStage=1;server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();var s=(MarketStallEntity)level.getBlockEntity(POS);
                ((net.minecraft.world.Container)level.getBlockEntity(POS.below())).clearContent();
                p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inv,x)->new MarketStallMenu(id,inv,POS),Component.literal("Refill check")),POS);
                var previous=StallJournal.read(p.getServer(),s).txId;
                ((MarketStallMenu)p.containerMenu).clickMenuButton(p,0);
                require(StallJournal.read(p.getServer(),s).txId.equals(previous),"empty stock rejects purchase without starting payment");
                if(StallCreateCheck.ENABLED)StallCreateCheck.install(level,POS);
                else{
                    var feed=POS.below().east();
                    level.setBlockAndUpdate(feed,Blocks.HOPPER.defaultBlockState().setValue(net.minecraft.world.level.block.HopperBlock.FACING,Direction.WEST));
                    ((net.minecraft.world.Container)level.getBlockEntity(feed)).setItem(0,new ItemStack(Items.IRON_INGOT,16));
                }
                refillTx=previous;refillStage=2;
            });}
            if(refilledDue())server(mc,()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var s=(MarketStallEntity)p.level().getBlockEntity(POS);var menu=(MarketStallMenu)p.containerMenu;
                var op=StallJournal.read(p.getServer(),s);
                if(StallCreateCheck.ENABLED)StallCreateCheck.observe(s,menu);
                if(refillStage==3 && op.phase.equals("complete") && !op.txId.equals(refillTx)){
                    refillTx=op.txId;refillStage=4;
                }
                if((refillStage==2 || refillStage==4) && MarketStallMenu.stock(s)>=8){
                    menu.clickMenuButton(p,9);require(menu.data.get(2)>=8,"refresh sees automatic stock replenishment");
                    if(StallCreateCheck.ENABLED)StallCreateCheck.beforeBuy(s);
                    menu.clickMenuButton(p,0);refillStage=refillStage==2?3:5;
                }else if(refillStage==5 && op.phase.equals("complete") && !op.txId.equals(refillTx)){
                    int count=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.IRON_INGOT))count+=p.getInventory().getItem(i).getCount();
                    require(count==24 && MarketStallMenu.stock(s)==0,"two automatically replenished batches deliver exactly 16 additional ingots");
                    if(StallCreateCheck.ENABLED)StallCreateCheck.drained(p.serverLevel());
                    else require(((net.minecraft.world.Container)p.level().getBlockEntity(POS.below().east())).isEmpty(),"hopper really transferred the stock into the barrel");
                    refillStage=6;CrystalMod.LOGGER.info("STALLSALE REFILL PASS tx={}",op.txId);
                }
            });
            if(refillStage==6){if(StallCreateCheck.ENABLED)StallCreateCheck.tick(mc);else mc.stop();}
        }
        if(age>(StallCreateCheck.ENABLED?3600:1200)) {failed=true;CrystalMod.LOGGER.error("STALLSALE RESULT: FAIL timeout");mc.stop();}
    }
}
