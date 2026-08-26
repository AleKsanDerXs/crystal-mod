package ru.alex.crystalmod;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Полная сериализация {@link ItemStack} (зачарования, кастомное имя, содержимое
 * рюкзака — вообще все компоненты) в base64 сжатого NBT для передачи на сайт и
 * обратно. Раньше рынок передавал только registry_id + количество — так предмет
 * с любыми данными сверх "голого" реестрового ID при доставке покупателю терял
 * их (обычный RCON {@code /give} умеет только чистый предмет).
 *
 * <p>Base64 хранится в {@code crystal_listings.item_data} как есть — сайт (PHP,
 * без парсера NBT) его не разбирает, только хранит и возвращает обратно. Доставка
 * с полными данными поэтому возможна только там, где именно МОД (а не RCON)
 * кладёт предмет в инвентарь — см. {@code ServerPayloadHandler.handleMarketBuy}.
 */
public final class ItemDataCodec {

    private ItemDataCodec() {
    }

    /** Количество стека НЕ сохраняется (в crystal_listings своя колонка quantity) — кодируем как count=1. */
    public static String encode(ItemStack stack, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag tag = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).getOrThrow();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            NbtIo.writeCompressed((CompoundTag) tag, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** @return предмет с указанным count, либо {@link ItemStack#EMPTY} если base64 пуст/битый (например, старый лот без item_data). */
    public static ItemStack decode(String base64, int count, HolderLookup.Provider registries) {
        if (base64 == null || base64.isEmpty()) return ItemStack.EMPTY;
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            CompoundTag tag = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            ItemStack stack = ItemStack.CODEC.parse(ops, tag).getOrThrow();
            stack.setCount(count);
            return stack;
        } catch (Exception e) {
            CrystalMod.LOGGER.warn("ItemDataCodec: не удалось декодировать item_data ({})", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * {@link #decode} с фолбэком на "голый" предмет по registry_id — для старых
     * лотов без сохранённых данных или если декодирование не удалось. Общая логика
     * для доставки покупки/отмены (сервер) и отрисовки иконки+тултипа в каталоге/
     * «Мои лоты» (клиент) — раньше эти места просто игнорировали item_data и всегда
     * строили голый {@code new ItemStack(item)}, из-за чего шалкер/рюкзак/зачарованный
     * меч выглядели как пустой предмет при наведении.
     */
    public static ItemStack decodeOrPlain(String base64, String registryId, int count, HolderLookup.Provider registries) {
        ItemStack stack = decode(base64, count, registries);
        if (!stack.isEmpty()) return stack;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(registryId));
        return new ItemStack(item, count);
    }

    /**
     * Текстовые строки тултипа предмета (имя + лор/зачарования/содержимое шалкера
     * и любого другого контейнера, который добавляет их через обычный
     * {@code appendHoverText} — тот же метод, что даёт ванильный список "до 5
     * предметов + ещё N" у шалкер-бокса, см. {@code ShulkerBoxBlock}). Считается
     * СЕРВЕРНО в момент выставления лота и уходит на сайт отдельным полем — PHP
     * не умеет разбирать NBT и не может получить этот текст из item_data сам,
     * а мод-версии рынка (терминал) он не нужен вовсе: там тултип всегда живой,
     * из уже раскодированного стека (см. {@code ItemDetailsScreen}).
     */
    public static List<String> tooltipLines(ItemStack stack, ServerPlayer player) {
        List<String> lines = new ArrayList<>();
        try {
            for (Component line : stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL)) {
                lines.add(line.getString());
            }
        } catch (Exception e) {
            CrystalMod.LOGGER.warn("ItemDataCodec: не удалось собрать тултип для {} ({})", stack, e.getMessage());
        }
        return lines;
    }
}
