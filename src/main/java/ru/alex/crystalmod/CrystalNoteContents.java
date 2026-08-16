package ru.alex.crystalmod;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Что записано в банкноте: {@code serial} — уникальный на сайте UUID (см.
 * {@code crystal_serials.serial} в БД), {@code value} — номинал.
 *
 * <p>Serial — это и есть анти-дюп: сайт помнит статус каждого выпущенного
 * serial'а ({@code in_game}/{@code deposited}) и при повторной сдаче того же
 * serial'а с другого аккаунта поднимает тревогу вместо повторного начисления.
 * Два предмета с одинаковым serial существовать не должны — если существуют,
 * это и есть дюп на уровне игры/мода, а не сайта.
 *
 * <p>Разные serial → разные компоненты → предметы никогда не стакуются между
 * собой (см. {@link CrystalNoteItem}) — осознанный выбор: одна банкнота —
 * один слот инвентаря, номинал уже закодирован в {@code value}.
 */
public record CrystalNoteContents(String serial, int value) {

    public static final Codec<CrystalNoteContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("serial").forGetter(CrystalNoteContents::serial),
            Codec.INT.fieldOf("value").forGetter(CrystalNoteContents::value)
    ).apply(instance, CrystalNoteContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrystalNoteContents> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, CrystalNoteContents::serial,
                    ByteBufCodecs.VAR_INT, CrystalNoteContents::value,
                    CrystalNoteContents::new
            );
}
