package com.llamination.backend.transport;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
        "/login",
        "/lobbies",
        "/lobbies/new",
        "/lobbies/{lobbyId}",
        "/invite/{inviteToken}",
        "/games/{gameId}"
    })
    public String frontendRoutes() {
        return "forward:/index.html";
    }
}
