package ru.alex.crystalmod.net;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import ru.alex.crystalmod.CrystalATMMenu;
import ru.alex.crystalmod.CrystalAPI;
import ru.alex.crystalmod.CrystalMod;
import ru.alex.crystalmod.ItemDataCodec;
import ru.alex.crystalmod.MarketListingMenu;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Обработчик пакетов банкомата/терминала рынка на серверной стороне. */
public final class ServerPayloadHandler {

    private ServerPayloadHandler() {
    }

    // ── Состояние "покупка в процессе" на игрока — см. handleMarketBuy ─────────
    // Нет постоянного Menu (как у банкомата/выставления лота), к которому можно
    // привязать pendingTxId, поэтому храним отдельной картой по UUID игрока.
    // Запись живёт ровно между кликом "Купить" и определённым исходом (успех/
    // законный отказ) — не растёт: одна запись на игрока максимум, удаляется
    // сразу по получении ответа, остаётся только при сетевой ошибке до повтора.
    private static final Set<UUID> BUY_BUSY = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, String> PENDING_BUY_TX = new ConcurrentHashMap<>();

    // Отмена лота идемпотентна по id лота (сайт проверяет status), поэтому в отличие
    // от покупки/депозита ей не нужен свой tx_id — просто анти-даблклик busy-флаг.
    private static final Set<UUID> CANCEL_BUSY = ConcurrentHashMap.newKeySet();

