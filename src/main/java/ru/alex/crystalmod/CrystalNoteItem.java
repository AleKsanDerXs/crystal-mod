package ru.alex.crystalmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Банкнота хрусталиков. Зачисление на баланс сайта (депозит) — только через
 * {@code /crystal deposit} или банкомат ({@link CrystalATMMenu}), НЕ по ПКМ
 * предметом (сознательно убрано — случайный клик мог депонировать банкноту,
 * которую игрок просто хотел переложить/рассмотреть).
 */
public class CrystalNoteItem extends Item {

    public CrystalNoteItem(Properties properties) {
        super(properties);
    }

    public static CrystalNoteContents contents(ItemStack stack) {
        return stack.getOrDefault(CrystalItems.CRYSTAL_NOTE_CONTENTS.get(),
                new CrystalNoteContents("", 0));
    }

    public static ItemStack create(String serial, int value) {
        ItemStack stack = new ItemStack(CrystalItems.CRYSTAL_NOTE.get());
        stack.set(CrystalItems.CRYSTAL_NOTE_CONTENTS.get(), new CrystalNoteContents(serial, value));
        return stack;
    }

    /** Номинал в самом названии — чтобы отличать банкноты в инвентаре не открывая тултип. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(), contents(stack).value());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CrystalNoteContents c = contents(stack);
        tooltip.add(Component.translatable("item.crystalmod.crystal_note.value", c.value())
                .withStyle(ChatFormatting.AQUA));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("serial: " + c.serial()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
