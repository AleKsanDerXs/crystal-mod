package ru.alex.crystalmod.client;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import ru.alex.crystalmod.CrystalMenus;
import ru.alex.crystalmod.net.BalanceResponsePayload;
import ru.alex.crystalmod.net.CancelListingResultPayload;
import ru.alex.crystalmod.net.MarketBrowseResponsePayload;
import ru.alex.crystalmod.net.MarketBuyResultPayload;
import ru.alex.crystalmod.net.MyListingsResponsePayload;

/**
 * Клиентская инициализация: регистрация экранов для меню + client-bound сетевых
 * пакетов. Отдельный подпакет {@code client} — единственное отступление от
 * плоского {@code ru.alex.crystalmod} во всём моде, и это не стиль, а требование
 * платформы: классы {@code Screen}/{@code EditBox} не существуют на выделенном
 * сервере, их нельзя даже class-load'ить из кода, который сервер выполняет
 * безусловно.
 *
 * <p>{@code registerClientPayloads} специально вынесен сюда, а не остался в
 * {@code CrystalMod.registerPayloads}: метод-референс {@code ClientPayloadHandler::method}
 * (в отличие от обычного вызова) идёт через {@code invokedynamic}/{@code LambdaMetafactory},
 * а это требует ПРЯМО СЕЙЧАС загрузить и верифицировать сам класс
 * {@code ClientPayloadHandler} — а в нём есть код, ссылающийся на {@code Screen}-наследников.
 * На выделенном сервере это падает {@code BootstrapMethodError: Attempted to load
 * class .../Screen for invalid dist DEDICATED_SERVER} (поймано на живом сервере,
 * см. crash report). Вызывающая сторона ({@code CrystalMod}) гейтит и {@code init()},
 * и {@code registerClientPayloads} за {@code Dist.CLIENT} — тогда сервер вообще не
 * выполняет этот код и не резолвит {@code CrystalModClient}/{@code ClientPayloadHandler}.
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

    public static void registerClientPayloads(PayloadRegistrar registrar) {
        registrar.playToClient(MyListingsResponsePayload.TYPE, MyListingsResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleMyListingsResponse);
        registrar.playToClient(MarketBrowseResponsePayload.TYPE, MarketBrowseResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleMarketBrowseResponse);
        registrar.playToClient(MarketBuyResultPayload.TYPE, MarketBuyResultPayload.STREAM_CODEC,
                ClientPayloadHandler::handleMarketBuyResult);
        registrar.playToClient(CancelListingResultPayload.TYPE, CancelListingResultPayload.STREAM_CODEC,
                ClientPayloadHandler::handleCancelListingResult);
        registrar.playToClient(BalanceResponsePayload.TYPE, BalanceResponsePayload.STREAM_CODEC,
                ClientPayloadHandler::handleBalanceResponse);
    }
}
