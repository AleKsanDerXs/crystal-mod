package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Клиент → сервер: кнопка «Внести» в {@code CrystalATMScreen}, сумма из поля ввода. */
public record AtmDepositPayload(int amount) implements CustomPacketPayload {

    public static final Type<AtmDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "atm_deposit"));

    public static final StreamCodec<FriendlyByteBuf, AtmDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, AtmDepositPayload::amount,
                    AtmDepositPayload::new);

    @Override
    public Type<AtmDepositPayload> type() {
        return TYPE;
    }
}
