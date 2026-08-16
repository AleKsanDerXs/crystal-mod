package ru.alex.crystalmod.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import ru.alex.crystalmod.CrystalMenus;

/**
 * Клиентская инициализация: регистрация экранов для меню. Отдельный подпакет
 * {@code client} — единственное отступление от плоского {@code ru.alex.crystalmod}
 * во всём моде, и это не стиль, а требование платформы: классы {@code Screen}/
 * {@code EditBox} не существуют на выделенном сервере, их нельзя даже
 * class-load'ить из кода, который сервер выполняет безусловно.
 *
 * <p>{@code RegisterMenuScreensEvent} — событие mod-bus (см. аналогичное
 * предупреждение в {@code ClansModClient} про то, почему его нельзя смешивать
 * с {@code @SubscribeEvent} на общей {@code NeoForge.EVENT_BUS}): регистрируем
 * явным {@code modBus.addListener}, без аннотации.
 */
public final class CrystalModClient {

    private CrystalModClient() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(CrystalModClient::onRegisterMenuScreens);
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CrystalMenus.ATM_MENU.get(), CrystalATMScreen::new);
        event.register(CrystalMenus.MARKET_LISTING_MENU.get(), MarketListingScreen::new);
    }
}
