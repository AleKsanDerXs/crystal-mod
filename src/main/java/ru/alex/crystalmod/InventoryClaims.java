package ru.alex.crystalmod;

import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Выдача предмета из рыночного инвентаря сайта в игру — со ВСЕМИ данными предмета ({@link ItemDataCodec}).
 *
 * <p>Двухфазно, как банкомат: {@code inv_claim_mod} (сайт бронирует предмет и отдаёт item_data) → мод кладёт предмет → {@code inv_confirm_mod}
 * (сайт помечает выданным; при неудаче возвращает предмет в инвентарь). Подтверждение повторяется при сбое сети. Два пути входа:
 * <ul>
 *   <li>{@link #claimFromSite} — кнопка «В игру» на сайте: сайт сам забронировал предмет и вызвал RCON-команду
 *       {@code crystal market claim <ник> <claim_id>};</li>
 *   <li>{@link #claimFromGame} — игрок сам командой {@code /crystal inventory claim <id> [кол-во]}.</li>
 * </ul>
 * Игрок должен быть на сервере. Не влезшее в инвентарь падает на пол (потери нет).
 */
public final class InventoryClaims {

    private InventoryClaims() {
    }

    private static final Set<UUID> BUSY = ConcurrentHashMap.newKeySet();
    // Выдача из игры: при неизвестном исходе (сеть) tx переиспользуем при повторе — ключ включает предмет и количество,
    // иначе повтор для другого предмета вернул бы чужую бронь.
    private static final Map<String, String> PENDING_TX = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService RETRY = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "CrystalInventory-Confirm");
        t.setDaemon(true);
        return t;
    });

    /** Сайт забронировал предмет (claim_id) и просит выдать его игроку, если тот онлайн. */
    public static void claimFromSite(MinecraftServer server, String playerName, String claimId, Consumer<Component> feedback) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player == null) {
            confirmWithRetry(claimId, false, "игрок не в сети", 0);
            feedback.accept(Component.translatable("crystalmod.inventory.player_offline", playerName));
            return;
        }
        run(server, player, claimId, null, 0, true);
    }

    /** Игрок сам выдаёт предмет из своего инвентаря сайта. quantity ≤ 0 — всё количество записи. */
    public static void claimFromGame(ServerPlayer player, int inventoryId, int quantity) {
        run(player.getServer(), player, null, inventoryId, quantity, false);
    }

    private static void run(MinecraftServer server, ServerPlayer player, String siteClaimId, Integer inventoryId, int quantity, boolean siteReserved) {
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();

        if (!BUSY.add(uuid)) {
            player.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            if (siteReserved) confirmWithRetry(siteClaimId, false, "игрок занят другой операцией", 0);
            return;
        }

        String pendingKey = uuid + ":" + inventoryId + ":" + quantity;
        String tx = siteReserved ? siteClaimId : PENDING_TX.computeIfAbsent(pendingKey, k -> CrystalAPI.newTxId());

        CrystalAPI.inventoryClaimAsync(name, tx, inventoryId, quantity)
                .thenAccept(result -> server.execute(() -> {
                    try {
                        handleReserved(server, uuid, name, pendingKey, result);
                    } finally {
                        BUSY.remove(uuid);
                    }
                }))
                .exceptionally(ex -> {
                    server.execute(() -> {
                        BUSY.remove(uuid);
                        ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                        if (p != null) p.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                        // бронь сайта уже могла быть создана, а ответ потерян — пробуем освободить (идемпотентно)
                        if (siteReserved) confirmWithRetry(siteClaimId, false, "сайт недоступен при выдаче", 0);
                    });
                    return null;
                });
    }

    private static void handleReserved(MinecraftServer server, UUID uuid, String name, String pendingKey, JsonObject result) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        boolean ok = result.get("ok").getAsBoolean();

        if (!ok) {
            PENDING_TX.remove(pendingKey);   // законный отказ: следующая попытка — новая бронь
            String error = result.has("error") ? result.get("error").getAsString() : "?";
            if (player != null) player.sendSystemMessage(Component.translatable("crystalmod.inventory.failed", error).withStyle(ChatFormatting.RED));
            return;
        }
        if (result.has("already_claimed")) {
            PENDING_TX.remove(pendingKey);
            if (player != null) player.sendSystemMessage(Component.translatable("crystalmod.inventory.already").withStyle(ChatFormatting.YELLOW));
            return;
        }

        String claimId = result.get("claim_id").getAsString();
        String registryId = result.get("registry_id").getAsString();
        int quantity = result.get("quantity").getAsInt();
        String itemData = result.has("item_data") && !result.get("item_data").isJsonNull() ? result.get("item_data").getAsString() : null;

        if (player == null) {   // успел выйти, пока шёл запрос: предмет остаётся в инвентаре сайта
            confirmWithRetry(claimId, false, "игрок вышел до выдачи", 0);
            PENDING_TX.remove(pendingKey);
            return;
        }

        Component itemName;
        boolean delivered;
        try {
            ItemStack proto = ItemDataCodec.decodeOrPlain(itemData, registryId, 1, player.registryAccess());
            if (proto.isEmpty()) throw new IllegalStateException("неизвестный предмет " + registryId);
            itemName = proto.getHoverName();
            int max = Math.max(1, proto.getMaxStackSize());
            for (int left = quantity; left > 0; ) {
                int n = Math.min(left, max);
                ItemStack part = proto.copyWithCount(n);
                // Inventory.add возвращает true и при неполной вставке — остаток роняем на пол, а не теряем
                player.getInventory().add(part);
                if (!part.isEmpty()) player.drop(part, false);
                left -= n;
            }
            delivered = true;
        } catch (Exception e) {
            CrystalMod.LOGGER.error("Не удалось выдать предмет из инвентаря сайта (claim {}): {}", claimId, e.toString());
            itemName = Component.literal(registryId);
            delivered = false;
        }

        confirmWithRetry(claimId, delivered, delivered ? null : "ошибка выдачи в моде", 0);
        PENDING_TX.remove(pendingKey);
        if (delivered) {
            player.sendSystemMessage(Component.translatable("crystalmod.inventory.claimed", itemName, quantity).withStyle(ChatFormatting.GREEN));
        } else {
            player.sendSystemMessage(Component.translatable("crystalmod.inventory.failed", "deliver").withStyle(ChatFormatting.RED));
        }
    }

    /** Подтверждение итога: при сетевой ошибке повторяем (3, 6, 9 … с, до 6 попыток); сам вызов идемпотентен на сайте. */
    private static void confirmWithRetry(String claimId, boolean ok, String reason, int attempt) {
        CrystalAPI.inventoryConfirmAsync(claimId, ok, reason).exceptionally(ex -> {
            if (attempt < 6) {
                RETRY.schedule(() -> confirmWithRetry(claimId, ok, reason, attempt + 1), 3L * (attempt + 1), TimeUnit.SECONDS);
            } else {
                CrystalMod.LOGGER.error("Не удалось подтвердить выдачу claim {} (ok={}): сайт недоступен. Разберите вручную в админке.", claimId, ok);
            }
            return null;
        });
    }
}
