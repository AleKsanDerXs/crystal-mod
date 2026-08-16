package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: (пере)загрузка каталога рынка из {@code MarketBrowseScreen} — поиск/сортировка/страница. */
public record MarketBrowseRequestPayload(String search, String sort, int page) implements CustomPacketPayload {

    public static final Type<MarketBrowseRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_browse_request"));

    public static final StreamCodec<FriendlyByteBuf, MarketBrowseRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MarketBrowseRequestPayload::search,
                    ByteBufCodecs.STRING_UTF8, MarketBrowseRequestPayload::sort,
                    ByteBufCodecs.VAR_INT, MarketBrowseRequestPayload::page,
                    MarketBrowseRequestPayload::new);

    @Override
    public Type<MarketBrowseRequestPayload> type() {
        return TYPE;
    }
}
