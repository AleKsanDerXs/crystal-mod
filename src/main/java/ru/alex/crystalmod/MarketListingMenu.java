package ru.alex.crystalmod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Терминал рынка: один входной слот — сколько положили, столько и выставится
 * (registry_id + весь stack.getCount() как quantity). Без BlockEntity, как и
 * банкомат — при закрытии меню несписанный предмет возвращается ({@link #removed}).
 */
public class MarketListingMenu extends AbstractContainerMenu {

    private static final int INPUT_SLOT = 0;
    private static final int INPUT_X = 80;
    private static final int INPUT_Y = 20;

    private final SimpleContainer input = new SimpleContainer(1);
    private final ServerPlayer serverPlayer;

    private volatile boolean busy = false;
    // Тот же принцип "неизвестный исход — переиспользуем tx_id", что у банкомата.
    private volatile String pendingTxId = null;
    private volatile ItemStack pendingStack = ItemStack.EMPTY;
    private volatile int pendingPrice = 0;

    public MarketListingMenu(int containerId, Inventory playerInventory) {
        super(CrystalMenus.MARKET_LISTING_MENU.get(), containerId);
        this.serverPlayer = playerInventory.player instanceof ServerPlayer sp ? sp : null;

        this.addSlot(new Slot(input, INPUT_SLOT, INPUT_X, INPUT_Y));

        // Y-координаты на 24px ниже "стандартных" 90/148 — под кнопкой «Каталог рынка»
        // в свободной строке MarketListingScreen (см. её javadoc про рост imageHeight).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 114 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 172));
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

        if (index == INPUT_SLOT) {
            if (!this.moveItemStackTo(original, 1, this.slots.size(), true)) return ItemStack.EMPTY;
        } else {
            if (!this.moveItemStackTo(original, INPUT_SLOT, 1, false)) return ItemStack.EMPTY;
        }

        if (original.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) return;
        ItemStack stack = input.getItem(INPUT_SLOT);
        if (!stack.isEmpty()) player.getInventory().placeItemBackInInventory(stack);
    }

    public void attemptList(int pricePerUnit) {
        if (serverPlayer == null) return;
        if (busy) {
            serverPlayer.sendSystemMessage(Component.translatable("crystalmod.atm.busy").withStyle(ChatFormatting.YELLOW));
            return;
        }

        String txId;
        ItemStack stack;
        int price;

        if (pendingTxId != null) {
            txId = pendingTxId;
            stack = pendingStack;
            price = pendingPrice;
        } else {
            if (pricePerUnit <= 0) {
                serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market_block.invalid_price").withStyle(ChatFormatting.RED));
                return;
            }
            ItemStack current = input.getItem(INPUT_SLOT);
            if (current.isEmpty()) {
                serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market_block.empty_slot").withStyle(ChatFormatting.RED));
                return;
            }
            stack = current.copy();
            input.setItem(INPUT_SLOT, ItemStack.EMPTY);
            txId = CrystalAPI.newTxId();
            price = pricePerUnit;
        }

        busy = true;
        pendingTxId = txId;
        pendingStack = stack;
        pendingPrice = price;

        String username = serverPlayer.getGameProfile().getName();
        String registryId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int quantity = stack.getCount();
        // Зачарования/имя/содержимое рюкзака и т.п. — иначе покупатель получит "голый" предмет.
        String itemData = ItemDataCodec.encode(stack, serverPlayer.registryAccess());
        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market.listing_pending").withStyle(ChatFormatting.GRAY));

        CrystalAPI.createListingDirectAsync(txId, username, registryId, quantity, price, itemData)
                .thenAccept(result -> serverPlayer.getServer().execute(() -> {
                    busy = false;
                    pendingTxId = null; // определённый исход
                    if (result.get("ok").getAsBoolean()) {
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market.listing_created",
                                quantity, registryId, price).withStyle(ChatFormatting.GREEN));
                    } else {
                        if (!serverPlayer.getInventory().add(pendingStack)) serverPlayer.drop(pendingStack, false);
                        String reason = result.has("error") ? result.get("error").getAsString() : "?";
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market.listing_failed", reason).withStyle(ChatFormatting.RED));
                    }
                }))
                .exceptionally(ex -> {
                    serverPlayer.getServer().execute(() -> {
                        busy = false;
                        // Неизвестный исход — предмет НЕ возвращаем (мог уже стать лотом на сайте),
                        // pendingTxId остаётся для повтора тем же tx_id по следующему клику.
                        serverPlayer.sendSystemMessage(Component.translatable("crystalmod.market.unreachable").withStyle(ChatFormatting.RED));
                    });
                    return null;
                });
    }
}
