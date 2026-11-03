package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: открыть группу — все активные лоты одного предмета. Ответ приходит как {@link MarketBrowseResponsePayload}. */
public record MarketItemLotsRequestPayload(String registryId) implements CustomPacketPayload {

    public static final Type<MarketItemLotsRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_item_lots_request"));

    public static final StreamCodec<FriendlyByteBuf, MarketItemLotsRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MarketItemLotsRequestPayload::registryId,
                    MarketItemLotsRequestPayload::new);

    @Override
    public Type<MarketItemLotsRequestPayload> type() {
        return TYPE;
    }
}
