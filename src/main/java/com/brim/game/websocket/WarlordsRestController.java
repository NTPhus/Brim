package com.brim.game.websocket;

import com.brim.game.game.warlords.*;
import com.brim.game.security.GuestSession;
import com.brim.game.service.WarlordsProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/warlords")
@RequiredArgsConstructor
public class WarlordsRestController {
    private final WarlordsProfileService profiles;
    private final com.brim.game.service.WarlordsCampaignArchive archive;
    public record Hub(WarlordsProfile profile,List<WarlordsCampaign.Chapter> chapters,List<WarlordsGame.Skill> skills,List<WarlordsHeroes.Hero> heroes,com.brim.game.service.WarlordsCampaignArchive.Summary savedCampaign) {}
    public record Upgrade(@NotBlank @Size(max=8) String type) {}
    public record HeroSelection(@NotBlank @Size(max=32) String heroId) {}
    @GetMapping
    public Hub hub(HttpServletRequest request) {
        String id=GuestSession.require(request.getSession(false)).id();
        return new Hub(profiles.load(id),WarlordsCampaign.CHAPTERS,WarlordsGame.SKILLS,WarlordsHeroes.ALL,archive.summary(id));
    }
    @PostMapping("/hero")
    public ResponseEntity<?> selectHero(@Valid @RequestBody HeroSelection selection,HttpServletRequest request) {
        String id=GuestSession.require(request.getSession(false)).id();
        try {return ResponseEntity.ok(profiles.selectHero(id,selection.heroId()));}
        catch(IllegalArgumentException invalid) {return ResponseEntity.badRequest().body(Map.of("message",invalid.getMessage()));}
    }
    @PostMapping("/upgrade")
    public ResponseEntity<?> upgrade(@Valid @RequestBody Upgrade upgrade,HttpServletRequest request) {
        String id=GuestSession.require(request.getSession(false)).id();
        try {return ResponseEntity.ok(profiles.upgrade(id,upgrade.type()));}
        catch(IllegalArgumentException invalid) {return ResponseEntity.badRequest().body(Map.of("message",invalid.getMessage()));}
    }
}
