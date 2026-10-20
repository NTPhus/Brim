package com.brim.game.config;

import com.brim.game.game.warlords.WarlordsGame;
import com.brim.game.model.dto.GameType;
import com.brim.game.runtime.TurnBasedGameRegistry.Definition;
import com.brim.game.service.WarlordsProfileService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WarlordsModules {
    @Bean org.springframework.beans.factory.InitializingBean validateWarlordsTemplates() {
        return ()->{com.brim.game.game.warlords.WarlordsHeroes.ALL.size();com.brim.game.game.warlords.WarlordsCampaign.CHAPTERS.size();};
    }
    @Bean Definition warlordsCampaignModule(WarlordsProfileService profiles,com.brim.game.service.WarlordsCampaignArchive archive) {
        return new Definition(GameType.WARLORDS_CAMPAIGN,(room,difficulty)->new WarlordsGame(room,profiles,archive));
    }
    @Bean Definition warlordsDuelModule(WarlordsProfileService profiles) {
        return new Definition(GameType.WARLORDS_DUEL,(room,difficulty)->new WarlordsGame(room,true,profiles));
    }
}
