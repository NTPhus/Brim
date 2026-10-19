package com.brim.game.websocket;

import com.brim.game.game.vietnam.VietnamCampaign;
import com.brim.game.security.GuestSession;
import com.brim.game.service.VietnamProgressService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vietnam")
@RequiredArgsConstructor
public class VietnamRestController {
    private final VietnamProgressService progress;
    public record Hub(int unlocked,List<VietnamCampaign.Chapter> chapters) {}
    @GetMapping public Hub hub(HttpServletRequest request) {
        return new Hub(progress.unlocked(GuestSession.require(request.getSession(false)).id()),VietnamCampaign.CHAPTERS);
    }
}
