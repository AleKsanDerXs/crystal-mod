package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: кнопка «Выставить лот» в {@code MarketListingScreen}, цена из поля ввода. */
public record ListItemPayload(int pricePerUnit) implements CustomPacketPayload {

    public static final Type<ListItemPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "list_item"));

    public static final StreamCodec<FriendlyByteBuf, ListItemPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ListItemPayload::pricePerUnit,
                    ListItemPayload::new);

    @Override
    public Type<ListItemPayload> type() {
        return TYPE;
    }
}
