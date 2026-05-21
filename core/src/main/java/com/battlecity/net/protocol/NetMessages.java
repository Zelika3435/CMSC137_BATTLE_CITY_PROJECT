package com.battlecity.net.protocol;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.TileChange;
import java.util.List;

public final class NetMessages {
    private NetMessages() {}

    // ---- C→S: join request ------------------------------------------------------------------

    public record JoinPayload(String playerName) {}

    // ---- S→C: join acknowledgement ----------------------------------------------------------

    /**
     * Extended JOIN_ACK now carries the current server phase so the client can immediately show
     * the correct UI (lobby, countdown, or — for late joins — already-running match).
     *
     * <p>Wire layout (little-endian):
     * <pre>
     *   byte  assignedPlayerId
     *   int   sessionId
     *   long  mapSeed
     *   long  serverTick
     *   byte  phaseOrdinal   ← {@link LobbyPhase#id()}
     * </pre>
     */
    public record JoinAckPayload(
            int assignedPlayerId,
            int sessionId,
            long mapSeed,
            long serverTick,
            LobbyPhase currentPhase
    ) {}

    // ---- C→S: game input --------------------------------------------------------------------

    public record InputPayload(
            long tickStamp,
            GameCommand command,
            Direction moveDir
    ) {}

    // ---- S→C: authoritative state snapshot --------------------------------------------------

    /**
     * Authoritative match state. {@link SnapshotFormat#FULL_MAP} includes the full tile grid in
     * {@link GameSnapshot#tiles()}; {@link SnapshotFormat#DELTA} omits tiles and lists
     * mutations in {@link #tileChanges()} (sorted by tile index).
     */
    public record SnapshotPayload(
            SnapshotFormat format,
            GameSnapshot snapshot,
            List<TileChange> tileChanges
    ) {
        public SnapshotPayload(GameSnapshot snapshot) {
            this(SnapshotFormat.FULL_MAP, snapshot, List.of());
        }

        public static SnapshotPayload fullMap(GameSnapshot snapshot) {
            return new SnapshotPayload(SnapshotFormat.FULL_MAP, snapshot, List.of());
        }

        public static SnapshotPayload delta(GameSnapshot snapshot, List<TileChange> tileChanges) {
            return new SnapshotPayload(SnapshotFormat.DELTA, snapshot, tileChanges);
        }
    }

    // ---- C↔S: keep-alive / latency ----------------------------------------------------------

    public record PingPayload(long clientTimeMs) {}

    public record PongPayload(long clientTimeMs, long serverTimeMs) {}

    // ---- C↔S: teardown ----------------------------------------------------------------------

    public record ErrorPayload(int code, String message) {}

    public record DisconnectPayload(String reason) {}

    // ---- S→C: lobby state broadcast ---------------------------------------------------------

    /**
     * One player slot in the lobby roster.
     *
     * <p>Wire layout per entry:
     * <pre>
     *   byte  playerId
     *   byte  nameLen (u8)
     *   bytes name   (UTF-8, max 32 chars)
     *   byte  flags  (bit 0 = ready, bit 1 = connected)
     * </pre>
     */
    public record LobbyPlayerEntry(
            int playerId,
            String name,
            boolean ready,
            boolean connected
    ) {}

    /**
     * Full lobby state snapshot sent by the server during {@link LobbyPhase#LOBBY} and
     * {@link LobbyPhase#COUNTDOWN}.
     *
     * <p>Wire layout (little-endian, after header):
     * <pre>
     *   byte  phaseOrdinal
     *   byte  hostPlayerId
     *   int   countdownTicksLeft
     *   byte  playerCount
     *   (playerCount × LobbyPlayerEntry)
     * </pre>
     */
    public record LobbyStatePayload(
            LobbyPhase phase,
            List<LobbyPlayerEntry> players,
            int hostPlayerId,
            int countdownTicksLeft
    ) {}

    // ---- C→S: ready toggle ------------------------------------------------------------------

    /**
     * Client signals its ready state.  Wire layout: {@code byte ready (0=false, 1=true)}.
     */
    public record SetReadyPayload(boolean ready) {}

    // ---- C→S: host match start --------------------------------------------------------------

    /**
     * Host requests an immediate match start, bypassing the remaining countdown.  The server
     * validates that the sender holds {@code playerId == 0} before honouring the request.
     *
     * <p>Wire layout: one reserved padding byte (so total packet length exceeds
     * {@link ProtocolConstants#HEADER_BYTES}).
     */
    public record StartMatchPayload() {}

    // ---- Envelope ---------------------------------------------------------------------------

    public record NetPacket(PacketHeader header, Object payload) {}
}
