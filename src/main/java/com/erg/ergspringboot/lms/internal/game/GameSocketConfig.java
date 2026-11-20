package com.erg.ergspringboot.lms.internal.game;

import com.erg.ergspringboot.lms.LmsCallerPermissions;
import com.erg.ergspringboot.lms.internal.game.GameModel.Conn;
import com.erg.ergspringboot.lms.internal.game.GameModel.Room;
import com.erg.ergspringboot.platform.security.ErgPrincipal;
import com.erg.ergspringboot.platform.web.ErgHttpProperties;
import java.util.Arrays;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * {@code /ws/games/{roomId}}: plain JSON text frames (no STOMP). The upgrade request goes through the normal security
 * filter chain, so the caller is the already authenticated session; the interceptor copies who it is (and checks the
 * tenant, the room and the games permission) into the socket attributes.
 */
@Configuration
@EnableWebSocket
public class GameSocketConfig implements WebSocketConfigurer {

    static final String PATH = "/ws/games/{roomId}";
    private static final String ROOM_ID = "gameRoomId";
    private static final String USER_ID = "gameUserId";

    private final GameRoomManager manager;
    private final LmsCallerPermissions permissions;
    private final ErgHttpProperties http;

    public GameSocketConfig(GameRoomManager manager, LmsCallerPermissions permissions, ErgHttpProperties http) {
        this.manager = manager;
        this.permissions = permissions;
        this.http = http;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(String.valueOf(http.getCors().getAllowedOrigins()).split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        registry.addHandler(new Handler(manager), PATH)
                .addInterceptors(new Interceptor())
                .setAllowedOriginPatterns(origins);
    }

    private final class Interceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                       Map<String, Object> attributes) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof ErgPrincipal principal)) {
                return reject(response, HttpStatus.UNAUTHORIZED);
            }
            String path = request.getURI().getPath();
            String roomId = path.substring(path.lastIndexOf('/') + 1);
            Room room = manager.get(roomId);
            if (room == null || !room.tenantId.equals(principal.tenantId())) return reject(response, HttpStatus.NOT_FOUND);
            boolean host = principal.userId().equals(room.hostUserId);
            if (!permissions.hasAny(host ? "lms.games.host" : "lms.games.play", "*")) {
                return reject(response, HttpStatus.FORBIDDEN);
            }
            attributes.put(ROOM_ID, roomId);
            attributes.put(USER_ID, principal.userId());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Exception exception) {
        }

        private static boolean reject(ServerHttpResponse response, HttpStatus status) {
            response.setStatusCode(status);
            return false;
        }
    }

    private static final class Handler extends TextWebSocketHandler {
        private final GameRoomManager manager;

        Handler(GameRoomManager manager) {
            this.manager = manager;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            Room room = manager.get((String) session.getAttributes().get(ROOM_ID));
            if (room == null) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            manager.connect(session, room, (String) session.getAttributes().get(USER_ID));
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            Room room = manager.get((String) session.getAttributes().get(ROOM_ID));
            Object conn = session.getAttributes().get("conn");
            if (room != null && conn instanceof Conn c) manager.onMessage(room, c, message.getPayload());
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            Room room = manager.get((String) session.getAttributes().get(ROOM_ID));
            Object conn = session.getAttributes().get("conn");
            if (room != null && conn instanceof Conn c) manager.disconnect(room, c);
        }
    }
}
