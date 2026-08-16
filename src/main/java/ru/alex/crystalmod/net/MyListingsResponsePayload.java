package ru.alex.crystalmod.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Сервер к клиенту: ответ на MyListingsRequestPayload.
 *
 * Каждая строка rows() - один лот, поля разделены SEP (символ "|", в base64/registry_id/
 * status встретиться не может): id + SEP + registryId + SEP + qty + SEP + price + SEP +
 * status + SEP + itemData (base64 NBT, см. ItemDataCodec, пустая строка - нет сохранённых
 * данных, старый лот). Отдельный record-codec под 6 примитивных полей - лишняя церемония
 * ради того же результата, строка проще и парсится в одну строку на клиенте.
 */
public record MyListingsResponsePayload(List<String> rows) implements CustomPacketPayload {

    public static final String SEP = "|";

    public static final Type<MyListingsResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("crystalmod", "my_listings_response"));

    public static final StreamCodec<FriendlyByteBuf, MyListingsResponsePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), MyListingsResponsePayload::rows,
                    MyListingsResponsePayload::new);

    @Override
    public Type<MyListingsResponsePayload> type() {
        return TYPE;
    }
}
