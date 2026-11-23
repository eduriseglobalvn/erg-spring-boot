package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.common.response.ApiResponse;
import com.erg.ergspringboot.lms.authorization.LmsGamesAccess;
import com.erg.ergspringboot.lms.authorization.LmsGamesHost;
import com.erg.ergspringboot.lms.authorization.LmsGamesPlay;
import com.erg.ergspringboot.lms.internal.game.GameDtos.CreateGameRoomRequest;
import com.erg.ergspringboot.lms.internal.game.GameDtos.JoinRequest;
import com.erg.ergspringboot.lms.internal.game.GameDtos.JoinResponse;
import com.erg.ergspringboot.lms.internal.game.GameDtos.RoomSummary;
import com.erg.ergspringboot.lms.internal.game.GameDtos.SourceItem;
import com.erg.ergspringboot.platform.security.UserAccessContext;
import com.erg.ergspringboot.platform.web.RequestCorrelation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST side of the classroom games; the live play happens on {@code /ws/games/{roomId}}. */
@RestController
@RequestMapping("/api/v1/lms/games")
public class LmsGameController {

    private final GameRoomService service;
    private final GameSourceService sources;

    public LmsGameController(GameRoomService service, GameSourceService sources) {
        this.service = service;
        this.sources = sources;
    }

    @GetMapping("/sources")
    @LmsGamesHost
    public ApiResponse<List<SourceItem>> sources(@RequestParam(required = false) String classId, HttpServletRequest request) {
        return ok("Game sources", sources.sources(UserAccessContext.from(request), classId), request);
    }

    @PostMapping("/rooms")
    @LmsGamesHost
    public ApiResponse<RoomSummary> create(@RequestBody CreateGameRoomRequest body, HttpServletRequest request) {
        return ok("Room created", service.create(UserAccessContext.from(request), body), request);
    }

    @PostMapping("/rooms/join")
    @LmsGamesPlay
    public ApiResponse<JoinResponse> join(@RequestBody JoinRequest body, HttpServletRequest request) {
        return ok("Room joined", service.join(UserAccessContext.from(request), body), request);
    }

    @GetMapping("/rooms/{id}")
    @LmsGamesAccess
    public ApiResponse<RoomSummary> get(@PathVariable String id, HttpServletRequest request) {
        return ok("Room", service.get(UserAccessContext.from(request), id), request);
    }

    @GetMapping("/rooms")
    @LmsGamesHost
    public ApiResponse<List<RoomSummary>> list(
            @RequestParam(required = false) String status, HttpServletRequest request) {
        return ok("Rooms", service.list(UserAccessContext.from(request), status), request);
    }

    private static <T> ApiResponse<T> ok(String message, T data, HttpServletRequest request) {
        return ApiResponse.success(200, message, data, request.getRequestURI(), RequestCorrelation.current(request));
    }
}
