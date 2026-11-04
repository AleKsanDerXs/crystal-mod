package ru.alex.crystalmod;

import java.nio.file.Files;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Independent file assertions exist only in opt-in tests, never in the release jar. */
final class StallTestJournal {
    static StallJournal.Operation read(MinecraftServer server,MarketStallEntity stall) throws java.io.IOException {
        var file=server.getWorldPath(LevelResource.ROOT).resolve("data/crystal-stall-transactions/"+stall.stallId+".json");
        return Files.exists(file)?new com.google.gson.Gson().fromJson(Files.readString(file),StallJournal.Operation.class):null;
    }
}
