package ru.alex.crystalmod.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import ru.alex.crystalmod.MarketListingMenu;
import ru.alex.crystalmod.net.ListItemPayload;
import ru.alex.crystalmod.net.MarketBrowseRequestPayload;
import ru.alex.crystalmod.net.MyListingsRequestPayload;

/**
 * См. javadoc {@link CrystalATMScreen} про то, почему без текстуры фона.
 *
 * <p>imageHeight на 24px больше "стандартных" 178 — под третью кнопку («Каталог
 * рынка») между «Выставить лот» и инвентарём игрока; слоты инвентаря в
 * {@link MarketListingMenu} сдвинуты вниз на те же 24px.
 */
public class MarketListingScreen extends AbstractContainerScreen<MarketListingMenu> {

    private EditBox priceBox;

    public MarketListingScreen(MarketListingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 202;
        this.titleLabelY = 6;
        this.inventoryLabelY = 108;
    }

    @Override
    protected void init() {
        super.init();
        priceBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 44, 160, 16,
                Component.translatable("crystalmod.market_block.price_placeholder"));
        priceBox.setMaxLength(9);
        priceBox.setValue("");
        this.addRenderableWidget(priceBox);
        this.setInitialFocus(priceBox);

        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.market_block.list_button"), b -> {
                    int price = parsePrice();
                    if (price > 0) PacketDistributor.sendToServer(new ListItemPayload(price));
                })
                .bounds(this.leftPos + 8, this.topPos + 64, 160, 20)
                .build());

        // Справа от единственного входного слота (x=80..98) в той же строке — свободное место
        // до края панели (imageWidth=176).
        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.market_block.my_listings_button"),
                        b -> PacketDistributor.sendToServer(new MyListingsRequestPayload()))
                .bounds(this.leftPos + 100, this.topPos + 19, 68, 18)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.market_block.browse_button"), b -> {
                    ru.alex.crystalmod.client.MarketBrowseScreen browse = new ru.alex.crystalmod.client.MarketBrowseScreen(this);
                    this.minecraft.setScreen(browse);
                })
                .bounds(this.leftPos + 8, this.topPos + 88, 160, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("?"), b -> this.minecraft.setScreen(new MarketHelpScreen(this)))
                .bounds(this.leftPos + imageWidth - 18, this.topPos + 4, 14, 14)
                .build());
    }

    private int parsePrice() {
        try {
            return Integer.parseInt(priceBox.getValue().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // См. CrystalATMScreen — без явного вызова renderTooltip() тултип предмета не рисуется.
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        BalanceOverlay.render(guiGraphics, this.font, this.leftPos + imageWidth / 2, this.topPos - 20);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xC0202030);
        guiGraphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xC0101018);
        int sx = x + 80 - 1;
        int sy = y + 20 - 1;
        guiGraphics.fill(sx, sy, sx + 18, sy + 18, 0xFF373750);
    }
}
