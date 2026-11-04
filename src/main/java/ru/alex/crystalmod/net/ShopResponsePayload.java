package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopResponsePayload(int containerId,int requestId,String action,String json) implements CustomPacketPayload {
    public static final Type<ShopResponsePayload> TYPE=new Type<>(ResourceLocation.parse("crystalmod:shop_response"));
    public static final StreamCodec<FriendlyByteBuf,ShopResponsePayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.containerId);b.writeVarInt(p.requestId);b.writeUtf(p.action,24);b.writeUtf(p.json,131072);
    },b->new ShopResponsePayload(b.readVarInt(),b.readVarInt(),b.readUtf(24),b.readUtf(131072)));
    @Override public Type<ShopResponsePayload> type(){return TYPE;}
}
