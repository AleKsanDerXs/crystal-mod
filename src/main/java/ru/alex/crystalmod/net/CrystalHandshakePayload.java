package ru.alex.crystalmod.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Пустой payload-маркер (см. ту же схему в dobbymod: DobbyHandshakePayload).
 *
 * Ничего не передаёт и никогда не отправляется вручную — нужен только чтобы
 * зарегистрировать канал "crystalmod:handshake". Раз канал не помечен
 * .optional(), NeoForge на этапе configuration-фазы сам сверяет списки
 * каналов клиента и сервера и отклоняет подключение (ModMismatchDisconnectedScreen),
 * если мод стоит только на одной стороне — без единой строчки ручной проверки.
 */
public record CrystalHandshakePayload() implements CustomPacketPayload {

    public static final Type<CrystalHandshakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "handshake"));

    public static final StreamCodec<ByteBuf, CrystalHandshakePayload> STREAM_CODEC =
            StreamCodec.unit(new CrystalHandshakePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
