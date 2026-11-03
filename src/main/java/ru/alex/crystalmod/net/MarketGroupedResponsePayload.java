package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Сервер к клиенту: ответ на {@link MarketGroupedRequestPayload}. Строка rows() — один предмет, поля через "|":
 * registryId|minPrice|totalQty|listingsCount. Строка "!error" вместо списка — сайт недоступен (не пустой каталог).
 */
public record MarketGroupedResponsePayload(List<String> rows, int total, int page, int perPage) implements CustomPacketPayload {

    public static final Type<MarketGroupedResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_grouped_response"));

    public static final StreamCodec<FriendlyByteBuf, MarketGroupedResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MarketGroupedResponsePayload::rows,
                    ByteBufCodecs.VAR_INT, MarketGroupedResponsePayload::total,
                    ByteBufCodecs.VAR_INT, MarketGroupedResponsePayload::page,
                    ByteBufCodecs.VAR_INT, MarketGroupedResponsePayload::perPage,
                    MarketGroupedResponsePayload::new);

    @Override
    public Type<MarketGroupedResponsePayload> type() {
        return TYPE;
    }
}
