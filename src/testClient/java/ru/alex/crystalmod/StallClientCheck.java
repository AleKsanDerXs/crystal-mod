package ru.alex.crystalmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import xaero.pac.common.server.api.OpenPACServerAPI;
import java.util.UUID;

@EventBusSubscriber(modid="crystalmod", value=Dist.CLIENT)
public final class StallClientCheck {
    private static int ticks, worldTicks;
    private static boolean requested, tested;
    private static volatile boolean ready;
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
        CrystalMod.LOGGER.info("STALLCHECK PASS: {}", name);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("crystalmod.stallTest")) return;
        var mc = Minecraft.getInstance();
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (++ticks > 100 && mc.getOverlay() == null && !requested) {
                requested=true;
                var settings=new LevelSettings("StallCheck"+System.currentTimeMillis(),net.minecraft.world.level.GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(settings.levelName(),settings,new WorldOptions(0L,false,false),
                    access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            }
            return;
        }
        if (mc.getSingleplayerServer()==null || mc.player==null) return;
        mc.setScreen(null);
        if (!tested) {
            tested=true;
            var server=mc.getSingleplayerServer();
            server.execute(()->{
                try {
                    var player=server.getPlayerList().getPlayers().get(0);
                    var level=player.serverLevel();
                    var pos=new BlockPos(0,-59,0);
                    level.setBlockAndUpdate(pos.below(),Blocks.BARREL.defaultBlockState());
                    level.setBlockAndUpdate(pos,CrystalItems.MARKET_STALL.get().defaultBlockState());
                    var block=CrystalItems.MARKET_STALL.get();
                    var state=level.getBlockState(pos);
                    block.setPlacedBy(level,pos,state,player,new ItemStack(CrystalItems.MARKET_STALL_ITEM.get()));
                    var stall=(MarketStallEntity)level.getBlockEntity(pos);
                    check(player.getUUID().equals(stall.owner),"owner assigned at placement");
                    check(block.getTicker(level,state,CrystalItems.MARKET_STALL_ENTITY.get())==null,"no block entity ticker");
                    check(!tschipp.carryon.common.config.ListHandler.isPermitted(block),"Carry On refuses stall");
                    player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.DIAMOND));
                    var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.UP,pos,false);
                    block.useWithoutItem(state,level,pos,player,hit);
                    check(stall.sample.is(Items.DIAMOND),"owner selects sample without Shift");
                    player.setShiftKeyDown(true);
                    player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.EMERALD));
                    block.useWithoutItem(state,level,pos,player,hit);
                    check(stall.sample.is(Items.DIAMOND),"Shift does not change sample");
                    player.setShiftKeyDown(false);
                    var api=OpenPACServerAPI.get(server);
                    var dim=level.dimension().location();
                    UUID other=UUID.randomUUID();
                    api.getServerClaimsManager().claim(dim,other,0,0,0,false);
                    // Use a foreign claim and an ordinary survival player, with no admin bypass.
                    player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    var protection=api.getChunkProtection();
                    check(protection.onEntityPlaceBlock(player,level,pos),"foreign claim still blocks placement");

                    check(protection.onBlockInteraction(player,InteractionHand.MAIN_HAND,ItemStack.EMPTY,level,pos.below(),Direction.UP,false,false,true),"foreign claim still protects stock barrel");
                    check(net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level,pos,state,player)).isCanceled(),
                            "foreign claim still blocks breaking stall");
                    var use = net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                            new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,pos,hit));
                    check(use.isCanceled() && use.getCancellationResult()==InteractionResult.SUCCESS,
                            "real right-click event handles public stall use in foreign claim");
                    stall.owner=other;
                    block.useWithoutItem(state,level,pos,player,hit);
                    check(stall.sample.is(Items.DIAMOND),"visitor cannot change sample");
                    player.setYRot(180); player.setXRot(12);
                    level.setBlockAndUpdate(new BlockPos(0,-60,3),Blocks.STONE.defaultBlockState());
                    player.teleportTo(0.5,-59,3.5);
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    ready=true;
                    CrystalMod.LOGGER.info("STALLCHECK RESULT: PASS");
                } catch(Throwable ex) { CrystalMod.LOGGER.error("STALLCHECK RESULT: FAIL",ex); mc.execute(mc::stop); }
            });
        }
        if (ready) { mc.player.setYRot(180); mc.player.setXRot(10); }
        if (ready && ++worldTicks==100) {
            Screenshot.grab(mc.gameDirectory,"stall-check.png",mc.getMainRenderTarget(),msg->CrystalMod.LOGGER.info("STALLCHECK screenshot: {}",msg.getString()));
        }
        if (ready && worldTicks>130) mc.stop();
    }
}
