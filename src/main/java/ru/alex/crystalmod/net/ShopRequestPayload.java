package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShopRequestPayload(int containerId,int requestId,String action,String json) implements CustomPacketPayload {
    public static final Type<ShopRequestPayload> TYPE=new Type<>(ResourceLocation.parse("crystalmod:shop_request"));
    public static final StreamCodec<FriendlyByteBuf,ShopRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{
        b.writeVarInt(p.containerId);b.writeVarInt(p.requestId);b.writeUtf(p.action,24);b.writeUtf(p.json,8192);
    },b->new ShopRequestPayload(b.readVarInt(),b.readVarInt(),b.readUtf(24),b.readUtf(8192)));
    @Override public Type<ShopRequestPayload> type(){return TYPE;}
}
