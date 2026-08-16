package ru.alex.crystalmod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import ru.alex.crystalmod.CrystalItems;
import ru.alex.crystalmod.net.BalanceRequestPayload;

/**
 * Мини-плашка баланса над модовыми окнами (банкомат/терминал рынка/«Мои лоты»/каталог).
 * Кешируется на клиенте с ленивым TTL-обновлением — {@link #render} можно звать из
 * каждого кадра render() любого экрана, лишний трафик не создаёт: сетевой запрос
 * уходит не чаще раза в {@link #TTL_MS}, а не на каждый кадр.
 */
public final class BalanceOverlay {

    private static final long TTL_MS = 15_000;
    private static final ItemStack ICON = new ItemStack(CrystalItems.CRYSTAL_NOTE.get());

    // -1 — баланс ещё не известен (первая отрисовка после входа/захода в окно).
    private static volatile int cachedValue = -1;
    private static volatile long lastRequestMs = 0;

    private BalanceOverlay() {
    }

    /** Вызывается {@link ClientPayloadHandler} при получении ответа с сервера. */
    public static void onResponse(boolean ok, int crystals) {
        if (ok) cachedValue = crystals; // при ok=false оставляем последнее известное значение, не мигаем "..."
    }

    /** Рисует плашку с центром в (centerX, topY..topY+16), при устаревшем кеше сама шлёт обновление. */
    public static void render(GuiGraphics guiGraphics, Font font, int centerX, int topY) {
        long now = System.currentTimeMillis();
        if (now - lastRequestMs > TTL_MS) {
            lastRequestMs = now;
            PacketDistributor.sendToServer(new BalanceRequestPayload());
        }

        Component text = Component.translatable("crystalmod.balance_badge",
                cachedValue < 0 ? "..." : String.valueOf(cachedValue));
        int textWidth = font.width(text);
        int pillWidth = textWidth + 24;
        int left = centerX - pillWidth / 2;

        guiGraphics.fill(left, topY, left + pillWidth, topY + 16, 0xC0202030);
        guiGraphics.renderItem(ICON, left + 2, topY - 1);
        guiGraphics.drawString(font, text, left + 20, topY + 4, 0xFFD700, false);
    }
}
