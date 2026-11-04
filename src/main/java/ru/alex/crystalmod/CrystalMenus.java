package ru.alex.crystalmod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CrystalMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CrystalMod.MOD_ID);

    // РћР±С‹С‡РЅС‹Р№ MenuType (РЅРµ IMenuTypeExtension) вЂ” РїСЂРё РѕС‚РєСЂС‹С‚РёРё РЅРµ РїРµСЂРµРґР°С‘Рј РЅРёРєР°РєРёС…
    // РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹С… РґР°РЅРЅС‹С… (РЅРµС‚ BlockPos/BlockEntity), РєРѕРЅСЃС‚СЂСѓРєС‚РѕСЂСѓ РґРѕСЃС‚Р°С‚РѕС‡РЅРѕ
    // СЃС‚Р°РЅРґР°СЂС‚РЅС‹С… (int containerId, Inventory playerInventory).
    public static final DeferredHolder<MenuType<?>, MenuType<CrystalATMMenu>> ATM_MENU =
            MENUS.register("atm", () -> new MenuType<>(CrystalATMMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketListingMenu>> MARKET_LISTING_MENU =
            MENUS.register("market_listing", () -> new MenuType<>(MarketListingMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<MarketStallMenu>> MARKET_STALL_MENU =
            MENUS.register("market_stall", () -> net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(
                    (id,inv,buf) -> new MarketStallMenu(id,inv,buf.readBlockPos(),net.minecraft.world.item.ItemStack.OPTIONAL_STREAM_CODEC.decode(buf))));

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
