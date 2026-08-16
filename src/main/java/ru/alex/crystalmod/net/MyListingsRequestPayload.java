package ru.alex.crystalmod.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: кнопка «Мои лоты» в {@code MarketListingScreen}. Без полей — сервер знает отправителя. */
public record MyListingsRequestPayload() implements CustomPacketPayload {

    public static final Type<MyListingsRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "my_listings_request"));

    public static final StreamCodec<ByteBuf, MyListingsRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new MyListingsRequestPayload());

    @Override
    public Type<MyListingsRequestPayload> type() {
        return TYPE;
    }
}
