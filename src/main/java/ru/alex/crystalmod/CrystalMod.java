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
import ru.alex.crystalmod.net.CancelListingPayload;
import ru.alex.crystalmod.net.CrystalHandshakePayload;
import ru.alex.crystalmod.net.ListItemPayload;
import ru.alex.crystalmod.net.MarketBrowseRequestPayload;
import ru.alex.crystalmod.net.MarketBuyPayload;
import ru.alex.crystalmod.net.MyListingsRequestPayload;
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

        // playToClient(...) со ссылками на ClientPayloadHandler — только на клиенте,
        // см. javadoc CrystalModClient.registerClientPayloads про то, почему.
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ru.alex.crystalmod.client.CrystalModClient.registerClientPayloads(registrar);
        }
    }
}
