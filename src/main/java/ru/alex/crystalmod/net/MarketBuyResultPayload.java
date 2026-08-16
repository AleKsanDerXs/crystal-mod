package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Сервер к клиенту: итог покупки (успех/отказ/сеть-недоступна — {@code error} тогда
 * "unreachable"). Помимо системного чата игроку, если открыт MarketBrowseScreen —
 * триггерит у него повторный запрос каталога с теми же фильтрами (актуализация
 * количества/исчезнувшего лота), не полагаясь на ручное обновление игроком.
 */
public record MarketBuyResultPayload(boolean ok, String error) implements CustomPacketPayload {

    public static final Type<MarketBuyResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "market_buy_result"));

    public static final StreamCodec<FriendlyByteBuf, MarketBuyResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, MarketBuyResultPayload::ok,
                    ByteBufCodecs.STRING_UTF8, MarketBuyResultPayload::error,
                    MarketBuyResultPayload::new);

    @Override
    public Type<MarketBuyResultPayload> type() {
        return TYPE;
    }
}