    public static void handleAtmDeposit(AtmDepositPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof CrystalATMMenu menu) {
                menu.attemptDeposit(payload.amount());
            }
        });
    }

    public static void handleAtmWithdraw(AtmWithdrawPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof CrystalATMMenu menu) {
                menu.attemptWithdraw(payload.amount());
            }
        });
    }

    public static void handleListItem(ListItemPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (player.containerMenu instanceof MarketListingMenu menu) {
                menu.attemptList(payload.pricePerUnit());
            }
        });
    }

    /** item_data — nullable колонка, PHP отдаёт JSON null для старых лотов без сохранённых данных. */
    private static String itemDataOrEmpty(JsonObject listing) {
        return listing.has("item_data") && !listing.get("item_data").isJsonNull()
                ? listing.get("item_data").getAsString() : "";
    }

    public static void handleMyListingsRequest(MyListingsRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String username = player.getGameProfile().getName();

            CrystalAPI.myListingsAsync(username)
                    .thenAccept(result -> player.getServer().execute(() -> {
                        List<String> rows = new ArrayList<>();
                        if (result.get("ok").getAsBoolean() && result.has("listings")) {
                            for (var el : result.getAsJsonArray("listings")) {
                                JsonObject l = el.getAsJsonObject();
                                rows.add(String.join(MyListingsResponsePayload.SEP,
                                        l.get("id").getAsString(),
                                        l.get("registry_id").getAsString(),
                                        l.get("quantity_available").getAsString(),
                                        l.get("price_per_unit").getAsString(),
                                        l.get("status").getAsString(),
                                        itemDataOrEmpty(l)));
                            }
                        }
                        PacketDistributor.sendToPlayer(player, new MyListingsResponsePayload(rows));
                    }))
                    .exceptionally(ex -> {
                        player.getServer().execute(() -> {
                            CrystalMod.LOGGER.warn("Не удалось получить лоты игрока {}: {}", username, ex.getMessage());
                            // Список из одного маркера "!error" — клиент отличает его от "лотов нет".
                            PacketDistributor.sendToPlayer(player, new MyListingsResponsePayload(List.of("!error")));
                        });
                        return null;
                    });
        });
    }

    public static void handleMarketBrowseRequest(MarketBrowseRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            CrystalAPI.marketListAsync(payload.search(), payload.sort(), payload.page(), 20)
                    .thenAccept(result -> player.getServer().execute(() -> {
                        List<String> rows = new ArrayList<>();
                        int total = 0, page = 1, perPage = 20;
                        if (result.get("ok").getAsBoolean()) {
                            total = result.get("total").getAsInt();
                            page = result.get("page").getAsInt();
                            perPage = result.get("per_page").getAsInt();
                            for (var el : result.getAsJsonArray("items")) {
                                JsonObject l = el.getAsJsonObject();
                                rows.add(String.join("|",
                                        l.get("id").getAsString(),
                                        l.get("registry_id").getAsString(),
                                        l.get("quantity_available").getAsString(),
                                        l.get("price_per_unit").getAsString(),
                                        l.get("seller_name").getAsString(),
                                        itemDataOrEmpty(l)));
                            }
                        }
                        PacketDistributor.sendToPlayer(player, new MarketBrowseResponsePayload(rows, total, page, perPage));
                    }))
                    .exceptionally(ex -> {
                        player.getServer().execute(() -> {
                            CrystalMod.LOGGER.warn("Не удалось загрузить каталог рынка: {}", ex.getMessage());
                            PacketDistributor.sendToPlayer(player, new MarketBrowseResponsePayload(List.of("!error"), 0, payload.page(), 20));
                        });
                        return null;
                    });
        });
    }

    /**
     * Восстанавливает предмет по ответу сайта (registry_id + опциональный item_data)
     * и кладёт его в инвентарь игрока, с падением на пол при переполненном инвентаре.
     * item_data может отсутствовать/не раскодироваться (старый лот без сохранённых
     * данных) — тогда собираем "голый" предмет по registry_id, как раньше делал RCON.
     */
    private static void deliverItem(ServerPlayer player, JsonObject result) {
        String registryId = result.get("registry_id").getAsString();
        int quantity = result.get("quantity").getAsInt();
        String itemData = result.has("item_data") && !result.get("item_data").isJsonNull()
                ? result.get("item_data").getAsString() : null;

        ItemStack stack = ItemDataCodec.decodeOrPlain(itemData, registryId, quantity, player.registryAccess());
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    public static void handleMarketBuy(MarketBuyPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            UUID uuid = player.getUUID();
            if (!BUY_BUSY.add(uuid)) {
                player.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
                return;
            }
            // Тот же принцип, что и у банкомата/выставления лота: при сетевой ошибке (исход
            // неизвестен) tx_id НЕ меняется, следующий клик "Купить" повторит РОВНО этот запрос,
            // а не спишет деньги дважды. При определённом исходе (успех/законный отказ) — забываем.
            String txId = PENDING_BUY_TX.computeIfAbsent(uuid, k -> CrystalAPI.newTxId());
            String username = player.getGameProfile().getName();

            CrystalAPI.buyListingAsync(txId, username, payload.listingId(), payload.quantity())
                    .thenAccept(result -> player.getServer().execute(() -> {
                        BUY_BUSY.remove(uuid);
                        PENDING_BUY_TX.remove(uuid);
                        boolean ok = result.get("ok").getAsBoolean();
                        if (ok) {
                            // buy_mod на сайте всегда отвечает modDelivery=true — RCON-доставку
                            // сайт больше не делает, предмет со всеми данными кладёт мод сам.
                            if (result.has("registry_id")) deliverItem(player, result);
                            player.sendSystemMessage(Component.translatable("crystalmod.market.buy_success").withStyle(ChatFormatting.GREEN));
                            PacketDistributor.sendToPlayer(player, new MarketBuyResultPayload(true, ""));
                        } else {
                            String error = result.has("error") ? result.get("error").getAsString() : "?";
                            player.sendSystemMessage(Component.translatable("crystalmod.market.buy_failed", error).withStyle(ChatFormatting.RED));
                            PacketDistributor.sendToPlayer(player, new MarketBuyResultPayload(false, error));
                        }
                    }))
                    .exceptionally(ex -> {
                        player.getServer().execute(() -> {
                            BUY_BUSY.remove(uuid);
                            player.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                            PacketDistributor.sendToPlayer(player, new MarketBuyResultPayload(false, "unreachable"));
                        });
                        return null;
                    });
        });
    }

    public static void handleCancelListing(CancelListingPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            UUID uuid = player.getUUID();
            if (!CANCEL_BUSY.add(uuid)) {
                player.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
                return;
            }
            String username = player.getGameProfile().getName();

            CrystalAPI.cancelListingAsync(username, payload.listingId())
                    .thenAccept(result -> player.getServer().execute(() -> {
                        CANCEL_BUSY.remove(uuid);
                        boolean ok = result.get("ok").getAsBoolean();
                        if (ok) {
                            if (result.has("registry_id")) deliverItem(player, result);
                            player.sendSystemMessage(Component.translatable("crystalmod.market.cancel_success").withStyle(ChatFormatting.GREEN));
                            PacketDistributor.sendToPlayer(player, new CancelListingResultPayload(true, ""));
                        } else {
                            String error = result.has("error") ? result.get("error").getAsString() : "?";
                            player.sendSystemMessage(Component.translatable("crystalmod.market.cancel_failed", error).withStyle(ChatFormatting.RED));
                            PacketDistributor.sendToPlayer(player, new CancelListingResultPayload(false, error));
                        }
                    }))
                    .exceptionally(ex -> {
                        player.getServer().execute(() -> {
                            CANCEL_BUSY.remove(uuid);
                            player.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                            PacketDistributor.sendToPlayer(player, new CancelListingResultPayload(false, "unreachable"));
                        });
                        return null;
                    });
        });
    }

    /** Мини-плашка баланса — без busy-флага/чата, тихий фоновый запрос, клиент сам кеширует. */
    public static void handleBalanceRequest(BalanceRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            String username = player.getGameProfile().getName();

            CrystalAPI.balanceAsync(username)
                    .thenAccept(result -> player.getServer().execute(() -> {
                        boolean ok = result.get("ok").getAsBoolean();
                        int crystals = ok ? result.get("crystals").getAsInt() : 0;
                        PacketDistributor.sendToPlayer(player, new BalanceResponsePayload(ok, crystals));
                    }))
                    .exceptionally(ex -> {
                        player.getServer().execute(() ->
                                PacketDistributor.sendToPlayer(player, new BalanceResponsePayload(false, 0)));
                        return null;
                    });
        });
    }
}
