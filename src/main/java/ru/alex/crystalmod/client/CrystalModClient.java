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
 * <p>Client-bound сетевые пакеты (см. {@link ClientPayloadHandler}) регистрируются
 * НЕ здесь, а прямо в {@code CrystalMod.registerPayloads} — канал обязан быть
 * зарегистрирован ОДИНАКОВО на клиенте и сервере (иначе рукопожатие рвётся,
 * "Incompatible client!", поймано на живом сервере), а вот какой ИМЕННО
 * обработчик подставить (настоящий на клиенте / пустышка на сервере) уже
 * решается через {@code Dist.CLIENT} прямо на месте регистрации — см. javadoc
 * там.
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
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(ru.alex.crystalmod.CrystalItems.MARKET_STALL_ENTITY.get(), MarketStallRenderer::new));
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CrystalMenus.ATM_MENU.get(), CrystalATMScreen::new);
        event.register(CrystalMenus.MARKET_STALL_MENU.get(), MarketStallScreen::new);
        event.register(CrystalMenus.MARKET_LISTING_MENU.get(), MarketListingScreen::new);
    }
}
