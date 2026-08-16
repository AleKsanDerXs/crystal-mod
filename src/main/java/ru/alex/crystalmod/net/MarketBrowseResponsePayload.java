package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Сервер к клиенту: ответ на MarketBrowseRequestPayload.
 *
 * Каждая строка rows() - один лот, поля разделены "|" (в registry_id/seller_name/base64
 * встретиться не может, ник в Minecraft не содержит "|"):
 * id|registryId|qty|price|sellerName|itemData (base64 NBT, см. ItemDataCodec, пустая
 * строка - нет сохранённых данных, старый лот). Строка "!error" вместо списка - сетевая
 * ошибка на стороне сервера (см. ServerPayloadHandler), не пустой каталог.
 */
public record MarketBrowseResponsePayload(List<String> rows, int total, int page, int perPage) implements CustomPacketPayload {

    public static final Type<MarketBrowseResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_browse_response"));

    public static final StreamCodec<FriendlyByteBuf, MarketBrowseResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MarketBrowseResponsePayload::rows,
                    ByteBufCodecs.VAR_INT, MarketBrowseResponsePayload::total,
                    ByteBufCodecs.VAR_INT, MarketBrowseResponsePayload::page,
                    ByteBufCodecs.VAR_INT, MarketBrowseResponsePayload::perPage,
                    MarketBrowseResponsePayload::new);

    @Override
    public Type<MarketBrowseResponsePayload> type() {
        return TYPE;
    }
}
