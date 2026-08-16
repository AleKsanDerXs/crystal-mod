package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Сервер -> клиент: ответ на {@link BalanceRequestPayload}. {@code ok=false} — сайт недоступен/ошибка. */
public record BalanceResponsePayload(boolean ok, int crystals) implements CustomPacketPayload {

    public static final Type<BalanceResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "balance_response"));

    public static final StreamCodec<FriendlyByteBuf, BalanceResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BalanceResponsePayload::ok,
                    ByteBufCodecs.VAR_INT, BalanceResponsePayload::crystals,
                    BalanceResponsePayload::new);

    @Override
    public Type<BalanceResponsePayload> type() {
        return TYPE;
    }
}
