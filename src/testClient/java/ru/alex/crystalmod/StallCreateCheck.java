package ru.alex.crystalmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Real Create transport, enabled only in the opt-in client harness. */
final class StallCreateCheck {
    static final boolean ENABLED=Boolean.getBoolean("crystalmod.stallCreateTest");
    private static BlockPos source,belt,stallPos;
    private static int stage,polls,shotTicks;
    private static int purchaseStock;
    private static String lastTx;
    private static boolean overlap,configured;
    private static volatile boolean finished;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);CrystalMod.LOGGER.info("STALLCREATE PASS: {}",message);}
    private static <T extends Comparable<T>> BlockState prop(BlockState s,Property<T> p,String value){return s.setValue(p,p.getValue(value).orElseThrow());}
    private static BlockState state(String id,String... values){
        var b=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:"+id));
        check(b!=Blocks.AIR,"Create block available: "+id);var s=b.defaultBlockState();
        for(int i=0;i<values.length;i+=2)s=prop(s,b.getStateDefinition().getProperty(values[i]),values[i+1]);return s;
    }
    static void install(ServerLevel level,BlockPos pos) throws Exception {
        stallPos=pos;belt=pos.below(2).east();source=pos.below().east(7);
        level.setBlockAndUpdate(pos.east(2),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(pos.east(2).below(),Blocks.AIR.defaultBlockState());
        for(int x=1;x<=8;x++)for(int z=-1;z<=1;z++)level.setBlockAndUpdate(pos.below(2).offset(x,0,z),Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(belt,state("shaft","axis","z"));
        level.setBlockAndUpdate(belt.east(5),state("shaft","axis","z"));
        Class.forName("com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem").getMethod("createBelts",Level.class,BlockPos.class,BlockPos.class).invoke(null,level,belt,belt.east(5));
        level.setBlockAndUpdate(source,Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(source.west(),state("brass_belt_funnel","facing","west","shape","pushing"));
        level.setBlockAndUpdate(pos.below().east(),state("andesite_belt_funnel","facing","east","shape","pulling"));
        level.setBlockAndUpdate(belt.east(5).north(),state("creative_motor","facing","south"));
        CrystalMod.LOGGER.info("STALLCREATE LINE stall={} source={} belt={}",pos,source,belt);
    }
    static void beforeBuy(MarketStallEntity s){purchaseStock=MarketStallMenu.stock(s);CrystalMod.LOGGER.info("STALLCREATE BUY stock={}",purchaseStock);}
    static void observe(MarketStallEntity s,MarketStallMenu menu) throws Exception {
        if(!configured){
            var level=s.getLevel();var funnel=level.getBlockEntity(source.west());
            var field=funnel.getClass().getDeclaredField("filtering");field.setAccessible(true);var filter=field.get(funnel);
            check(filter!=null,"brass funnel filtering behaviour initialized");
            filter.getClass().getMethod("setFilter",ItemStack.class).invoke(filter,new ItemStack(Items.IRON_INGOT));
            var settings=Class.forName("com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour$ValueSettings");
            filter.getClass().getMethod("setValueSettings",net.minecraft.world.entity.player.Player.class,settings,boolean.class)
                    .invoke(filter,menu.inventory.player,settings.getConstructor(int.class,int.class).newInstance(0,1),false);
            check((int)funnel.getClass().getMethod("getAmountToExtract").invoke(funnel)==1,"brass funnel is set to extract one item at a time");
            var motor=level.getBlockEntity(belt.east(5).north());var speed=motor.getClass().getField("generatedSpeed").get(motor);
            speed.getClass().getMethod("setValue",int.class).invoke(speed,64);
            ((Container)level.getBlockEntity(source)).setItem(0,new ItemStack(Items.IRON_INGOT,16));configured=true;
        }
        if(menu.data.get(4)!=0 && MarketStallMenu.stock(s)>purchaseStock)overlap=true;
    }
    static void drained(ServerLevel level) throws Exception {
        check(((Container)level.getBlockEntity(source)).isEmpty(),"Create extracted all 16 ingots from source barrel");
        var b=level.getBlockEntity(belt);var facing=b.getClass().getMethod("getMovementFacing").invoke(b);
        check(facing==Direction.WEST,"powered Create belt really moves toward the market barrel");
        check(overlap,"stock increased through Create while an HTTP purchase was pending");
    }
    private static int goods(ServerPlayer p){int n=0;for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(Items.IRON_INGOT))n+=p.getInventory().getItem(i).getCount();return n;}
    static void tick(Minecraft mc){
        if(finished){if(++shotTicks==5)mc.setScreen(null);if(shotTicks==10)mc.options.hideGui=true;
            if(shotTicks==20)Screenshot.grab(mc.gameDirectory,"market-create-line.png",mc.getMainRenderTarget(),m->{});
            if(shotTicks>30)mc.stop();return;}
        if(++polls%10!=0)return;
        mc.getSingleplayerServer().execute(()->{try{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();var s=(MarketStallEntity)level.getBlockEntity(stallPos);
            var menu=(MarketStallMenu)p.containerMenu;var op=StallJournal.read(p.getServer(),s);
            if(stage==0){
                check(goods(p)==24 && MarketStallMenu.stock(s)==0,"16 transported ingots were sold without loss or duplication");
                lastTx=op.txId;((Container)level.getBlockEntity(source)).setItem(0,new ItemStack(Items.IRON_INGOT,8));stage=1;
            }else if(stage==1 && MarketStallMenu.stock(s)>=8){
                menu.clickMenuButton(p,9);menu.clickMenuButton(p,0);
                // Simulate the owner removing the stock container before the delayed reserve response.
                ((Container)level.getBlockEntity(stallPos.below())).clearContent();level.setBlockAndUpdate(stallPos.below(),Blocks.AIR.defaultBlockState());stage=2;
            }else if(stage==2 && op.phase.equals("complete") && !op.txId.equals(lastTx)){
                check(menu.data.get(5)==2 && goods(p)==24,"missing barrel refunds the purchase and gives no goods");lastTx=op.txId;
                level.setBlockAndUpdate(stallPos.below(),Blocks.BARREL.defaultBlockState());
                // Return the removed eight test items to the source and let Create transport them again.
                ((Container)level.getBlockEntity(source)).setItem(0,new ItemStack(Items.IRON_INGOT,8));stage=3;
            }else if(stage==3 && MarketStallMenu.stock(s)>=8){menu.clickMenuButton(p,9);menu.clickMenuButton(p,0);stage=4;}
            else if(stage==4 && op.phase.equals("complete") && !op.txId.equals(lastTx)){
                check(menu.data.get(5)==1 && goods(p)==32 && MarketStallMenu.stock(s)==0,"restored barrel resumes automatic sales on the same stall");
                check(((Container)level.getBlockEntity(source)).isEmpty(),"restarted Create line has no stranded source goods");
                p.closeContainer();p.teleportTo(5.5,-59,6.5);
                finished=true;CrystalMod.LOGGER.info("STALLCREATE RESULT: PASS stall={} final_tx={}",s.stallId,op.txId);
            }
            if(polls>2400)throw new AssertionError("Create follow-up timed out at stage "+stage);
        }catch(Throwable e){CrystalMod.LOGGER.error("STALLCREATE RESULT: FAIL",e);mc.execute(mc::stop);}});
    }
}
