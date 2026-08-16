package ru.alex.crystalmod;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ConfigManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_DIR = Paths.get("config/Xs_Crystal");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config CONFIG = new Config();

    public static void loadConfig() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                saveConfig(); // создаём дефолтный файл
            }
            String json = Files.readString(CONFIG_FILE, StandardCharsets.UTF_8);
            Config loaded = GSON.fromJson(json, Config.class);
            if (loaded != null) CONFIG = loaded;
            // Поля, которых не было в файле (например, из более старой версии мода), Gson тихо
            // оставляет со значением по умолчанию из класса Config, но сам файл на диске остаётся
            // без них. Дописываем их явно, чтобы файл всегда отражал реальные действующие значения.
            saveConfig();
            LOGGER.info("CrystalMod: конфигурация загружена из {}", CONFIG_FILE);
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("CrystalMod: ошибка при загрузке конфига", e);
        }
    }

    public static void saveConfig() {
        try {
            String json = GSON.toJson(CONFIG);
            Files.writeString(CONFIG_FILE, json, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOGGER.error("CrystalMod: ошибка при сохранении конфига", e);
        }
    }

    public static class Config {
        // Тот же сайт и тот же ключ, что и у AuthMod (config/Xs_Auth) — можно взять готовые значения оттуда.
        public String apiBaseUrl = "https://runes-and-rails.ru";
        public String apiSecret = "change_this_to_the_site_api_secret";
    }
}
