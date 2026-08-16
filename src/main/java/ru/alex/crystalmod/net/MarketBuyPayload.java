package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: кнопка «Купить» на строке лота в {@code MarketBrowseScreen}. */
public record MarketBuyPayload(int listingId, int quantity) implements CustomPacketPayload {

    public static final Type<MarketBuyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_buy"));

    public static final StreamCodec<FriendlyByteBuf, MarketBuyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MarketBuyPayload::listingId,
                    ByteBufCodecs.VAR_INT, MarketBuyPayload::quantity,
                    MarketBuyPayload::new);

    @Override
    public Type<MarketBuyPayload> type() {
        return TYPE;
    }
}
