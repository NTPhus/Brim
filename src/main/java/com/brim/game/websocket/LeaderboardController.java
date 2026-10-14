package com.brim.game.websocket;

import com.brim.game.model.dto.GameType;
import com.brim.game.model.dto.LeaderboardEntry;
import com.brim.game.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    @GetMapping
    public List<LeaderboardEntry> leaderboard(@RequestParam(required = false) GameType gameType,
                                               @RequestParam(defaultValue = "12") int limit) {
        return leaderboardService.getTop(gameType, Math.max(1, Math.min(limit, 50)));
    }
}
