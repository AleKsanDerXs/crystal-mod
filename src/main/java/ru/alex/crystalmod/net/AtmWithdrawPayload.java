package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: кнопка «Снять» в {@code CrystalATMScreen}, сумма из поля ввода. */
public record AtmWithdrawPayload(int amount) implements CustomPacketPayload {

    public static final Type<AtmWithdrawPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "atm_withdraw"));

    public static final StreamCodec<FriendlyByteBuf, AtmWithdrawPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AtmWithdrawPayload::amount,
                    AtmWithdrawPayload::new);

    @Override
    public Type<AtmWithdrawPayload> type() {
        return TYPE;
    }
}
