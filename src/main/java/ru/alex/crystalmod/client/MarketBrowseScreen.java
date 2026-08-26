package ru.alex.crystalmod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import ru.alex.crystalmod.ItemDataCodec;
import ru.alex.crystalmod.net.MarketBrowseRequestPayload;
import ru.alex.crystalmod.net.MarketBuyPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Каталог рынка — поиск/сортировка/пагинация/покупка. Панель считается как доля
 * от {@code this.width}/{@code this.height} в {@link #init()}, который ваниль
 * сама перевызывает при смене размера окна/GUI Scale ({@code Screen#resize}) —
 * это и даёт «масштабируется под окно» без ручного драг-ресайза (в ванильном
 * Minecraft GUI такого вообще не существует, только окно/масштаб целиком).
 *
 * <p>Обычный {@code Screen}, не {@code AbstractContainerScreen}: строки —
 * не инвентарные слоты, а данные с сайта, отрисованные вручную.
 */
public class MarketBrowseScreen extends Screen {

    // stack хранит уже раскодированный ItemStack (зачарования/содержимое рюкзака-шалкера/т.п.,
    // см. ItemDataCodec) — декодируется один раз в applyResults(), не на каждый кадр render().
    private record Row(int id, ItemStack stack, int qty, int price, String seller) {
    }

    private static final String[] SORT_MODES = {"newest", "price_asc", "price_desc", "qty_desc"};
    private static final int ROW_HEIGHT = 20;
    // +22 к прежним 50 — под добавленную строку с полем "Количество" (общее на всю
    // покупку, применяется к любому лоту, на который нажали "Купить").
    private static final int LIST_TOP_OFFSET = 72;
    private static final int LIST_BOTTOM_MARGIN = 34;

    private final Screen parent;
    private final List<Button> rowButtons = new ArrayList<>();

    private EditBox searchBox;
    private EditBox qtyBox;
    private int sortIndex = 0;
    private int page = 1;
    private int totalPages = 1;
    private List<Row> rows = List.of();
    private boolean loading = true;
    private boolean unreachable = false;
    private int scroll = 0;
    private int buyingRowId = -1;

    private int panelLeft, panelTop, panelWidth, panelHeight, visibleRows;
    private Button sortButton;
    private Button prevButton;
    private Button nextButton;

    public MarketBrowseScreen(Screen parent) {
        super(Component.translatable("crystalmod.market_block.browse_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.max(280, Math.min(this.width - 20, (int) (this.width * 0.8)));
        panelHeight = Math.max(200, Math.min(this.height - 20, (int) (this.height * 0.8)));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        visibleRows = Math.max(1, (panelHeight - LIST_TOP_OFFSET - LIST_BOTTOM_MARGIN) / ROW_HEIGHT);
        rowButtons.clear();

        searchBox = new EditBox(this.font, panelLeft + 8, panelTop + 24, panelWidth - 176, 18,
                Component.translatable("crystalmod.market_block.browse_search_placeholder"));
        searchBox.setMaxLength(64);
        this.addRenderableWidget(searchBox);
        this.setInitialFocus(searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("crystalmod.market_block.browse_search_button"),
                        b -> {
                            page = 1;
                            requestData();
                        })
                .bounds(panelLeft + panelWidth - 164, panelTop + 24, 60, 18)
                .build());

        qtyBox = new EditBox(this.font, panelLeft + 66, panelTop + 46, 46, 18,
                Component.translatable("crystalmod.market_block.browse_qty_placeholder"));
        qtyBox.setMaxLength(5);
        qtyBox.setValue("1");
        this.addRenderableWidget(qtyBox);

        sortButton = Button.builder(sortLabel(), b -> {
                    sortIndex = (sortIndex + 1) % SORT_MODES.length;
                    sortButton.setMessage(sortLabel());
                    page = 1;
                    requestData();
                })
                .bounds(panelLeft + panelWidth - 100, panelTop + 24, 92, 18)
                .build();
        this.addRenderableWidget(sortButton);

        prevButton = Button.builder(Component.literal("<"), b -> {
                    if (page > 1) {
                        page--;
                        requestData();
                    }
                })
                .bounds(panelLeft + panelWidth / 2 - 60, panelTop + panelHeight - 24, 20, 18)
                .build();
        this.addRenderableWidget(prevButton);

        nextButton = Button.builder(Component.literal(">"), b -> {
                    if (page < totalPages) {
                        page++;
                        requestData();
                    }
                })
                .bounds(panelLeft + panelWidth / 2 + 40, panelTop + panelHeight - 24, 20, 18)
                .build();
        this.addRenderableWidget(nextButton);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
                .bounds(panelLeft + panelWidth - 68, panelTop + panelHeight - 24, 60, 18)
                .build());

        this.addRenderableWidget(Button.builder(Component.literal("?"), b -> this.minecraft.setScreen(new MarketHelpScreen(this)))
                .bounds(panelLeft + panelWidth - 20, panelTop + 2, 16, 16)
                .build());

        requestData();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private Component sortLabel() {
        return Component.translatable("crystalmod.market_block.sort." + SORT_MODES[sortIndex]);
    }

    private void requestData() {
        loading = true;
        unreachable = false;
        String search = searchBox != null ? searchBox.getValue().trim() : "";
        PacketDistributor.sendToServer(new MarketBrowseRequestPayload(search, SORT_MODES[sortIndex], page));
    }

    /** Вызывается {@link ClientPayloadHandler} при получении ответа с сервера. */
    public void applyResults(List<String> packedRows, int total, int respPage, int perPage) {
        loading = false;
        if (packedRows.size() == 1 && "!error".equals(packedRows.get(0))) {
            unreachable = true;
            rows = List.of();
            rebuildRowButtons();
            return;
        }
        unreachable = false;
        page = respPage;
        totalPages = Math.max(1, (int) Math.ceil(total / (double) Math.max(1, perPage)));
        scroll = 0;

        var registries = Minecraft.getInstance().level.registryAccess();
        List<Row> parsed = new ArrayList<>();
        for (String r : packedRows) {
            String[] f = r.split("\\|", -1);
            if (f.length < 6) continue;
            int qty = parseIntSafe(f[2]);
            ItemStack stack = ItemDataCodec.decodeOrPlain(f[5], f[1], qty, registries);
            parsed.add(new Row(parseIntSafe(f[0]), stack, qty, parseIntSafe(f[3]), f[4]));
        }
        rows = parsed;
        rebuildRowButtons();
    }

    /** Вызывается {@link ClientPayloadHandler} при получении итога покупки. */
    public void onBuyResult(boolean ok, String error) {
        buyingRowId = -1;
        requestData(); // актуализируем каталог — количество могло измениться/лот пропасть
    }

    private void rebuildRowButtons() {
        for (Button b : rowButtons) this.removeWidget(b);
        rowButtons.clear();
        if (prevButton != null) prevButton.active = page > 1;
        if (nextButton != null) nextButton.active = page < totalPages;

        int visible = Math.min(visibleRows, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(scroll + i);
            int rowY = panelTop + LIST_TOP_OFFSET + i * ROW_HEIGHT;

            Button detailsBtn = Button.builder(Component.literal("i"), b -> this.minecraft.setScreen(new ItemDetailsScreen(row.stack(), this)))
                    .bounds(panelLeft + panelWidth - 88, rowY, 20, ROW_HEIGHT - 2)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crystalmod.market_block.details_button")))
                    .build();
            this.addRenderableWidget(detailsBtn);
            rowButtons.add(detailsBtn);

            Button buyBtn = Button.builder(Component.translatable("crystalmod.market_block.browse_buy_button"), b -> {
                        int qty = Math.max(1, Math.min(parseQty(), row.qty()));
                        buyingRowId = row.id();
                        rebuildRowButtons();
                        PacketDistributor.sendToServer(new MarketBuyPayload(row.id(), qty));
                    })
                    .bounds(panelLeft + panelWidth - 60, rowY, 52, ROW_HEIGHT - 2)
                    .build();
            buyBtn.active = buyingRowId == -1;
            this.addRenderableWidget(buyBtn);
            rowButtons.add(buyBtn);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && this.getFocused() == searchBox) { // Enter / KP_Enter
            page = 1;
            requestData();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, rows.size() - visibleRows);
        int next = Math.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll);
        if (next != scroll) {
            scroll = next;
            rebuildRowButtons();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xE0101018);
        guiGraphics.fill(panelLeft, panelTop + 68, panelLeft + panelWidth, panelTop + 69, 0xFF373750);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(this.font, this.title, panelLeft + panelWidth / 2, panelTop + 8, 0xFFFFFF);
        BalanceOverlay.render(guiGraphics, this.font, panelLeft + panelWidth / 2, panelTop - 20);
        guiGraphics.drawString(this.font, Component.translatable("crystalmod.market_block.browse_qty_label"),
                panelLeft + 8, panelTop + 51, 0xCCCCCC);
        guiGraphics.drawCenteredString(this.font,
                Component.translatable("crystalmod.market_block.browse_page", page, totalPages),
                panelLeft + panelWidth / 2, panelTop + panelHeight - 20, 0xAAAAAA);

        ItemStack hovered = ItemStack.EMPTY;
        if (loading) {
            guiGraphics.drawCenteredString(this.font, Component.literal("..."),
                    panelLeft + panelWidth / 2, panelTop + panelHeight / 2, 0xAAAAAA);
        } else if (unreachable) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("crystalmod.market_block.browse_unreachable"),
                    panelLeft + panelWidth / 2, panelTop + panelHeight / 2, 0xFF5555);
        } else if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("crystalmod.market_block.browse_empty"),
                    panelLeft + panelWidth / 2, panelTop + panelHeight / 2, 0xAAAAAA);
        } else {
            int visible = Math.min(visibleRows, rows.size() - scroll);
            for (int i = 0; i < visible; i++) {
                Row row = rows.get(scroll + i);
                int rowY = panelTop + LIST_TOP_OFFSET + i * ROW_HEIGHT;
                guiGraphics.fill(panelLeft + 6, rowY, panelLeft + panelWidth - 6, rowY + ROW_HEIGHT - 2,
                        (i % 2 == 0) ? 0x30303050 : 0x20202038);

                ItemStack stack = row.stack();
                guiGraphics.renderItem(stack, panelLeft + 8, rowY + 1);
                if (mouseX >= panelLeft + 8 && mouseX < panelLeft + 26 && mouseY >= rowY + 1 && mouseY < rowY + 19) {
                    hovered = stack;
                }

                Component line = stack.getHoverName().copy()
                        .append(" x" + row.qty() + "  ")
                        .append(Component.translatable("crystalmod.market_block.price_suffix", row.price()).withStyle(ChatFormatting.AQUA));
                guiGraphics.drawString(this.font, line, panelLeft + 28, rowY + 2, 0xFFFFFF);
                guiGraphics.drawString(this.font,
                        Component.translatable("crystalmod.market_block.browse_seller", row.seller()).withStyle(ChatFormatting.GRAY),
                        panelLeft + 28, rowY + 11, 0x888888);
            }
        }

        if (!hovered.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hovered, mouseX, mouseY);
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int parseQty() {
        try {
            return Math.max(1, Integer.parseInt(qtyBox.getValue().trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
