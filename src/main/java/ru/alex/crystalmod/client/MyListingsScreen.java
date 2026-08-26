package ru.alex.crystalmod.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import ru.alex.crystalmod.ItemDataCodec;
import ru.alex.crystalmod.net.CancelListingPayload;
import ru.alex.crystalmod.net.MyListingsRequestPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Список собственных лотов игрока — открывается кнопкой «Мои лоты» из
 * {@link MarketListingScreen}. Только просмотр + отмена, без слотов инвентаря —
 * поэтому обычный {@code Screen}, а не {@code AbstractContainerScreen}: тултип
 * предмета рисуем вручную через {@code guiGraphics.renderTooltip(...)}.
 *
 * <p>Закрытие возвращает на {@code parent} (исходный экран терминала), а не
 * закрывает GUI целиком — серверный {@code MarketListingMenu} всё это время
 * остаётся открытым, {@code setScreen(null)} послал бы пакет закрытия
 * контейнера и потерял состояние лотка.
 */
public class MyListingsScreen extends Screen {

    // stack хранит уже раскодированный ItemStack (см. ItemDataCodec) — декодируется
    // один раз в applyResults(), не на каждый кадр render().
    private record Row(int id, ItemStack stack, int qty, int price, String status) {
    }

    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 8;

    private final Screen parent;
    private final List<Button> rowButtons = new ArrayList<>();
    // Последний известный "сырой" ответ сервера — переигрывается в init() при
    // ресайзе окна (Screen.resize() зовёт init() заново), иначе ресайз откатывал
    // бы список к состоянию на момент открытия, теряя обновление после отмены лота.
    private List<String> lastPackedRows;
    private List<Row> rows = List.of();
    private boolean unreachable;
    private int scroll = 0;
    private int cancellingRowId = -1;

    private int panelLeft, top;

    public MyListingsScreen(List<String> initialRows, Screen parent) {
        super(Component.translatable("crystalmod.market_block.my_listings_title"));
        this.parent = parent;
        this.lastPackedRows = initialRows;
    }

    @Override
    protected void init() {
        int panelWidth = 300; // +40 к прежним 260 — под добавленную кнопку «i» (подробнее) в каждой строке
        panelLeft = (this.width - panelWidth) / 2;
        top = (this.height - VISIBLE_ROWS * ROW_HEIGHT) / 2 - 20;
        int bottom = (this.height + VISIBLE_ROWS * ROW_HEIGHT) / 2 + 40;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> this.onClose())
                .bounds(panelLeft, bottom, panelWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("?"), b -> this.minecraft.setScreen(new MarketHelpScreen(this)))
                .bounds(panelLeft + panelWidth - 16, top - 18, 16, 16)
                .build());
        applyResults(lastPackedRows);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    /** Вызывается {@link ClientPayloadHandler} при получении/обновлении списка лотов. */
    public void applyResults(List<String> packedRows) {
        lastPackedRows = packedRows;
        cancellingRowId = -1;
        if (packedRows.size() == 1 && "!error".equals(packedRows.get(0))) {
            unreachable = true;
            rows = List.of();
            rebuildRowButtons();
            return;
        }
        unreachable = false;
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

    /** Вызывается {@link ClientPayloadHandler} при получении итога отмены лота. */
    public void onCancelResult(boolean ok, String error) {
        // Актуализируем список независимо от исхода — статус мог всё равно смениться.
        PacketDistributor.sendToServer(new MyListingsRequestPayload());
    }

    private void rebuildRowButtons() {
        for (Button b : rowButtons) this.removeWidget(b);
        rowButtons.clear();
        if (rows == null) return;

        int panelWidth = 300; // +40 к прежним 260 — под добавленную кнопку «i» (подробнее) в каждой строке
        int visible = Math.min(VISIBLE_ROWS, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(scroll + i);
            int rowY = top + i * ROW_HEIGHT;

            Button detailsBtn = Button.builder(Component.literal("i"), b -> this.minecraft.setScreen(new ItemDetailsScreen(row.stack(), this)))
                    .bounds(panelLeft + panelWidth - 88, rowY, 20, ROW_HEIGHT - 2)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("crystalmod.market_block.details_button")))
                    .build();
            this.addRenderableWidget(detailsBtn);
            rowButtons.add(detailsBtn);

            boolean cancellable = "active".equals(row.status()) || "pending_escrow".equals(row.status());
            if (!cancellable) continue;

            Button cancelBtn = Button.builder(Component.translatable("crystalmod.market_block.cancel_button"), b -> {
                        cancellingRowId = row.id();
                        rebuildRowButtons();
                        PacketDistributor.sendToServer(new CancelListingPayload(row.id()));
                    })
                    .bounds(panelLeft + panelWidth - 60, rowY, 52, ROW_HEIGHT - 2)
                    .build();
            cancelBtn.active = cancellingRowId == -1;
            this.addRenderableWidget(cancelBtn);
            rowButtons.add(cancelBtn);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, rows.size() - VISIBLE_ROWS);
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int panelWidth = 300; // +40 к прежним 260 — под добавленную кнопку «i» (подробнее) в каждой строке
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top - 16, 0xFFFFFF);
        BalanceOverlay.render(guiGraphics, this.font, this.width / 2, top - 34);

        if (unreachable) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("crystalmod.market_block.my_listings_unreachable"),
                    this.width / 2, top + VISIBLE_ROWS * ROW_HEIGHT / 2, 0xFF5555);
            return;
        }
        if (rows.isEmpty()) {
            guiGraphics.drawCenteredString(this.font,
                    Component.translatable("crystalmod.market_block.my_listings_empty"),
                    this.width / 2, top + VISIBLE_ROWS * ROW_HEIGHT / 2, 0xAAAAAA);
            return;
        }

        ItemStack hoveredStack = ItemStack.EMPTY;
        int visible = Math.min(VISIBLE_ROWS, rows.size() - scroll);
        for (int i = 0; i < visible; i++) {
            Row row = rows.get(scroll + i);
            int rowY = top + i * ROW_HEIGHT;
            ItemStack stack = row.stack();

            guiGraphics.fill(panelLeft, rowY, panelLeft + panelWidth, rowY + ROW_HEIGHT - 2, 0x40202030);
            guiGraphics.renderItem(stack, panelLeft + 2, rowY + 1);
            if (mouseX >= panelLeft + 2 && mouseX < panelLeft + 20 && mouseY >= rowY + 1 && mouseY < rowY + 19) {
                hoveredStack = stack;
            }

            Component line = Component.literal("x" + row.qty() + " ")
                    .append(Component.translatable("crystalmod.market_block.price_suffix", row.price()).withStyle(ChatFormatting.AQUA))
                    .append("  ")
                    .append(statusLabel(row.status()));
            guiGraphics.drawString(this.font, line, panelLeft + 24, rowY + 6, 0xFFFFFF);
        }

        if (!hoveredStack.isEmpty()) {
            guiGraphics.renderTooltip(this.font, hoveredStack, mouseX, mouseY);
        }
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Component statusLabel(String status) {
        ChatFormatting color = switch (status) {
            case "active" -> ChatFormatting.GREEN;
            case "pending_escrow" -> ChatFormatting.YELLOW;
            case "sold_out" -> ChatFormatting.GRAY;
            default -> ChatFormatting.RED;
        };
        return Component.translatable("crystalmod.market_block.status." + status).withStyle(color);
    }
}
