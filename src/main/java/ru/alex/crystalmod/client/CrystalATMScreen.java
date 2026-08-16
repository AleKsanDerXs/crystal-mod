package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.CrystalATMMenu;
import ru.alex.crystalmod.net.AtmDepositPayload;
import ru.alex.crystalmod.net.AtmWithdrawPayload;

/**
 * Без текстуры фона — обычные {@code guiGraphics.fill(...)} прямоугольники.
 * Осознанный выбор: любая переиспользованная ванильная GUI-текстура требует
 * пиксель-в-пиксель совпадения раскладки слотов, а этот код не собирался и не
 * тестировался визуально (нет сети до Maven/NeoForge в песочнице, где писался) —
 * лучше грубый, но гарантированно не «съехавший» интерфейс.
 */
public class CrystalATMScreen extends AbstractContainerScreen<CrystalATMMenu> {

    private EditBox amountBox;

    public CrystalATMScreen(CrystalATMMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 178;
        this.titleLabelY = 6;
        this.inventoryLabelY = 84;
    }

    @Override
    protected void init() {
        super.init();
        amountBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 18, 160, 16,
                Component.translatable("crystalmod.atm.amount_placeholder"));
        amountBox.setMaxLength(9);
        amountBox.setValue("");
        this.addRenderableWidget(amountBox);
        this.setInitialFocus(amountBox);

        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.atm.deposit_button"), b -> {
                    int amount = parseAmount();
                    if (amount > 0) PacketDistributor.sendToServer(new AtmDepositPayload(amount));
                })
                .bounds(this.leftPos + 8, this.topPos + 38, 78, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.atm.withdraw_button"), b -> {
                    int amount = parseAmount();
                    if (amount > 0) PacketDistributor.sendToServer(new AtmWithdrawPayload(amount));
                })
                .bounds(this.leftPos + 90, this.topPos + 38, 78, 20)
                .build());
    }

    private int parseAmount() {
        try {
            return Integer.parseInt(amountBox.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // AbstractContainerScreen сам не вызывает renderTooltip() внутри своего render() —
        // это обязанность подкласса (см. CraftingScreen в ванили). Без этой строки наведение
        // на банкноту в лотке/инвентаре не показывает номинал/лору из appendHoverText.
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        BalanceOverlay.render(guiGraphics, this.font, this.leftPos + imageWidth / 2, this.topPos - 20);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xC0202030);
        guiGraphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xC0101018);
        // Подложки под слоты лотка, чтобы визуально отделить их от общего фона.
        for (int i = 0; i < 5; i++) {
            int sx = x + 43 + i * 18 - 1;
            int sy = y + 64 - 1;
            guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF373750);
        }
    }
}
