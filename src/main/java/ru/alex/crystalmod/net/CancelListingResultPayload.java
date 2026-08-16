package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: итог отмены лота — триггерит у клиента повторный запрос «Мои лоты». */
public record CancelListingResultPayload(boolean ok, String error) implements CustomPacketPayload {

    public static final Type<CancelListingResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "cancel_listing_result"));

    public static final StreamCodec<FriendlyByteBuf, CancelListingResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CancelListingResultPayload::ok,
                    ByteBufCodecs.STRING_UTF8, CancelListingResultPayload::error,
                    CancelListingResultPayload::new);

    @Override
    public Type<CancelListingResultPayload> type() {
        return TYPE;
    }
}
