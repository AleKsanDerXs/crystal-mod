package ru.alex.crystalmod;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class MarketStallEntity extends BlockEntity {
    UUID owner;
    String ownerName = "";
    UUID stallId = UUID.randomUUID();
    int price = 1, partySize = 1;
    long lastCatalogPublish,catalogRevision;
    boolean journalLoaded=true,journalLoading,journalError;
    StallJournal.Operation operation;
    public ItemStack sample = ItemStack.EMPTY;
    public MarketStallEntity(BlockPos pos, BlockState state) { super(CrystalItems.MARKET_STALL_ENTITY.get(), pos, state); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("CatalogRevision",catalogRevision);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putString("OwnerName",ownerName); tag.putUUID("StallId",stallId);
        tag.putInt("Price",price); tag.putInt("PartySize",partySize);
        if (!sample.isEmpty()) tag.put("Sample", sample.save(registries));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        journalLoaded=false;journalLoading=false;journalError=false;operation=null;
        catalogRevision=tag.getLong("CatalogRevision");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        sample = ItemStack.parseOptional(registries, tag.getCompound("Sample"));
        ownerName=tag.getString("OwnerName");
        if(tag.hasUUID("StallId")) stallId=tag.getUUID("StallId");
        price=Math.clamp(tag.getInt("Price"),1,30000); partySize=Math.clamp(tag.getInt("PartySize"),1,64);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // The client needs only the cosmetic sample, not the owner or the stock container.
        if (!sample.isEmpty()) tag.put("Sample", sample.save(registries));
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
