package ru.alex.crystalmod.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент -> сервер: запрос баланса для мини-плашки над окнами. Без полей — сервер знает отправителя. */
public record BalanceRequestPayload() implements CustomPacketPayload {

    public static final Type<BalanceRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "balance_request"));

    public static final StreamCodec<ByteBuf, BalanceRequestPayload> STREAM_CODEC =
            StreamCodec.unit(new BalanceRequestPayload());

    @Override
    public Type<BalanceRequestPayload> type() {
        return TYPE;
    }
}
