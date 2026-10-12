# BRIM Game Platform

Multi-game platform with Spring Boot, Kafka, WebSocket supporting real-time and turn-based games.

## Games Included
- **Snake** (Real-time, WebSocket + Phaser)
- **Tank Battle** (Real-time, WebSocket + Phaser)
- **Chess** (Turn-based, Kafka)
- **Chinese Chess** (Turn-based, Kafka)

## Architecture

```
┌─────────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Vue 3 + Phaser     │────▶│  Spring Boot     │────▶│   Redis         │
│  (Browser)          │◀───│  WebSocket       │     │  (Room/Session) │
└─────────────────────┘     └────────┬─────────┘     └─────────────────┘
                                     │
                        ┌────────────┴────────────┐
                        ▼                         ▼
               ┌─────────────────┐       ┌─────────────────┐
               │ Real-time Games │       │ Turn-based Games│
               │ (Snake, Tank)   │       │ (Chess, C-Chess)│
               │ Server game loop│       │   Kafka Topics  │
               └─────────────────┘       └─────────────────┘
```

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2, Java 17 |
| Messaging | Apache Kafka |
| Session/Rooms | Redis |
| Realtime | STOMP over WebSocket (SockJS) |
| Frontend | Vue 3 + TypeScript + Vite |
| Game Rendering | Phaser 3 (Snake, Tank), DOM (Chess) |
| Infra | Docker Compose |

## Quick Start

### 1. Start Infrastructure
```bash
docker compose up -d
```

### 2. Run Backend
```bash
./run-backend.sh
# hoặc: mvn spring-boot:run
```

### 3. Run Frontend
```bash
cd frontend
npm install
npm run dev     # http://localhost:3000
```

### 4. Build Frontend for production
```bash
cd frontend && npm run build   # output: dist/
```

## Access Points
- **Frontend dev**: http://localhost:3000
- **REST API**: http://localhost:8080/api/rooms
- **WebSocket**: ws://localhost:8080/api/ws/game (SockJS)
- **H2 Console**: http://localhost:8080/api/h2-console (user `sa`, no password)
- **Kafka UI**: http://localhost:8081

## REST API

### Create Room
```bash
curl -X POST http://localhost:8080/api/rooms \
  -H "Content-Type: application/json" \
  -d '{"name": "My Snake Game", "gameType": "SNAKE", "maxPlayers": 4}'
```

### List Rooms
```bash
curl http://localhost:8080/api/rooms
```

### Join Room
```bash
curl -X POST http://localhost:8080/api/rooms/{roomId}/join \
  -H "Content-Type: application/json" \
  -d '{"username": "Player1", "sessionId": "ws-session-id"}'
```

## WebSocket Protocol (STOMP over SockJS)

Endpoint: `/api/ws/game`

### Subscriptions (client subscribes)
| Destination | Payload |
|-------------|---------|
| `/topic/room/{roomId}/state` | `GameRoom` |
| `/topic/game/{roomId}/state` | `SnakeGameState` / `TankGameState` / `ChessGameState` |
| `/topic/game/{roomId}/started` | `GameMessage` |
| `/topic/game/{roomId}/over` | `GameMessage` |
| `/user/queue/errors` | `GameMessage<string>` |

### Publish (client sends)
| Destination | Payload |
|-------------|---------|
| `/app/snake/{roomId}/join` | `GameMessage<Player>` |
| `/app/snake/{roomId}/move` | `GameMessage<{direction}>` |
| `/app/snake/{roomId}/ready` | `GameMessage` |
| `/app/tank/{roomId}/join` | `GameMessage<Player>` |
| `/app/tank/{roomId}/input` | `GameMessage<TankInput>` |
| `/app/turn/{roomId}/join` | `GameMessage<Player>` |
| `/app/turn/{roomId}/move` | `GameMessage<ChessMove>` |

## Project Structure

```
BRIM/
├── src/main/java/com/brim/game/
│   ├── config/              # WebSocket, Kafka, Redis configs
│   ├── game/
│   │   ├── snake/           # Snake game logic
│   │   ├── tank/            # Tank game logic
│   │   ├── chess/           # Chess game logic
│   │   └── chinesechess/    # Chinese Chess logic
│   ├── kafka/               # Kafka producer/consumer
│   ├── model/dto/           # DTOs (Room, Player, GameMessage...)
│   ├── service/             # RoomService, TurnBasedGameService
│   └── websocket/           # STOMP controllers + REST
├── frontend/                # Vue 3 + TS + Phaser
│   └── src/
│       ├── views/           # Lobby, Room, Snake, Tank, Chess...
│       ├── game/            # Phaser scenes
│       ├── composables/     # useStompClient, useGameRoom
│       ├── stores/          # Pinia
│       └── types/           # TS interfaces mirroring backend
├── docker-compose.yml       # Kafka, Zookeeper, Redis, PostgreSQL, Kafka-UI
└── pom.xml
```

## Configuration

Key settings in `src/main/resources/application.yml`:
```yaml
game:
  snake:
    grid-width: 20
    grid-height: 20
    tick-rate-ms: 150
  tank:
    map-width: 800
    map-height: 600
    tick-rate-ms: 50
```

Frontend proxy (`frontend/vite.config.ts`): `/api` → `http://localhost:8080`

## Extending with New Games

1. Backend: create `game/{newgame}/` with game logic
2. Frontend: create `views/{NewGame}View.vue` + Phaser scene if real-time
3. Add `GameType` enum value (backend) + `GAME_TYPE_INFO` (frontend)
4. Add route in `frontend/src/router/index.ts`

## License

MIT
# Brim
# Brim
# Brim
