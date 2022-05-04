package me.hsgamer.epicmegagames.config;

import me.hsgamer.hscore.config.PathableConfig;
import me.hsgamer.hscore.config.path.ConfigPath;
import me.hsgamer.hscore.config.path.impl.Paths;
import me.hsgamer.hscore.config.simplixstorage.YamlProvider;

import java.io.File;

public class MainConfig extends PathableConfig {
    public static final ConfigPath<String> SERVER_IP = Paths.stringPath("server.ip", "localhost");
    public static final ConfigPath<Integer> SERVER_PORT = Paths.integerPath("server.port", 25565);
    public static final ConfigPath<Boolean> SERVER_ONLINE_MODE = Paths.booleanPath("server.online-mode", true);
    public static final ConfigPath<Boolean> BUNGEE = Paths.booleanPath("server.bungee", false);
    public static final ConfigPath<Integer> ARENA_PERIOD = Paths.integerPath("arena.period", 1);
    public static final ConfigPath<Boolean> ARENA_ASYNC = Paths.booleanPath("arena.async", true);

    public MainConfig() {
        super(new YamlProvider().loadConfiguration(new File("config.yml")));
    }
}
