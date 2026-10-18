package com.brim.game.config;

import com.brim.game.game.warlords.WarlordsGame;
import com.brim.game.model.dto.GameType;
import com.brim.game.runtime.TurnBasedGameRegistry.Definition;
import com.brim.game.service.WarlordsProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WarlordsModules {
    @Bean Definition warlordsCampaignModule(WarlordsProfileService profiles) {
        return new Definition(GameType.WARLORDS_CAMPAIGN,(room,difficulty)->new WarlordsGame(room,false,profiles));
    }
    @Bean Definition warlordsDuelModule(WarlordsProfileService profiles) {
        return new Definition(GameType.WARLORDS_DUEL,(room,difficulty)->new WarlordsGame(room,true,profiles));
    }
}
