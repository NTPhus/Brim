package com.brim.game.config;

import com.brim.game.game.chess.ChessGame;
import com.brim.game.game.chinesechess.ChineseChessGame;
import com.brim.game.model.dto.GameType;
import com.brim.game.runtime.TurnBasedGameRegistry.Definition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BoardGameModules {
    @Bean Definition chessModule() { return new Definition(GameType.CHESS, ChessGame::new); }
    @Bean Definition xiangqiModule() { return new Definition(GameType.CHINESE_CHESS, ChineseChessGame::new); }
}
