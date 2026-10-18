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
    public record Hub(WarlordsProfile profile,List<WarlordsCampaign.Chapter> chapters,List<WarlordsGame.Skill> skills) {}
    public record Upgrade(@NotBlank @Size(max=8) String type) {}
    @GetMapping
    public Hub hub(HttpServletRequest request) {
        return new Hub(profiles.load(GuestSession.require(request.getSession(false)).id()),WarlordsCampaign.CHAPTERS,WarlordsGame.SKILLS);
    }
    @PostMapping("/upgrade")
    public ResponseEntity<?> upgrade(@Valid @RequestBody Upgrade upgrade,HttpServletRequest request) {
        String id=GuestSession.require(request.getSession(false)).id();
        try {return ResponseEntity.ok(profiles.upgrade(id,upgrade.type()));}
        catch(IllegalArgumentException invalid) {return ResponseEntity.badRequest().body(Map.of("message",invalid.getMessage()));}
    }
}
