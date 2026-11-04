package ru.alex.crystalmod;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** All production file access runs on one bounded queue; phases are durable before continuing. */
final class StallJournal {
    private static final Gson JSON=new Gson();
    private static final ExecutorService IO=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(128),r->{
        Thread t=new Thread(r,"CrystalStall-Journal");t.setDaemon(true);return t;
    },new ThreadPoolExecutor.AbortPolicy());
    static final class Operation {
        String txId,buyer,seller,buyerId,itemId,dimension,phase="request";
        int x,y,z,quantity,price;
    }
    private static Path path(MinecraftServer server,MarketStallEntity stall){return server.getWorldPath(LevelResource.ROOT).resolve("data/crystal-stall-transactions/"+stall.stallId+".json");}
    private static Operation readFile(Path file) throws java.io.IOException {
        if(!Files.exists(file))return null;
        try{
            Operation op=JSON.fromJson(Files.readString(file),Operation.class);
            if(op==null || !Set.of("request","applying","delivered","refunding","complete").contains(op.phase)
                    || op.quantity<1 || op.quantity>64 || op.price<1 || op.price>30000
                    || op.buyer==null || op.seller==null || op.itemId==null)throw new IllegalArgumentException("Invalid operation");
            UUID.fromString(op.txId);UUID.fromString(op.buyerId);return op;
        }catch(RuntimeException e){throw new java.io.IOException("Invalid stall journal",e);}
    }
    static CompletableFuture<Operation> readAsync(MinecraftServer server,MarketStallEntity stall){
        Path file=path(server,stall);return submit(()->readFile(file));
    }
    static CompletableFuture<Void> writeAsync(MinecraftServer server,MarketStallEntity stall,Operation op){
        Path file=path(server,stall);String json=JSON.toJson(op);
        return submit(()->{
            Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".tmp");
            try(FileChannel c=FileChannel.open(temp,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING,StandardOpenOption.WRITE)){
                ByteBuffer bytes=StandardCharsets.UTF_8.encode(json);while(bytes.hasRemaining())c.write(bytes);c.force(true);
            }
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);return null;
        });
    }
    private static <T> CompletableFuture<T> submit(Callable<T> work){
        var result=new CompletableFuture<T>();
        try{IO.execute(()->{try{result.complete(work.call());}catch(Exception e){result.completeExceptionally(e);}});}
        catch(RejectedExecutionException e){result.completeExceptionally(e);}return result;
    }
}
