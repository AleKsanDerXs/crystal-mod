package ru.alex.crystalmod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Банкомат. Никакого BlockEntity — лоток временный (живёт только пока меню
 * открыто), при закрытии всё, что в нём осталось, возвращается игроку
 * ({@link #removed}), как у ванильного верстака. Это единственная причина,
 * по которой не нужен ни тикер, ни персистентное хранилище — а значит и
 * протекать нечему.
 */
public class CrystalATMMenu extends AbstractContainerMenu {

    private static final int TRAY_SIZE = 5;
    private static final int TRAY_X = 43;
    private static final int TRAY_Y = 64; // ниже поля ввода суммы и кнопок Внести/Снять — см. CrystalATMScreen

    private final SimpleContainer tray = new SimpleContainer(TRAY_SIZE);
    private final ServerPlayer serverPlayer; // null на клиенте — там меню только для рендера слотов

    /** true, пока идёт HTTP-запрос — блокирует повторный клик Внести/Снять, пока не пришёл ответ. */
    private volatile boolean busy = false;

    // Поля pendingDepositTxId/pendingWithdrawTxId объявлены рядом с attemptDeposit/attemptWithdraw
    // ниже — там же, где и остальное состояние "неизвестного исхода" для той же операции.

    public CrystalATMMenu(int containerId, Inventory playerInventory) {
        super(CrystalMenus.ATM_MENU.get(), containerId);
        this.serverPlayer = playerInventory.player instanceof ServerPlayer sp ? sp : null;

        for (int i = 0; i < TRAY_SIZE; i++) {
            final int slotIndex = i;
            this.addSlot(new Slot(tray, i, TRAY_X + i * 18, TRAY_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof CrystalNoteItem;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 96 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 154));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < TRAY_SIZE) {
            // Из лотка — в инвентарь игрока.
            if (!this.moveItemStackTo(original, TRAY_SIZE, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            // Из инвентаря — в лоток, только банкноты (mayPlace сам отфильтрует).
            if (!this.moveItemStackTo(original, 0, TRAY_SIZE, false)) return ItemStack.EMPTY;
        }

        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    /** Всё, что осталось в лотке при закрытии меню, — обратно игроку (как ванильный верстак). */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;
        for (int i = 0; i < tray.getContainerSize(); i++) {
            ItemStack stack = tray.getItem(i);
            if (!stack.isEmpty()) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    // ── Внести ───────────────────────────────────────────────────────────────

    // Хранится, только пока исход последней попытки НЕИЗВЕСТЕН (сеть оборвалась, а не сайт
    // явно отказал) — банкноты уже физически изъяты из лотка, деть их больше некуда, кроме
    // как ждать повтора с тем же tx_id/тем же набором (см. attemptDeposit).
    private volatile String pendingDepositTxId = null;
    private volatile List<CrystalNoteContents> pendingDepositNotes = null;
    private volatile int pendingDepositTarget = 0;

    public void attemptDeposit(int targetAmount) {
        if (serverPlayer == null) return;
        if (busy) {
            serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }

        String txId;
        List<CrystalNoteContents> chosenNotes;
        int actualTarget;

        if (pendingDepositTxId != null) {
            // Предыдущая попытка зависла без ответа — повторяем ЕЁ (тот же tx_id и тот же набор
            // банкнот), а не то, что сейчас лежит в лотке: банкноты из прошлой попытки уже не в
            // лотке (их изъяли тогда же), новый ввод игрока в данном случае игнорируем.
            txId = pendingDepositTxId;
            chosenNotes = pendingDepositNotes;
            actualTarget = pendingDepositTarget;
        } else {
            if (targetAmount <= 0) return;
            List<Integer> traySlots = new ArrayList<>();
            List<CrystalNoteContents> trayNotes = new ArrayList<>();
            for (int i = 0; i < tray.getContainerSize(); i++) {
                ItemStack stack = tray.getItem(i);
                if (stack.isEmpty()) continue;
                traySlots.add(i);
                trayNotes.add(CrystalNoteItem.contents(stack));
            }

            // Сортируем по возрастанию номинала — меньше шанс на большую сдачу при накоплении суммы.
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < trayNotes.size(); i++) order.add(i);
            order.sort((a, b) -> Integer.compare(trayNotes.get(a).value(), trayNotes.get(b).value()));

            int running = 0;
            List<Integer> chosenTraySlots = new ArrayList<>();
            List<CrystalNoteContents> chosen = new ArrayList<>();
            for (int idx : order) {
                if (running >= targetAmount) break;
                running += trayNotes.get(idx).value();
                chosenTraySlots.add(traySlots.get(idx));
                chosen.add(trayNotes.get(idx));
            }

            if (running < targetAmount) {
                serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.insufficient_inserted").withStyle(ChatFormatting.RED));
                return;
            }

            for (int slotIdx : chosenTraySlots) tray.setItem(slotIdx, ItemStack.EMPTY);
            txId = CrystalAPI.newTxId();
            chosenNotes = chosen;
            actualTarget = targetAmount;
        }

        busy = true;
        pendingDepositTxId = txId;
        pendingDepositNotes = chosenNotes;
        pendingDepositTarget = actualTarget;

        String username = serverPlayer.getGameProfile().getName();
        List<CrystalNoteContents> notesForCallback = chosenNotes;
        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.processing").withStyle(ChatFormatting.GRAY));

        CrystalAPI.atmDepositAsync(txId, username, chosenNotes, actualTarget)
                .thenAccept(result -> serverPlayer.getServer().execute(() -> {
                    busy = false;
                    pendingDepositTxId = null; // ответ получен — определённый исход, больше не "зависшая" попытка
                    pendingDepositNotes = null;
                    if (result.get("ok").getAsBoolean()) {
                        int credited = result.get("credited").getAsInt();
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.deposit_success", credited).withStyle(ChatFormatting.GREEN));
                        if (result.has("change") && !result.get("change").isJsonNull()) {
                            var change = result.getAsJsonObject("change");
                            int value = change.get("value").getAsInt();
                            ItemStack note = CrystalNoteItem.create(change.get("serial").getAsString(), value);
                            if (!serverPlayer.getInventory().add(note)) serverPlayer.drop(note, false);
                            serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.change_given", value).withStyle(ChatFormatting.AQUA));
                        }
                    } else {
                        // Явный отказ сайта (не обрыв связи) — банкноты можно спокойно вернуть.
                        for (CrystalNoteContents n : notesForCallback) {
                            ItemStack note = CrystalNoteItem.create(n.serial(), n.value());
                            if (!serverPlayer.getInventory().add(note)) serverPlayer.drop(note, false);
                        }
                        String reason = result.has("error") ? result.get("error").getAsString() : "?";
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.deposit.failed", reason).withStyle(ChatFormatting.RED));
                    }
                }))
                .exceptionally(ex -> {
                    serverPlayer.getServer().execute(() -> {
                        busy = false;
                        // Исход НЕИЗВЕСТЕН (сеть/таймаут) — pendingDepositTxId/Notes намеренно НЕ
                        // очищаем и банкноты НЕ возвращаем: сайт мог и обработать запрос, ответ
                        // просто не дошёл. Если вернуть банкноты сейчас и сайт всё же зачислил —
                        // получится дюп (деньги и там, и здесь). Ждём повторного клика "Внести".
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
    }

    // ── Снять ────────────────────────────────────────────────────────────────

    // Та же логика "неизвестный исход — храним tx_id для повтора", что у депозита. Для снятия
    // это даже критичнее: тут нет ничего похожего на fresh_credited-подстраховку сайта (там
    // не банкноты с serial, а голое число) — единственная защита от двойного списания при
    // повторном клике после обрыва связи ровно в переиспользовании этого tx_id.
    private volatile String pendingWithdrawTxId = null;
    private volatile int pendingWithdrawAmount = 0;

    public void attemptWithdraw(int amount) {
        if (serverPlayer == null) return;
        if (busy) {
            serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }

        String txId;
        int actualAmount;
        if (pendingWithdrawTxId != null) {
            txId = pendingWithdrawTxId;
            actualAmount = pendingWithdrawAmount;
        } else {
            if (amount <= 0) return;
            txId = CrystalAPI.newTxId();
            actualAmount = amount;
        }

        busy = true;
        pendingWithdrawTxId = txId;
        pendingWithdrawAmount = actualAmount;

        String username = serverPlayer.getGameProfile().getName();
        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.processing").withStyle(ChatFormatting.GRAY));

        CrystalAPI.atmWithdrawAsync(txId, username, actualAmount)
                .thenAccept(result -> serverPlayer.getServer().execute(() -> {
                    busy = false;
                    pendingWithdrawTxId = null; // ответ получен — определённый исход
                    if (!result.get("ok").getAsBoolean()) {
                        String error = result.has("error") ? result.get("error").getAsString() : "?";
                        String key = "insufficient_funds".equals(error) ? "crystalmod.atm.insufficient_funds" : "crystalmod.atm.unreachable";
                        serverPlayer.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
                        return;
                    }
                    int total = 0;
                    for (var el : result.getAsJsonArray("notes")) {
                        var n = el.getAsJsonObject();
                        int value = n.get("value").getAsInt();
                        total += value;
                        ItemStack note = CrystalNoteItem.create(n.get("serial").getAsString(), value);
                        if (!serverPlayer.getInventory().add(note)) serverPlayer.drop(note, false);
                    }
                    serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.withdraw_success", total).withStyle(ChatFormatting.GREEN));
                }))
                .exceptionally(ex -> {
                    serverPlayer.getServer().execute(() -> {
                        busy = false;
                        // Исход НЕИЗВЕСТЕН — pendingWithdrawTxId намеренно НЕ очищаем, следующий клик
                        // "Снять" повторит РОВНО этот же запрос (см. комментарий у поля выше).
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
    }
}
