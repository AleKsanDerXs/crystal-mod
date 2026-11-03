package ru.alex.crystalmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CrystalMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CrystalMod.MOD_ID);

    // Обычный MenuType (не IMenuTypeExtension) — при открытии не передаём никаких
    // дополнительных данных (нет BlockPos/BlockEntity), конструктору достаточно
    // стандартных (int containerId, Inventory playerInventory).
    public static final DeferredHolder<MenuType<?>, MenuType<CrystalATMMenu>> ATM_MENU =
            MENUS.register("atm", () -> new MenuType<>(CrystalATMMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketListingMenu>> MARKET_LISTING_MENU =
            MENUS.register("market_listing", () -> new MenuType<>(MarketListingMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketStallMenu>> MARKET_STALL_MENU =
            MENUS.register("market_stall", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id,inv,buf) -> new MarketStallMenu(id,inv,buf.readBlockPos())));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
