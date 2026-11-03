package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Numbers are suggestions only; the active server menu validates owner, distance and totals. */
public record StallConfigurePayload(int containerId,int price,int quantity,boolean perUnit) implements CustomPacketPayload {
    public static final Type<StallConfigurePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod","stall_configure"));
    public static final StreamCodec<FriendlyByteBuf,StallConfigurePayload> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_INT,StallConfigurePayload::containerId,
            ByteBufCodecs.VAR_INT,StallConfigurePayload::price,
            ByteBufCodecs.VAR_INT,StallConfigurePayload::quantity,
            ByteBufCodecs.BOOL,StallConfigurePayload::perUnit,StallConfigurePayload::new);
    @Override public Type<StallConfigurePayload> type(){return TYPE;}
}
