package me.hsgamer.flexegames.state;

import me.hsgamer.flexegames.api.game.ArenaGame;
import me.hsgamer.flexegames.config.MessageConfig;
import me.hsgamer.flexegames.feature.GameFeature;
import me.hsgamer.minigamecore.base.Arena;
import me.hsgamer.minigamecore.base.GameState;

public class WaitingState implements GameState {
    @Override
    public void start(Arena arena) {
        ArenaGame arenaGame = arena.getArenaFeature(GameFeature.class).getGame();
        arenaGame.postInit();
        arenaGame.onWaitingStart();
    }

    @Override
    public void update(Arena arena) {
        ArenaGame arenaGame = arena.getArenaFeature(GameFeature.class).getGame();
        if (arenaGame.isWaitingOver()) {
            if (arenaGame.canStart()) {
                arena.setNextState(InGameState.class);
            } else {
                arena.setNextState(KillingState.class);
            }
        }
    }

    @Override
    public void end(Arena arena) {
        ArenaGame arenaGame = arena.getArenaFeature(GameFeature.class).getGame();
        if (arenaGame.canStart()) {
            arenaGame.onWaitingEnd();
        } else {
            arenaGame.onFailedWaitingEnd();
        }
    }

    @Override
    public String getDisplayName() {
        return MessageConfig.STATE_WAITING.getValue();
    }
}
