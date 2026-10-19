package com.brim.game.config;

import com.brim.game.game.vietnam.VietnamGame;
import com.brim.game.model.dto.GameType;
import com.brim.game.runtime.TurnBasedGameRegistry.Definition;
import com.brim.game.service.VietnamProgressService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VietnamModules {
    @Bean Definition vietnamModule(VietnamProgressService progress) {
        return new Definition(GameType.VIETNAM_CAMPAIGN,(room,difficulty)->new VietnamGame(progress::unlocked,progress::complete));
    }
}
