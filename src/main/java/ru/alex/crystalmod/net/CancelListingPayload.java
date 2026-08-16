package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: кнопка «Отменить» на строке лота в {@code MyListingsScreen}. */
public record CancelListingPayload(int listingId) implements CustomPacketPayload {

    public static final Type<CancelListingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "cancel_listing"));

    public static final StreamCodec<FriendlyByteBuf, CancelListingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CancelListingPayload::listingId,
                    CancelListingPayload::new);

    @Override
    public Type<CancelListingPayload> type() {
        return TYPE;
    }
}
