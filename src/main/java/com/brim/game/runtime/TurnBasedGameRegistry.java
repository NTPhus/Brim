package com.brim.game.runtime;

import com.brim.game.model.dto.GameType;
import com.brim.game.service.TurnBasedGameService.TurnBasedGame;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.function.BiFunction;

@Component
public class TurnBasedGameRegistry {
    public record Definition(GameType type, BiFunction<String, String, TurnBasedGame> factory) {}
    private final Map<GameType, Definition> definitions = new EnumMap<>(GameType.class);
    public TurnBasedGameRegistry(List<Definition> modules) {
        for (Definition module : modules) {
            if (definitions.putIfAbsent(module.type(), module) != null) throw new IllegalArgumentException("Duplicate game: " + module.type());
        }
    }
    public TurnBasedGame create(GameType type, String roomId, String difficulty) {
        Definition module = definitions.get(type);
        if (module == null) throw new IllegalArgumentException("No turn-based module: " + type);
        return module.factory().apply(roomId, difficulty);
    }
}
