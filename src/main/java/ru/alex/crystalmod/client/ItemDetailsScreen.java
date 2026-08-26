package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * «Подробнее» по предмету — открывается кнопкой «i» из каталога рынка/«Мои лоты».
 * Ничего не парсит сама: {@code stack} уже полностью раскодирован (см.
 * {@link ru.alex.crystalmod.ItemDataCodec}), а обычный тултип предмета САМ показывает
 * содержимое, если оно есть — у шалкера это список из {@code appendHoverText}
 * (ShulkerBoxBlock: до 5 предметов + «и ещё N»), у модовых контейнеров вроде рюкзаков
 * Sophisticated Backpacks — их собственный {@code getTooltipImage()}/hover-текст,
 * ровно то же самое, что видно при наведении. Экран просто рисует этот тултип
 * закреплённым по центру, а не бегущим за курсором — не нужно самим собирать сетку
 * содержимого предмета.
 */
public class ItemDetailsScreen extends Screen {

    private final Screen parent;
    private final ItemStack stack;

    public ItemDetailsScreen(ItemStack stack, Screen parent) {
        super(stack.getHoverName());
        this.stack = stack;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
                .bounds(this.width / 2 - 60, this.height - 36, 120, 20)
                .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Тултип сам рисует имя первой строкой — заголовок экрана отдельно не дублируем.
        guiGraphics.renderTooltip(this.font, stack, this.width / 2, this.height / 2 - 40);
    }
}
