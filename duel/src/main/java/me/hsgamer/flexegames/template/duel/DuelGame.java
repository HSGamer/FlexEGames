package me.hsgamer.flexegames.template.duel;

import io.github.bloepiloepi.pvp.events.EntityPreDeathEvent;
import io.github.bloepiloepi.pvp.events.FinalDamageEvent;
import io.github.bloepiloepi.pvp.events.PlayerExhaustEvent;
import me.hsgamer.flexegames.api.game.ArenaGame;
import me.hsgamer.flexegames.api.game.JoinResponse;
import me.hsgamer.flexegames.api.game.Template;
import me.hsgamer.flexegames.builder.ItemBuilder;
import me.hsgamer.flexegames.feature.LobbyFeature;
import me.hsgamer.flexegames.manager.ReplacementManager;
import me.hsgamer.flexegames.state.EndingState;
import me.hsgamer.flexegames.state.InGameState;
import me.hsgamer.flexegames.state.WaitingState;
import me.hsgamer.flexegames.util.*;
import me.hsgamer.hscore.minestom.board.Board;
import me.hsgamer.minigamecore.base.Arena;
import me.hsgamer.minigamecore.implementation.feature.arena.ArenaTimerFeature;
import me.hsgamer.minigamecore.implementation.feature.single.TimerFeature;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.AddEntityToInstanceEvent;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.EntityEvent;
import net.minestom.server.instance.IChunkLoader;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.ExecutionType;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class DuelGame implements ArenaGame {
    private final DuelTemplate template;
    private final Arena arena;
    private final TimerFeature timerFeature;
    private final InstanceContainer instance;
    private final AtomicBoolean isFinished = new AtomicBoolean(false);
    private final Tag<Boolean> deadTag = Tag.Boolean("duel:dead").defaultValue(false);
    private final Tag<Boolean> playerBlockTag = Tag.Boolean("duel:playerBlock").defaultValue(false);
    private final AtomicReference<Player> winner = new AtomicReference<>();
    private final Board board;
    private final EventNode<EntityEvent> entityEventNode;
    private Task task;

    public DuelGame(DuelTemplate template, Arena arena) {
        this.template = template;
        this.arena = arena;
        this.timerFeature = arena.getArenaFeature(ArenaTimerFeature.class);
        this.instance = MinecraftServer.getInstanceManager().createInstanceContainer(FullBrightDimension.INSTANCE);
        instance.setTimeRate(0);
        instance.setTime(6000);
        instance.getWorldBorder().setCenter((float) template.joinPos.x(), (float) template.joinPos.z());
        instance.getWorldBorder().setDiameter(template.borderDiameter);
        this.board = new Board(
                player -> ReplacementManager.builder()
                        .replaceGlobal()
                        .replace(getReplacements())
                        .replacePlayer(player)
                        .build(template.boardTitle),
                player -> {
                    ReplacementManager.Builder builder = ReplacementManager.builder()
                            .replaceGlobal()
                            .replace(getReplacements())
                            .replacePlayer(player);
                    List<Component> components = Collections.emptyList();
                    if (arena.getState() == WaitingState.class) {
                        components = template.boardLinesWaiting;
                    } else if (arena.getState() == InGameState.class) {
                        components = template.boardLinesIngame;
                    } else if (arena.getState() == EndingState.class) {
                        components = template.boardLinesEnding;
                    }
                    return components.stream().map(builder::build).toList();
                }
        );
        entityEventNode = EventNode.event("entityEvent-" + arena.getName(), EventFilter.ENTITY, entityEvent -> entityEvent.getEntity().getInstance() == instance);
        PvpUtil.applyPvp(entityEventNode, template.useLegacyPvp);
        entityEventNode
                .addListener(EntityPreDeathEvent.class, event -> {
                    if (event.getEntity() instanceof Player player) {
                        event.setCancelled(true);
                        onKill(player);
                    }
                })
                .addListener(PlayerExhaustEvent.class, event -> {
                    if (isFinished.get() || arena.getState() != InGameState.class || Boolean.TRUE.equals(event.getEntity().getTag(deadTag))) {
                        event.setCancelled(true);
                    }
                })
                .addListener(PlayerSpawnEvent.class, event -> event.getPlayer().teleport(template.joinPos))
                .addListener(FinalDamageEvent.class, event -> {
                    if (isFinished.get() || arena.getState() != InGameState.class || Boolean.TRUE.equals(event.getEntity().getTag(deadTag))) {
                        event.setCancelled(true);
                    }
                });
    }

    private void onKill(Player player) {
        player.heal();
        player.setFood(20);
        player.getInventory().clear();
        if (!isFinished.get()) {
            player.setTag(deadTag, true);
            player.setGameMode(GameMode.SPECTATOR);
            player.setInvisible(true);
        }
    }

    @Override
    public Template getTemplate() {
        return template;
    }

    @Override
    public ItemStack getDisplayItem() {
        return ItemBuilder.buildItem(template.gameDisplayItem, getReplacements());
    }

    @Override
    public Collection<Player> getPlayers() {
        return instance.getPlayers();
    }

    private Map<String, Supplier<ComponentLike>> getReplacements() {
        return Map.of(
                "players", () -> Component.text(Integer.toString(getPlayerCount())),
                "time", () -> Component.text(TimeUtil.format(timerFeature.getDuration(TimeUnit.MILLISECONDS))),
                "max-players", () -> Component.text(Integer.toString(template.posList.size())),
                "state", () -> ArenaUtil.getDisplayState(arena),
                "template", () -> template.displayName,
                "owner", () -> ArenaUtil.getDisplayOwner(arena),
                "name", () -> Component.text(arena.getName()),
                "winner", () -> Optional.ofNullable(winner.get()).map(Player::getName).orElse(Component.empty()),
                "alive", () -> Component.text(Integer.toString(getAlivePlayers().size()))
        );
    }

    @Override
    public JoinResponse join(Player player) {
        if (arena.getState() == WaitingState.class) {
            if (instance.getPlayers().size() >= template.posList.size()) {
                return JoinResponse.MAX_PLAYER_REACHED;
            }
            player.setInstance(instance);
            return JoinResponse.SUCCESSFUL_JOIN;
        }
        return JoinResponse.NOT_WAITING;
    }

    @Override
    public void init() {
        boolean setGenerator = true;
        if (template.useWorld) {
            IChunkLoader chunkLoader = template.worldLoader.getLoader(instance, AssetUtil.getWorldFile(template.worldName).toPath());
            if (chunkLoader != null) {
                instance.setChunkLoader(chunkLoader);
                setGenerator = false;
            }
        }
        if (setGenerator) {
            instance.setGenerator(unit -> {
                unit.modifier().fillHeight(0, 1, Block.BEDROCK);
                if (template.maxHeight > 1) {
                    unit.modifier().fillHeight(1, template.maxHeight, Block.GRASS_BLOCK);
                }
            });
        }
        MinecraftServer.getGlobalEventHandler().addChild(entityEventNode);
        instance.eventNode()
                .addListener(AddEntityToInstanceEvent.class, event -> {
                    if (event.getEntity() instanceof Player player) {
                        ArenaUtil.callJoinEvent(arena, player);
                        player.setRespawnPoint(template.joinPos);
                        player.setGameMode(GameMode.SURVIVAL);
                        board.addPlayer(player);
                    }
                })
                .addListener(PlayerMoveEvent.class, event -> {
                    if (!instance.isInVoid(event.getNewPosition())) return;
                    event.setNewPosition(template.joinPos);
                    if (arena.getState() == InGameState.class) {
                        onKill(event.getPlayer());
                    }
                })
                .addListener(RemoveEntityFromInstanceEvent.class, event -> {
                    if (event.getEntity() instanceof Player player) {
                        ArenaUtil.callLeaveEvent(arena, player);
                        board.removePlayer(player);
                        player.removeTag(deadTag);
                    }
                })
                .addListener(PlayerBlockBreakEvent.class, event -> {
                    if (Boolean.FALSE.equals(event.getBlock().getTag(playerBlockTag))) {
                        event.setCancelled(true);
                    }
                })
                .addListener(PlayerBlockPlaceEvent.class, event -> event.setBlock(event.getBlock().withTag(playerBlockTag, true)));
        task = instance.scheduler()
                .buildTask(board::updateAll)
                .repeat(TaskSchedule.nextTick())
                .executionType(ExecutionType.ASYNC)
                .schedule();
    }

    @Override
    public void postInit() {
        MinecraftServer.getInstanceManager().registerInstance(instance);
    }

    @Override
    public void onWaitingStart() {
        timerFeature.setDuration(template.waitingTime, TimeUnit.SECONDS);
    }

    @Override
    public boolean isWaitingOver() {
        return timerFeature.getDuration(TimeUnit.MILLISECONDS) <= 0;
    }

    @Override
    public boolean canStart() {
        return instance.getPlayers().size() > 1;
    }

    @Override
    public void onFailedWaitingEnd() {
        Component component = template.notEnoughPlayers;
        for (Player player : instance.getPlayers()) {
            player.sendMessage(component);
        }
    }

    @Override
    public void onInGameStart() {
        List<Player> players = new ArrayList<>(instance.getPlayers());
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            giveKit(player);
            Pos pos = template.posList.get(i % template.posList.size());
            player.teleport(pos);
        }
    }

    private void giveKit(Player player) {
        var inventory = player.getInventory();
        template.kit.forEach((slot, item) -> {
            if (slot < 0 || slot >= inventory.getSize()) return;
            player.getInventory().setItemStack(slot, item);
        });
    }

    private List<Player> getAlivePlayers() {
        return instance.getPlayers().stream().filter(player -> Boolean.FALSE.equals(player.tagHandler().getTag(deadTag))).toList();
    }

    private void checkWinner() {
        List<Player> alivePlayers = getAlivePlayers();
        if (alivePlayers.size() <= 1) {
            isFinished.set(true);
            if (alivePlayers.size() == 1) {
                winner.set(alivePlayers.get(0));
            }
        }
    }

    @Override
    public boolean isInGameOver() {
        checkWinner();
        return isFinished.get();
    }

    @Override
    public void onEndingStart() {
        timerFeature.setDuration(template.endingTime, TimeUnit.SECONDS);
        Player winnerPlayer = winner.get();
        Component message;
        if (winnerPlayer != null) {
            message = ReplacementManager.replace(template.winnerMessage, getReplacements());
        } else {
            message = template.noWinnerMessage;
        }
        for (Player player : instance.getPlayers()) {
            player.getInventory().clear();
            if (winnerPlayer != null) {
                player.sendMessage(message);
            }
        }
    }

    @Override
    public boolean isEndingOver() {
        return timerFeature.getDuration(TimeUnit.MILLISECONDS) <= 0;
    }

    @Override
    public void clear() {
        for (Player player : instance.getPlayers()) {
            arena.getFeature(LobbyFeature.class).backToLobby(player);
        }
        if (task != null) {
            task.cancel();
        }
        MinecraftServer.getGlobalEventHandler().removeChild(entityEventNode);
        MinecraftServer.getInstanceManager().unregisterInstance(instance);
    }
}
