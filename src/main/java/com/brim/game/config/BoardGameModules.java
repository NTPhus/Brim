package com.brim.game.config;

import com.brim.game.game.chess.ChessGame;
import com.brim.game.game.chinesechess.ChineseChessGame;
import com.brim.game.game.connectfour.ConnectFourGame;
import com.brim.game.model.dto.GameType;
import com.brim.game.runtime.TurnBasedGameRegistry.Definition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BoardGameModules {
    @Bean Definition caroModule() { return new Definition(GameType.CARO, com.brim.game.game.caro.CaroGame::new); }
    @Bean Definition othelloModule() { return new Definition(GameType.OTHELLO, com.brim.game.game.othello.OthelloGame::new); }
    @Bean Definition battleshipModule() { return new Definition(GameType.BATTLESHIP, com.brim.game.game.battleship.BattleshipGame::new); }
    @Bean Definition matchThreeModule() { return new Definition(GameType.MATCH_THREE, com.brim.game.game.matchthree.MatchThreeGame::new); }
    @Bean Definition bombermanModule() { return new Definition(GameType.BOMBERMAN, com.brim.game.game.bomberman.BombermanGame::new); }
    @Bean Definition connectFourModule() { return new Definition(GameType.CONNECT_FOUR, ConnectFourGame::new); }
    @Bean Definition chessModule() { return new Definition(GameType.CHESS, ChessGame::new); }
    @Bean Definition xiangqiModule() { return new Definition(GameType.CHINESE_CHESS, ChineseChessGame::new); }
}
