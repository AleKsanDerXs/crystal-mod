package ru.alex.crystalmod;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Клиент {@code crystals_api.php?action=deposit} — та же схема, что у
 * {@code AuthAPI.PhpApiClient} (auth-mod): блокирующий {@link HttpClient},
 * вызываемый только из {@link #ASYNC_EXECUTOR}, чтобы не вешать тик сервера.
 */
public class CrystalAPI {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    // Тот же принцип, что и AuthAPI-Async в auth-mod: отдельный пул для блокирующих HTTP-вызовов,
    // инициированных игровыми событиями (в нашем случае — ПКМ по банкноте).
    private static final ExecutorService ASYNC_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "CrystalAPI-Async");
        t.setDaemon(true);
        return t;
    });

    private static class PhpApiClient {
        private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        static HttpResponse<String> postJson(String url, String apiKey, String jsonBody) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Auth-Key", apiKey)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            // Код ответа читаем сами (401/403 — не бросаем, это законный исход депозита,
            // а не "PHP недоступен" — см. depositNote()), в отличие от AuthAPI.PhpApiClient,
            // который на не-200 сразу бросает IOException.
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        }

        static HttpResponse<String> get(String url, String apiKey) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("X-Auth-Key", apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    /** Общая обёртка ошибок авторизации/файрвола — одинакова для всех эндпоинтов X-Auth-Key. */
    private static JsonObject authErrorOrNull(int statusCode) {
        if (statusCode == 401) {
            JsonObject r = new JsonObject();
            r.addProperty("ok", false);
            r.addProperty("error", "unauthorized: проверьте apiSecret в config/Xs_Crystal/config.json");
            return r;
        }
        if (statusCode == 403) {
            JsonObject r = new JsonObject();
            r.addProperty("ok", false);
            r.addProperty("error", "forbidden: IP сервера не в белом списке файрвола API сайта");
            return r;
        }
        return null;
    }

    /**
     * Новый tx_id для НАЧАЛА логической операции банкомата/выставления лота.
     * Вызывающий код обязан сохранить его и переиспользовать при повторных
     * попытках той же операции (после сетевой ошибки) — генерировать новый
     * tx_id на retry нельзя, это ломает дедупликацию на сайте (см.
     * crystalIdempotent() в crystals_common.php).
     */
    public static String newTxId() {
        return java.util.UUID.randomUUID().toString();
    }

    // ── Банкомат / команда deposit: внести (пачка банкнот на сумму + сдача) ────

    /**
     * Возвращает нормализованный {@link JsonObject}: {@code ok} (bool),
     * {@code credited} (int), {@code change} ({@code serial}/{@code value}
     * либо {@code null}), {@code error} (строка, если ok=false).
     * txId — см. {@link #newTxId()}, ОБЯЗАТЕЛЬНО тот же при повторе.
     */
    public static JsonObject atmDeposit(String txId, String username, List<CrystalNoteContents> notes, int targetAmount)
            throws IOException, InterruptedException {
        JsonArray notesArr = new JsonArray();
        for (CrystalNoteContents n : notes) {
            JsonObject note = new JsonObject();
            note.addProperty("serial", n.serial());
            note.addProperty("value", n.value());
            notesArr.add(note);
        }
        JsonObject body = new JsonObject();
        body.addProperty("tx_id", txId);
        body.addProperty("username", username);
        body.addProperty("target_amount", targetAmount);
        body.add("notes", notesArr);

        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_api.php?action=atm_deposit";
        HttpResponse<String> response = PhpApiClient.postJson(url, ConfigManager.CONFIG.apiSecret, body.toString());

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> atmDepositAsync(String txId, String username, List<CrystalNoteContents> notes, int targetAmount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return atmDeposit(txId, username, notes, targetAmount);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при atm_deposit tx={} ({})", txId, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Банкомат: снять (списание с баланса + минт банкнот напрямую) ───────────

    /**
     * {@code ok}/{@code notes} (JsonArray {serial,value}) либо {@code ok=false}/{@code error}
     * (в т.ч. {@code insufficient_funds} — сайт вернул 402, это не сетевая
     * ошибка, а законный отказ, exception НЕ бросается).
     */
    public static JsonObject atmWithdraw(String txId, String username, int amount) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("tx_id", txId);
        body.addProperty("username", username);
        body.addProperty("amount", amount);

        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_api.php?action=atm_withdraw";
        HttpResponse<String> response = PhpApiClient.postJson(url, ConfigManager.CONFIG.apiSecret, body.toString());

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject result = new JsonObject();
        if (response.statusCode() == 402) {
            result.addProperty("ok", false);
            result.addProperty("error", "insufficient_funds");
            return result;
        }

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> atmWithdrawAsync(String txId, String username, int amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return atmWithdraw(txId, username, amount);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при atm_withdraw tx={} ({})", txId, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Проверка подлинности банкноты (/crystal check) ──────────────────────────

    /** {@code status}: genuine | already_deposited | forged (см. crystals_api.php action=verify_note). */
    public static JsonObject verifyNote(String serial, int value) throws IOException, InterruptedException {
        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_api.php?action=verify_note"
                + "&serial=" + java.net.URLEncoder.encode(serial, java.nio.charset.StandardCharsets.UTF_8)
                + "&value=" + value;
        HttpResponse<String> response = PhpApiClient.get(url, ConfigManager.CONFIG.apiSecret);

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> verifyNoteAsync(String serial, int value) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return verifyNote(serial, value);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при verify_note serial={} ({})", serial, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Баланс (мини-плашка над окнами) ─────────────────────────────────────────

    /** {@code ok}/{@code crystals}, либо {@code ok=false}/{@code error}. */
    public static JsonObject balance(String username) throws IOException, InterruptedException {
        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_api.php?action=balance_mod"
                + "&username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = PhpApiClient.get(url, ConfigManager.CONFIG.apiSecret);

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> balanceAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return balance(username);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при balance_mod username={} ({})", username, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Прямое выставление лота (блок рынка / op-команда из сундука) ───────────

    /** {@code ok}/{@code listing_id}/{@code status}, либо {@code ok=false}/{@code error}. */
    public static JsonObject createListingDirect(String txId, String username, String registryId, int quantity, int pricePerUnit,
            String itemData, List<String> tooltipLines) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("tx_id", txId);
        body.addProperty("username", username);
        body.addProperty("registry_id", registryId);
        body.addProperty("quantity", quantity);
        body.addProperty("price_per_unit", pricePerUnit);
        body.addProperty("item_data", itemData);
        JsonArray tooltipArr = new JsonArray();
        tooltipLines.forEach(tooltipArr::add);
        body.add("tooltip_lines", tooltipArr);

        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_market_api.php?action=create_listing_direct";
        HttpResponse<String> response = PhpApiClient.postJson(url, ConfigManager.CONFIG.apiSecret, body.toString());

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> createListingDirectAsync(String txId, String username, String registryId, int quantity,
            int pricePerUnit, String itemData, List<String> tooltipLines) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return createListingDirect(txId, username, registryId, quantity, pricePerUnit, itemData, tooltipLines);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при create_listing_direct tx={} ({})", txId, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Мои лоты (кнопка «Мои лоты» в торговом терминале) ───────────────────────

    /** {@code ok}/{@code listings} (JsonArray {registry_id, quantity_available, quantity_total, price_per_unit, status}). */
    public static JsonObject myListings(String username) throws IOException, InterruptedException {
        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_market_api.php?action=my_listings_mod"
                + "&username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> response = PhpApiClient.get(url, ConfigManager.CONFIG.apiSecret);

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> myListingsAsync(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return myListings(username);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при my_listings_mod username={} ({})", username, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    // ── Каталог рынка (торговый терминал — просмотр/покупка) ────────────────────

    /** {@code ok}/{@code items} (JsonArray)/{@code total}/{@code page}/{@code per_page}. */
    public static JsonObject marketList(String search, String sort, int page, int perPage) throws IOException, InterruptedException {
        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_market_api.php?action=list_mod"
                + "&search=" + java.net.URLEncoder.encode(search, java.nio.charset.StandardCharsets.UTF_8)
                + "&sort=" + java.net.URLEncoder.encode(sort, java.nio.charset.StandardCharsets.UTF_8)
                + "&page=" + page + "&per_page=" + perPage;
        HttpResponse<String> response = PhpApiClient.get(url, ConfigManager.CONFIG.apiSecret);

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> marketListAsync(String search, String sort, int page, int perPage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return marketList(search, sort, page, perPage);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при list_mod ({})", e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Купить quantity единиц лота listingId. Подписки не требует — гейт
     * Subscription::active() в crystals_market_api.php действует только для
     * покупки с сайта (action=buy), не для action=buy_mod. {@code ok}/
     * {@code trade_id}/{@code total}/{@code delivered}, либо {@code ok=false}/
     * {@code error} — законный отказ (лот распродан/недоступен), не сетевая ошибка.
     */
    public static JsonObject buyListing(String txId, String username, int listingId, int quantity) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("tx_id", txId);
        body.addProperty("username", username);
        body.addProperty("id", listingId);
        body.addProperty("quantity", quantity);

        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_market_api.php?action=buy_mod";
        HttpResponse<String> response = PhpApiClient.postJson(url, ConfigManager.CONFIG.apiSecret, body.toString());

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> buyListingAsync(String txId, String username, int listingId, int quantity) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return buyListing(txId, username, listingId, quantity);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при buy_mod tx={} ({})", txId, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }

    /**
     * Отменить свой лот (кнопка «Отменить» в «Мои лоты»). {@code ok}/{@code status}/
     * {@code return_delivered}, при непроданном остатке ещё {@code item_data}/
     * {@code registry_id}/{@code quantity} — мод сам кладёт остаток в инвентарь.
     */
    public static JsonObject cancelListing(String username, int listingId) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("id", listingId);

        String url = ConfigManager.CONFIG.apiBaseUrl.replaceAll("/+$", "") + "/crystals_market_api.php?action=cancel_listing_mod";
        HttpResponse<String> response = PhpApiClient.postJson(url, ConfigManager.CONFIG.apiSecret, body.toString());

        JsonObject authErr = authErrorOrNull(response.statusCode());
        if (authErr != null) return authErr;

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("ok") || !json.get("ok").getAsBoolean()) {
            JsonObject result = new JsonObject();
            result.addProperty("ok", false);
            result.addProperty("error", json != null && json.has("error") ? json.get("error").getAsString() : "unknown_error");
            return result;
        }
        JsonObject data = json.getAsJsonObject("data");
        data.addProperty("ok", true);
        return data;
    }

    public static CompletableFuture<JsonObject> cancelListingAsync(String username, int listingId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return cancelListing(username, listingId);
            } catch (IOException | InterruptedException e) {
                LOGGER.warn("CrystalAPI: сайт недоступен при cancel_listing_mod id={} ({})", listingId, e.getMessage());
                throw new java.util.concurrent.CompletionException(e);
            }
        }, ASYNC_EXECUTOR);
    }
}
