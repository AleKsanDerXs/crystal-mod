package ru.alex.crystalmod.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.alex.crystalmod.net.BalanceResponsePayload;
import ru.alex.crystalmod.net.CancelListingResultPayload;
import ru.alex.crystalmod.net.MarketBrowseResponsePayload;
import ru.alex.crystalmod.net.MarketBuyResultPayload;
import ru.alex.crystalmod.net.MyListingsResponsePayload;

/** Обработчик пакетов сервер → клиент (см. {@link ru.alex.crystalmod.net.ServerPayloadHandler}). */
public final class ClientPayloadHandler {

    private ClientPayloadHandler() {
    }

    public static void handleMyListingsResponse(MyListingsResponsePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            // Экран уже открыт (например, обновление после отмены лота) — обновляем на месте,
            // а не открываем новый поверх (иначе "Назад" вёл бы через цепочку старых снимков).
            if (mc.screen instanceof MyListingsScreen screen) {
                screen.applyResults(payload.rows());
            } else {
                mc.setScreen(new MyListingsScreen(payload.rows(), mc.screen));
            }
        });
    }

    public static void handleCancelListingResult(CancelListingResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof MyListingsScreen screen) {
                screen.onCancelResult(payload.ok(), payload.error());
            }
        });
    }

    public static void handleMarketBrowseResponse(MarketBrowseResponsePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof MarketBrowseScreen screen) {
                screen.applyResults(payload.rows(), payload.total(), payload.page(), payload.perPage());
            }
        });
    }

    public static void handleMarketBuyResult(MarketBuyResultPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof MarketBrowseScreen screen) {
                screen.onBuyResult(payload.ok(), payload.error());
            }
        });
    }

    public static void handleBalanceResponse(BalanceResponsePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> BalanceOverlay.onResponse(payload.ok(), payload.crystals()));
    }
}
