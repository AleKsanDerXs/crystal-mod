package ru.alex.crystalmod;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

/**
 * {@code /crystal give <player> <serial> <value>} — команда, которой сайт
 * выдаёт банкноту по RCON при выводе хрусталиков в игру. Сигнатура
 * зафиксирована в {@code config.php → crystals.give_command} сайта
 * ({@code "crystal give {user} {serial} {value}"}) — менять здесь без правки
 * там нельзя.
 */
@EventBusSubscriber(modid = CrystalMod.MOD_ID)
public final class CrystalCommands {

    private CrystalCommands() {
    }

    // 3 проверки в минуту на игрока — команда только смотрит статус, но дёргает сайт;
    // общий RateLimiter (см. класс) сам чистит за собой память по неактивным игрокам.
    private static final RateLimiter CHECK_RATE_LIMITER = new RateLimiter(3, 60_000L);

    /**
     * Права на {@code /crystal deposit}/{@code /crystal withdraw} — по умолчанию
     * только op (резолвер {@code player.hasPermissions(2)}), но зарегистрированы
     * через штатный {@link PermissionAPI}: если на сервере стоит мост LuckPerms
     * (регистрирует свой {@code IPermissionHandler}), права на конкретные группы
     * будут работать сами, без единой правки этого мода.
     */
    public static final PermissionNode<Boolean> DEPOSIT_PERMISSION = new PermissionNode<>(
            CrystalMod.MOD_ID, "command.deposit", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));
    public static final PermissionNode<Boolean> WITHDRAW_PERMISSION = new PermissionNode<>(
            CrystalMod.MOD_ID, "command.withdraw", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(2));

    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(DEPOSIT_PERMISSION, WITHDRAW_PERMISSION);
    }

    /** op/консоль/RCON — всегда да (level 2), иначе — что скажет активный обработчик прав (см. node выше). */
    private static boolean hasEconomyPermission(CommandSourceStack source, PermissionNode<Boolean> node) {
        if (source.hasPermission(2)) return true;
        return source.getEntity() instanceof ServerPlayer player && PermissionAPI.getPermission(player, node);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                // Корень БЕЗ requires — /crystal check должна остаться доступна всем игрокам;
                // ограничение на права стоит точечно на give/market/deposit/withdraw, а не на всём дереве.
                Commands.literal("crystal")
                        .then(Commands.literal("give")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("serial", StringArgumentType.string())
                                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                        .executes(CrystalCommands::giveNote)))))
                        .then(Commands.literal("check")
                                .executes(CrystalCommands::checkNote))
                        .then(Commands.literal("deposit")
                                .requires(source -> hasEconomyPermission(source, DEPOSIT_PERMISSION))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(CrystalCommands::depositCommand)))
                        .then(Commands.literal("withdraw")
                                .requires(source -> hasEconomyPermission(source, WITHDRAW_PERMISSION))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                        .executes(CrystalCommands::withdrawCommand)))
                        .then(Commands.literal("market")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.literal("listchest")
                                        .then(Commands.argument("registry_id", StringArgumentType.string())
                                                .then(Commands.argument("quantity", IntegerArgumentType.integer(1))
                                                        .then(Commands.argument("price", IntegerArgumentType.integer(1))
                                                                .executes(CrystalCommands::listFromChest))))))
        );
    }

    // ── /crystal deposit <amount> / /crystal withdraw <amount> ─────────────────
    // Тот же принцип "неизвестный исход — храним tx_id для повтора", что у
    // CrystalATMMenu, но без Menu — состояние per-player в статических картах
    // (аналог PENDING_BUY_TX в ServerPayloadHandler).
    private static final Set<UUID> ECONOMY_BUSY = ConcurrentHashMap.newKeySet();
    private static final java.util.Map<UUID, String> PENDING_DEPOSIT_TX = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, String> PENDING_WITHDRAW_TX = new ConcurrentHashMap<>();

    private static int depositCommand(CommandContext<CommandSourceStack> ctx) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только для игроков"));
            return 0;
        }

        UUID uuid = player.getUUID();
        if (!ECONOMY_BUSY.add(uuid)) {
            player.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            return 0;
        }

        String pendingTx = PENDING_DEPOSIT_TX.get(uuid);
        String txId;
        List<CrystalNoteContents> chosenNotes;

        if (pendingTx != null) {
            // Повтор зависшей попытки — банкноты уже изъяты в прошлый раз, новый скан инвентаря не нужен.
            txId = pendingTx;
            chosenNotes = PENDING_DEPOSIT_NOTES.get(uuid);
        } else {
            Inventory inv = player.getInventory();
            List<Integer> slots = new ArrayList<>();
            List<CrystalNoteContents> notes = new ArrayList<>();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof CrystalNoteItem) {
                    slots.add(i);
                    notes.add(CrystalNoteItem.contents(stack));
                }
            }
            // Сортируем по возрастанию номинала — меньше шанс на большую сдачу (тот же приём, что у банкомата).
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < notes.size(); i++) order.add(i);
            order.sort((a, b) -> Integer.compare(notes.get(a).value(), notes.get(b).value()));

            int running = 0;
            List<Integer> chosenSlots = new ArrayList<>();
            chosenNotes = new ArrayList<>();
            for (int idx : order) {
                if (running >= amount) break;
                running += notes.get(idx).value();
                chosenSlots.add(slots.get(idx));
                chosenNotes.add(notes.get(idx));
            }
            if (running < amount) {
                ECONOMY_BUSY.remove(uuid);
                source.sendFailure(Component.translatable("crystalmod.atm.insufficient_inserted"));
                return 0;
            }
            for (int slot : chosenSlots) inv.setItem(slot, ItemStack.EMPTY);
            txId = CrystalAPI.newTxId();
            PENDING_DEPOSIT_TX.put(uuid, txId);
            PENDING_DEPOSIT_NOTES.put(uuid, chosenNotes);
        }

        List<CrystalNoteContents> notesForCallback = chosenNotes;
        int finalAmount = amount;
        player.sendSystemMessage(Component.translatable("crystalmod.deposit.pending").withStyle(ChatFormatting.GRAY));

        CrystalAPI.atmDepositAsync(txId, player.getGameProfile().getName(), chosenNotes, amount)
                .thenAccept(result -> player.getServer().execute(() -> {
                    ECONOMY_BUSY.remove(uuid);
                    PENDING_DEPOSIT_TX.remove(uuid);
                    PENDING_DEPOSIT_NOTES.remove(uuid);
                    if (result.get("ok").getAsBoolean()) {
                        int credited = result.get("credited").getAsInt();
                        player.sendSystemMessage(Component.translatable("crystalmod.atm.deposit_success", credited).withStyle(ChatFormatting.GREEN));
                        if (result.has("change") && !result.get("change").isJsonNull()) {
                            var change = result.getAsJsonObject("change");
                            ItemStack note = CrystalNoteItem.create(change.get("serial").getAsString(), change.get("value").getAsInt());
                            if (!player.getInventory().add(note)) player.drop(note, false);
                        }
                    } else {
                        for (CrystalNoteContents n : notesForCallback) {
                            ItemStack note = CrystalNoteItem.create(n.serial(), n.value());
                            if (!player.getInventory().add(note)) player.drop(note, false);
                        }
                        String reason = result.has("error") ? result.get("error").getAsString() : "?";
                        player.sendSystemMessage(Component.translatable("crystalmod.deposit.failed", reason).withStyle(ChatFormatting.RED));
                    }
                }))
                .exceptionally(ex -> {
                    player.getServer().execute(() -> {
                        ECONOMY_BUSY.remove(uuid);
                        // tx_id/notes НЕ трогаем — исход неизвестен, следующий запуск команды повторит его же.
                        player.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
        return 1;
    }

    private static final java.util.Map<UUID, List<CrystalNoteContents>> PENDING_DEPOSIT_NOTES = new ConcurrentHashMap<>();

    private static int withdrawCommand(CommandContext<CommandSourceStack> ctx) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только для игроков"));
            return 0;
        }

        UUID uuid = player.getUUID();
        if (!ECONOMY_BUSY.add(uuid)) {
            player.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        String txId = PENDING_WITHDRAW_TX.computeIfAbsent(uuid, k -> CrystalAPI.newTxId());
        player.sendSystemMessage(Component.translatable("crystalmod.atm.processing").withStyle(ChatFormatting.GRAY));

        CrystalAPI.atmWithdrawAsync(txId, player.getGameProfile().getName(), amount)
                .thenAccept(result -> player.getServer().execute(() -> {
                    ECONOMY_BUSY.remove(uuid);
                    PENDING_WITHDRAW_TX.remove(uuid);
                    if (!result.get("ok").getAsBoolean()) {
                        String error = result.has("error") ? result.get("error").getAsString() : "?";
                        String key = "insufficient_funds".equals(error) ? "crystalmod.atm.insufficient_funds" : "crystalmod.atm.unreachable";
                        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
                        return;
                    }
                    int total = 0;
                    for (var el : result.getAsJsonArray("notes")) {
                        var n = el.getAsJsonObject();
                        int value = n.get("value").getAsInt();
                        total += value;
                        ItemStack note = CrystalNoteItem.create(n.get("serial").getAsString(), value);
                        if (!player.getInventory().add(note)) player.drop(note, false);
                    }
                    player.sendSystemMessage(Component.translatable("crystalmod.atm.withdraw_success", total).withStyle(ChatFormatting.GREEN));
                }))
                .exceptionally(ex -> {
                    player.getServer().execute(() -> {
                        ECONOMY_BUSY.remove(uuid);
                        player.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
        return 1;
    }

    private static int giveNote(CommandContext<CommandSourceStack> ctx) {
        String username = StringArgumentType.getString(ctx, "player");
        String serial = StringArgumentType.getString(ctx, "serial");
        int value = IntegerArgumentType.getInteger(ctx, "value");

        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(username);
        if (player == null) {
            // Обычный неудачный результат команды, НЕ разрыв соединения — с точки зрения
            // Rcon::execServer() на сайте это выглядит как «доставлено» (см. README про то,
            // почему ответ RCON сайт не разбирает). Банкнота останется pending_delivery
            // до повторной попытки, инициированной игроком со страницы сайта.
            source.sendFailure(Component.translatable("crystalmod.give.player_offline", username));
            return 0;
        }

        ItemStack stack = CrystalNoteItem.create(serial, value);
        boolean added = player.getInventory().add(stack);
        if (!added || !stack.isEmpty()) {
            ItemEntity dropped = player.drop(stack, false);
            if (dropped != null) dropped.setNoPickUpDelay();
        }

        source.sendSuccess(() -> Component.translatable("crystalmod.give.success", value, username), true);
        return 1;
    }

    /** {@code /crystal check} — проверяет банкноту в основной руке. Только игрок (не консоль/RCON), с rate-limit. */
    private static int checkNote(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только для игроков"));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof CrystalNoteItem)) {
            source.sendFailure(Component.translatable("crystalmod.check.not_holding"));
            return 0;
        }

        UUID uuid = player.getUUID();
        if (CHECK_RATE_LIMITER.isLimited(uuid)) {
            source.sendFailure(Component.translatable("crystalmod.check.rate_limited"));
            return 0;
        }

        CrystalNoteContents c = CrystalNoteItem.contents(held);
        player.sendSystemMessage(Component.translatable("crystalmod.check.pending").withStyle(ChatFormatting.GRAY));

        CrystalAPI.verifyNoteAsync(c.serial(), c.value())
                .thenAccept(result -> player.getServer().execute(() -> reportCheckResult(player, result)))
                .exceptionally(ex -> {
                    player.getServer().execute(() -> player.sendSystemMessage(
                            Component.translatable("crystalmod.check.unreachable").withStyle(ChatFormatting.RED)));
                    return null;
                });
        return 1;
    }

    private static void reportCheckResult(ServerPlayer player, JsonObject result) {
        if (!result.get("ok").getAsBoolean()) {
            player.sendSystemMessage(Component.translatable("crystalmod.check.unreachable").withStyle(ChatFormatting.RED));
            return;
        }
        String status = result.get("status").getAsString();
        switch (status) {
            case "genuine" -> player.sendSystemMessage(
                    Component.translatable("crystalmod.check.genuine").withStyle(ChatFormatting.GREEN));
            case "already_deposited" -> {
                String depositedBy = result.has("deposited_by_username") ? result.get("deposited_by_username").getAsString() : "?";
                player.sendSystemMessage(
                        Component.translatable("crystalmod.check.already_deposited", depositedBy).withStyle(ChatFormatting.YELLOW));
                CrystalMod.LOGGER.warn("/crystal check: {} держит уже сданную банкноту (deposited_by={}) — похоже на дюп",
                        player.getGameProfile().getName(), depositedBy);
            }
            default -> {
                player.sendSystemMessage(Component.translatable("crystalmod.check.forged").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
                CrystalMod.LOGGER.warn("/crystal check: {} держит поддельную банкноту (status={})",
                        player.getGameProfile().getName(), status);
            }
        }
    }

    /**
     * {@code /crystal market listchest <registry_id> <quantity> <price>} —
     * op смотрит на сундук (или любой другой {@link Container}-блок) в радиусе
     * досягаемости, команда изымает quantity предмета registry_id из него и
     * сразу выставляет лот (та же схема custody, что у блока рынка — мод уже
     * держит предмет физически, RCON-эскроу не нужен).
     */
    private static int listFromChest(CommandContext<CommandSourceStack> ctx) {
        String registryId = StringArgumentType.getString(ctx, "registry_id");
        int quantity = IntegerArgumentType.getInteger(ctx, "quantity");
        int price = IntegerArgumentType.getInteger(ctx, "price");

        CommandSourceStack source = ctx.getSource();
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            source.sendFailure(Component.literal("Только для игроков (нужно смотреть на сундук)"));
            return 0;
        }

        var item = BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.ResourceLocation.parse(registryId)).orElse(null);
        if (item == null) {
            source.sendFailure(Component.translatable("crystalmod.market.unknown_item", registryId));
            return 0;
        }

        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.translatable("crystalmod.market.not_looking_at_chest"));
            return 0;
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof Container container)) {
            source.sendFailure(Component.translatable("crystalmod.market.not_looking_at_chest"));
            return 0;
        }

        // Считаем, хватает ли — забирать частично нельзя (либо весь лот, либо ничего).
        int available = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() == item) available += s.getCount();
        }
        if (available < quantity) {
            source.sendFailure(Component.translatable("crystalmod.market.not_enough_in_chest", quantity, available));
            return 0;
        }

        int remaining = quantity;
        List<Integer> touchedSlots = new ArrayList<>();
        List<Integer> touchedCounts = new ArrayList<>();
        // Пулит по совпадению Item, не всего стека — если в сундуке лежат по-разному
        // зачарованные экземпляры одного предмета, данные берём с ПЕРВОГО тронутого
        // слота (лучшее, что можно сделать для команды, не гарантирующей однородность).
        String itemData = "";
        List<String> tooltipLines = List.of();
        for (int i = 0; i < container.getContainerSize() && remaining > 0; i++) {
            ItemStack s = container.getItem(i);
            if (s.getItem() != item) continue;
            if (itemData.isEmpty()) {
                itemData = ItemDataCodec.encode(s, player.registryAccess());
                tooltipLines = ItemDataCodec.tooltipLines(s, player);
            }
            int take = Math.min(remaining, s.getCount());
            touchedSlots.add(i);
            touchedCounts.add(take);
            s.shrink(take);
            remaining -= take;
        }
        container.setChanged();

        String username = player.getGameProfile().getName();
        String txId = CrystalAPI.newTxId();
        source.sendSuccess(() -> Component.translatable("crystalmod.market.listing_pending"), false);

        CrystalAPI.createListingDirectAsync(txId, username, registryId, quantity, price, itemData, tooltipLines)
                .thenAccept(result -> player.getServer().execute(() -> {
                    if (result.get("ok").getAsBoolean()) {
                        player.sendSystemMessage(Component.translatable("crystalmod.market.listing_created",
                                quantity, registryId, price).withStyle(ChatFormatting.GREEN));
                    } else {
                        // Не получилось — возвращаем предметы в те же слоты сундука, откуда взяли.
                        for (int i = 0; i < touchedSlots.size(); i++) {
                            ItemStack s = container.getItem(touchedSlots.get(i));
                            if (s.isEmpty()) {
                                container.setItem(touchedSlots.get(i), new ItemStack(item, touchedCounts.get(i)));
                            } else {
                                s.grow(touchedCounts.get(i));
                            }
                        }
                        container.setChanged();
                        player.sendSystemMessage(Component.translatable("crystalmod.market.listing_failed",
                                result.has("error") ? result.get("error").getAsString() : "?").withStyle(ChatFormatting.RED));
                    }
                }))
                .exceptionally(ex -> {
                    player.getServer().execute(() -> {
                        for (int i = 0; i < touchedSlots.size(); i++) {
                            ItemStack s = container.getItem(touchedSlots.get(i));
                            if (s.isEmpty()) {
                                container.setItem(touchedSlots.get(i), new ItemStack(item, touchedCounts.get(i)));
                            } else {
                                s.grow(touchedCounts.get(i));
                            }
                        }
                        container.setChanged();
                        player.sendSystemMessage(Component.translatable("crystalmod.market.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
        return 1;
    }
}
