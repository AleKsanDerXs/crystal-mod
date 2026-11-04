package ru.alex.crystalmod;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Fault injection uses only the test harness: no sleeping or latch waiting on the game thread. */
final class StallAsyncCheck {
    private static final CountDownLatch DISK=new CountDownLatch(1);
    private static volatile boolean diskBlocked;
    private static long startTick;
    static void block(MinecraftServer server) throws Exception {
        var parser=StallShops.class.getDeclaredMethod("input",String.class);parser.setAccessible(true);
        try{parser.invoke(null,"{\"nested\":"+"[".repeat(1000)+"0"+"]".repeat(1000)+"}");throw new AssertionError("Nested client JSON accepted");}
        catch(java.lang.reflect.InvocationTargetException e){if(!(e.getCause() instanceof IllegalArgumentException))throw e;}
        CrystalMod.LOGGER.info("STALLASYNC PASS: nested client payload is rejected without recursive parsing");
        var factory=CrystalAPI.class.getDeclaredMethod("pool",String.class,int.class,int.class);factory.setAccessible(true);
        ExecutorService pool=(ExecutorService)factory.invoke(null,"CrystalStall-Test",1,1);
        CountDownLatch release=new CountDownLatch(1);AtomicBoolean ranOnCaller=new AtomicBoolean();
        pool.execute(()->{try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
        pool.execute(()->{});
        var submit=CrystalAPI.class.getDeclaredMethod("async",Executor.class,Supplier.class);submit.setAccessible(true);
        long start=System.nanoTime();
        CompletableFuture<?> rejected=(CompletableFuture<?>)submit.invoke(null,pool,(Supplier<Object>)()->{ranOnCaller.set(true);return null;});
        if(!rejected.isCompletedExceptionally() || ranOnCaller.get() || System.nanoTime()-start>100_000_000L)throw new AssertionError("Saturated pool blocked the server or ran HTTP work inline");
        release.countDown();pool.shutdown();
        CrystalMod.LOGGER.info("STALLASYNC PASS: saturated HTTP queue rejects without caller execution");
        var io=StallJournal.class.getDeclaredField("IO");io.setAccessible(true);
        ((ExecutorService)io.get(null)).execute(()->{
            if(server.isSameThread())throw new AssertionError("Journal executed on server thread");
            diskBlocked=true;try{DISK.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
        var probe=new MarketStallEntity(new net.minecraft.core.BlockPos(0,-59,0),CrystalItems.MARKET_STALL.get().defaultBlockState());
        probe.setLevel(server.overworld());probe.journalLoaded=false;
        long readStart=System.nanoTime();if(StallSales.canEdit(probe))throw new AssertionError("Unloaded journal was editable");
        if(System.nanoTime()-readStart>100_000_000L)throw new AssertionError("Journal load blocked server");
        int queued=((ThreadPoolExecutor)io.get(null)).getQueue().size();
        for(int i=0;i<100;i++)if(StallSales.canEdit(probe))throw new AssertionError("Pending journal unexpectedly ready");
        if(((ThreadPoolExecutor)io.get(null)).getQueue().size()!=queued)throw new AssertionError("Repeated edit checks enqueued repeated disk reads");
        CrystalMod.LOGGER.info("STALLASYNC PASS: journal load and repeated edit checks are non-blocking and coalesced");
        startTick=server.overworld().getGameTime();
    }
    static void release(ServerPlayer p) {
        try{
            if(!diskBlocked || p.serverLevel().getGameTime()-startTick<10)throw new AssertionError("Server did not keep ticking during stalled journal I/O");
            if(!(p.containerMenu instanceof MarketStallMenu m) || m.data.get(4)!=1 || m.stall().operation==null || !m.stall().operation.phase.equals("request"))throw new AssertionError("Purchase escaped its durable request barrier");
            var reloaded=new MarketStallEntity(m.pos,m.stall().getBlockState());reloaded.setLevel(p.serverLevel());reloaded.stallId=m.stall().stallId;reloaded.journalLoaded=false;
            StallSales.prepare(reloaded);
            if(reloaded.journalLoading || reloaded.journalLoaded)throw new AssertionError("Reload cached an unfinished transaction from disk");
            CrystalMod.LOGGER.info("STALLASYNC PASS: reloaded block defers its journal read until the active sale finishes");
            CrystalMod.LOGGER.info("STALLASYNC PASS: game kept ticking while purchase waited for blocked journal worker");
        }finally{DISK.countDown();}
    }
}
