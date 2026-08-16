package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Справка «как работает рынок» — открывается кнопкой «?» из терминала/каталога/
 * «Мои лоты». Текст в {@code crystalmod.market_block.help_text} (с явными "\n"
 * между пунктами), {@link MultiLineLabel} сам переносит длинные строки по ширине —
 * не нужен свой велосипед для word-wrap.
 */
public class MarketHelpScreen extends Screen {

    private final Screen parent;
    private MultiLineLabel label;

    public MarketHelpScreen(Screen parent) {
        super(Component.translatable("crystalmod.market_block.help_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        label = MultiLineLabel.create(this.font, Component.translatable("crystalmod.market_block.help_text"), this.width - 60);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
                .bounds(this.width / 2 - 100, this.height - 30, 200, 20)
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
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        label.renderCentered(guiGraphics, this.width / 2, 44, 12, 0xCCCCCC);
    }
}
