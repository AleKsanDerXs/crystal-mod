package ru.alex.crystalmod;

import com.google.gson.Gson;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** One durable operation per stall; an uncertain item delivery always requires review. */
final class StallJournal {
    private static final Gson JSON = new Gson();
    static final class Operation {
        String txId, buyer, seller, buyerId, itemId, dimension, phase = "request";
        int x,y,z;
        int quantity, price;
    }
    private static Path path(MinecraftServer server, MarketStallEntity stall) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data/crystal-stall-transactions/" + stall.stallId + ".json");
    }
    static Operation read(MinecraftServer server, MarketStallEntity stall) throws java.io.IOException {
        Path file = path(server, stall);
        if (!Files.exists(file)) return null;
        Operation op = JSON.fromJson(Files.readString(file), Operation.class);
        if (op == null || op.txId == null || op.phase == null) throw new java.io.IOException("Invalid stall journal");
        return op;
    }
    static void write(MinecraftServer server, MarketStallEntity stall, Operation op) throws java.io.IOException {
        Path file = path(server, stall);
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(JSON.toJson(op));
            while (bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }
}
