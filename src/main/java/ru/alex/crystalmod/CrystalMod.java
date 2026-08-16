package ru.alex.crystalmod;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import ru.alex.crystalmod.net.AtmDepositPayload;
import ru.alex.crystalmod.net.AtmWithdrawPayload;
import ru.alex.crystalmod.net.BalanceRequestPayload;
import ru.alex.crystalmod.net.BalanceResponsePayload;
import ru.alex.crystalmod.net.CancelListingPayload;
import ru.alex.crystalmod.net.CancelListingResultPayload;
import ru.alex.crystalmod.net.CrystalHandshakePayload;
import ru.alex.crystalmod.net.ListItemPayload;
import ru.alex.crystalmod.net.MarketBrowseRequestPayload;
import ru.alex.crystalmod.net.MarketBrowseResponsePayload;
import ru.alex.crystalmod.net.MarketBuyPayload;
import ru.alex.crystalmod.net.MarketBuyResultPayload;
import ru.alex.crystalmod.net.MyListingsRequestPayload;
import ru.alex.crystalmod.net.MyListingsResponsePayload;
import ru.alex.crystalmod.net.ServerPayloadHandler;

/**
 * Игровая часть валюты «Хрусталики» сайта Runes & Rails: предмет-банкнота
 * (см. {@link CrystalNoteItem}), команды (см. {@link CrystalCommands}),
 * банкомат и терминал рынка (см. {@link CrystalATMMenu}, {@link MarketListingMenu})
 * и клиент сайта (см. {@link CrystalAPI}).
 */
@Mod("crystalmod")
public class CrystalMod {

    public static final String MOD_ID = "crystalmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CrystalMod(IEventBus modBus) {
        ConfigManager.loadConfig();
        CrystalItems.register(modBus);
        CrystalMenus.register(modBus);
        modBus.addListener(this::registerPayloads);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ru.alex.crystalmod.client.CrystalModClient.init(modBus);
        }

        LOGGER.info("CrystalMod: инициализация завершена. Сайт: {}", ConfigManager.CONFIG.apiBaseUrl);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Обязательный (не .optional()) канал — заставляет NeoForge требовать мод на обеих сторонах.
        registrar.playBidirectional(
                CrystalHandshakePayload.TYPE,
                CrystalHandshakePayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        (payload, context) -> { /* no-op: канал нужен только для проверки наличия */ },
                        (payload, context) -> { /* no-op */ }
                )
        );
        registrar.playToServer(AtmDepositPayload.TYPE, AtmDepositPayload.STREAM_CODEC, ServerPayloadHandler::handleAtmDeposit);
        registrar.playToServer(AtmWithdrawPayload.TYPE, AtmWithdrawPayload.STREAM_CODEC, ServerPayloadHandler::handleAtmWithdraw);
        registrar.playToServer(ListItemPayload.TYPE, ListItemPayload.STREAM_CODEC, ServerPayloadHandler::handleListItem);
        registrar.playToServer(MyListingsRequestPayload.TYPE, MyListingsRequestPayload.STREAM_CODEC, ServerPayloadHandler::handleMyListingsRequest);
        registrar.playToServer(MarketBrowseRequestPayload.TYPE, MarketBrowseRequestPayload.STREAM_CODEC, ServerPayloadHandler::handleMarketBrowseRequest);
        registrar.playToServer(MarketBuyPayload.TYPE, MarketBuyPayload.STREAM_CODEC, ServerPayloadHandler::handleMarketBuy);
        registrar.playToServer(CancelListingPayload.TYPE, CancelListingPayload.STREAM_CODEC, ServerPayloadHandler::handleCancelListing);
        registrar.playToServer(BalanceRequestPayload.TYPE, BalanceRequestPayload.STREAM_CODEC, ServerPayloadHandler::handleBalanceRequest);

        // playToClient(...) — тип/кодек/optional-флаг ОБЯЗАНЫ совпадать на обеих сторонах: при
        // рукопожатии NetworkComponentNegotiator сравнивает список зарегистрированных каналов
        // клиента и сервера, и если у одной из сторон канал не-optional, а у другой его вообще
        // нет — соединение рвётся ("Incompatible client!", проверено на живом сервере — раньше
        // тут был if (Dist.CLIENT), сервер эти 5 каналов вообще не регистрировал, и ВСЕ игроки
        // переставали заходить). Поэтому регистрируем канал одинаково всегда, но настоящий
        // обработчик (метод-референс в ClientPayloadHandler — а тот трогает Screen-наследников,
        // недоступных на DEDICATED_SERVER, см. предыдущий коммит) подставляем только на клиенте;
        // на сервере — обработчик-пустышка без единой ссылки на клиентские классы, который в
        // реальности никогда не вызовется (сервер playToClient-пакеты не получает, только шлёт).
        boolean client = FMLEnvironment.dist == Dist.CLIENT;
        registrar.playToClient(MyListingsResponsePayload.TYPE, MyListingsResponsePayload.STREAM_CODEC,
                client ? ru.alex.crystalmod.client.ClientPayloadHandler::handleMyListingsResponse : (payload, ctx) -> {});
        registrar.playToClient(MarketBrowseResponsePayload.TYPE, MarketBrowseResponsePayload.STREAM_CODEC,
                client ? ru.alex.crystalmod.client.ClientPayloadHandler::handleMarketBrowseResponse : (payload, ctx) -> {});
        registrar.playToClient(MarketBuyResultPayload.TYPE, MarketBuyResultPayload.STREAM_CODEC,
                client ? ru.alex.crystalmod.client.ClientPayloadHandler::handleMarketBuyResult : (payload, ctx) -> {});
        registrar.playToClient(CancelListingResultPayload.TYPE, CancelListingResultPayload.STREAM_CODEC,
                client ? ru.alex.crystalmod.client.ClientPayloadHandler::handleCancelListingResult : (payload, ctx) -> {});
        registrar.playToClient(BalanceResponsePayload.TYPE, BalanceResponsePayload.STREAM_CODEC,
                client ? ru.alex.crystalmod.client.ClientPayloadHandler::handleBalanceResponse : (payload, ctx) -> {});
    }
}
