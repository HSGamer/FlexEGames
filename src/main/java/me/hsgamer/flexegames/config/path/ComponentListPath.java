package me.hsgamer.flexegames.config.path;

import me.hsgamer.hscore.common.CollectionUtils;
import me.hsgamer.hscore.config.Config;
import me.hsgamer.hscore.config.path.AdvancedConfigPath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ComponentListPath extends AdvancedConfigPath<List<String>, List<Component>> {
    public ComponentListPath(@NotNull String path, @Nullable List<Component> def) {
        super(path, def);
    }

    @Override
    public @Nullable List<String> getFromConfig(@NotNull Config config) {
        Object o = config.get(getPath());
        if (o == null) {
            return null;
        } else {
            return CollectionUtils.createStringListFromObject(o, true);
        }
    }

    @Override
    public @Nullable List<Component> convert(@NotNull List<String> rawValue) {
        return rawValue.stream()
                .map(s -> LegacyComponentSerializer.legacyAmpersand().deserialize(s))
                .map(Component::asComponent)
                .toList();
    }

    @Override
    public @Nullable List<String> convertToRaw(@NotNull List<Component> value) {
        return value.stream()
                .map(LegacyComponentSerializer.legacyAmpersand()::serialize)
                .toList();
    }
}
