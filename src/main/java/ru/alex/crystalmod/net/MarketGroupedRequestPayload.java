package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: сгруппированный по предмету каталог (вид по умолчанию в {@code MarketBrowseScreen}) — поиск/сортировка/страница. */
public record MarketGroupedRequestPayload(String search, String sort, int page) implements CustomPacketPayload {

    public static final Type<MarketGroupedRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_grouped_request"));

    public static final StreamCodec<FriendlyByteBuf, MarketGroupedRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MarketGroupedRequestPayload::search,
                    ByteBufCodecs.STRING_UTF8, MarketGroupedRequestPayload::sort,
                    ByteBufCodecs.VAR_INT, MarketGroupedRequestPayload::page,
                    MarketGroupedRequestPayload::new);

    @Override
    public Type<MarketGroupedRequestPayload> type() {
        return TYPE;
    }
}
