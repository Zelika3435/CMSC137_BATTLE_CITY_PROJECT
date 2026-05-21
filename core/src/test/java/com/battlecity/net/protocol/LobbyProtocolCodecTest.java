package com.battlecity.net.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Headless codec round-trip and validation tests for lobby-phase protocol messages:
 * {@link MessageType#LOBBY_STATE}, {@link MessageType#SET_READY},
 * {@link MessageType#START_MATCH}, and the extended {@link MessageType#JOIN_ACK}.
 *
 * <p>All tests are pure in-memory (no network, no LibGDX, no game world).
 */
final class LobbyProtocolCodecTest {

    // ---- Helpers ----------------------------------------------------------------------------

    private static PacketHeader header(MessageType type) {
        return new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                type,
                /* sessionId */ 1,
                /* playerId  */ 0,
                /* seq       */ 1,
                /* ack       */ 0,
                /* serverTick*/ 0L
        );
    }

    private static NetMessages.NetPacket roundtrip(NetMessages.NetPacket original) {
        return MessageCodec.decode(MessageCodec.encode(original));
    }

    // ---- JOIN_ACK extended with currentPhase ------------------------------------------------

    @Test
    void roundtrip_joinAck_phaseLobby() {
        var payload = new NetMessages.JoinAckPayload(2, 5, 1234L, 99L, LobbyPhase.LOBBY);
        var decoded = (NetMessages.JoinAckPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.JOIN_ACK), payload)).payload();

        assertEquals(2,               decoded.assignedPlayerId());
        assertEquals(5,               decoded.sessionId());
        assertEquals(1234L,           decoded.mapSeed());
        assertEquals(99L,             decoded.serverTick());
        assertEquals(LobbyPhase.LOBBY, decoded.currentPhase());
    }

    @Test
    void roundtrip_joinAck_phaseCountdown() {
        var payload = new NetMessages.JoinAckPayload(1, 7, 0L, 42L, LobbyPhase.COUNTDOWN);
        var decoded = (NetMessages.JoinAckPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.JOIN_ACK), payload)).payload();

        assertEquals(LobbyPhase.COUNTDOWN, decoded.currentPhase());
        assertEquals(1,                    decoded.assignedPlayerId());
    }

    // ---- LOBBY_STATE ------------------------------------------------------------------------

    @Test
    void roundtrip_lobbyState_twoPlayers_countdown() {
        List<NetMessages.LobbyPlayerEntry> players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "Alice", true,  true),
                new NetMessages.LobbyPlayerEntry(1, "Bob",   false, true)
        );
        var original = new NetMessages.LobbyStatePayload(
                LobbyPhase.COUNTDOWN, players, /* hostPlayerId */ 0, /* countdownTicks */ 90);

        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals(LobbyPhase.COUNTDOWN, decoded.phase());
        assertEquals(0,  decoded.hostPlayerId());
        assertEquals(90, decoded.countdownTicksLeft());
        assertEquals(2,  decoded.players().size());

        NetMessages.LobbyPlayerEntry alice = decoded.players().get(0);
        assertEquals(0,       alice.playerId());
        assertEquals("Alice", alice.name());
        assertTrue(alice.ready());
        assertTrue(alice.connected());

        NetMessages.LobbyPlayerEntry bob = decoded.players().get(1);
        assertEquals(1,     bob.playerId());
        assertEquals("Bob", bob.name());
        assertFalse(bob.ready());
        assertTrue(bob.connected());
    }

    @Test
    void roundtrip_lobbyState_emptyPlayerList() {
        var original = new NetMessages.LobbyStatePayload(
                LobbyPhase.LOBBY, List.of(), 0, 0);
        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals(LobbyPhase.LOBBY, decoded.phase());
        assertTrue(decoded.players().isEmpty());
    }

    @Test
    void roundtrip_lobbyState_unicodeName() {
        List<NetMessages.LobbyPlayerEntry> players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "Ωmega", false, true));
        var original = new NetMessages.LobbyStatePayload(LobbyPhase.LOBBY, players, 0, 0);
        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals("Ωmega", decoded.players().get(0).name());
    }

    // ---- SET_READY --------------------------------------------------------------------------

    @Test
    void roundtrip_setReady_true() {
        var decoded = (NetMessages.SetReadyPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.SET_READY),
                        new NetMessages.SetReadyPayload(true))).payload();
        assertTrue(decoded.ready());
    }

    @Test
    void roundtrip_setReady_false() {
        var decoded = (NetMessages.SetReadyPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.SET_READY),
                        new NetMessages.SetReadyPayload(false))).payload();
        assertFalse(decoded.ready());
    }

    // ---- START_MATCH ------------------------------------------------------------------------

    @Test
    void roundtrip_startMatch() {
        var decoded = roundtrip(
                new NetMessages.NetPacket(header(MessageType.START_MATCH),
                        new NetMessages.StartMatchPayload()));
        assertNotNull(decoded.payload());
        assertInstanceOf(NetMessages.StartMatchPayload.class, decoded.payload());
        assertEquals(MessageType.START_MATCH, decoded.header().messageType());
    }

    // ---- LOBBY_STATE: RUNNING and END phases ------------------------------------------------

    @Test
    void roundtrip_lobbyState_phaseRunning() {
        // During RUNNING the server still needs to encode a valid LOBBY_STATE (e.g. for a
        // late-joining client that received a RUNNING-phase JOIN_ACK before the snapshot
        // arrives).  An empty player list is valid here.
        var original = new NetMessages.LobbyStatePayload(
                LobbyPhase.RUNNING, List.of(), /* hostPlayerId */ 0, /* countdownTicks */ 0);

        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals(LobbyPhase.RUNNING, decoded.phase());
        assertEquals(0, decoded.countdownTicksLeft());
        assertTrue(decoded.players().isEmpty());
    }

    @Test
    void roundtrip_lobbyState_phaseEnd() {
        List<NetMessages.LobbyPlayerEntry> players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "Winner", true,  true),
                new NetMessages.LobbyPlayerEntry(2, "Loser",  false, true)
        );
        var original = new NetMessages.LobbyStatePayload(
                LobbyPhase.END, players, /* hostPlayerId */ 0, /* countdownTicks */ 0);

        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals(LobbyPhase.END, decoded.phase());
        assertEquals(2,        decoded.players().size());
        assertEquals("Winner", decoded.players().get(0).name());
        assertEquals("Loser",  decoded.players().get(1).name());
    }

    @Test
    void roundtrip_lobbyState_maxPlayers_allConnected() {
        // Four players — the maximum the server supports.
        List<NetMessages.LobbyPlayerEntry> players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "Alpha",   true,  true),
                new NetMessages.LobbyPlayerEntry(1, "Bravo",   true,  true),
                new NetMessages.LobbyPlayerEntry(2, "Charlie", false, true),
                new NetMessages.LobbyPlayerEntry(3, "Delta",   true,  true)
        );
        var original = new NetMessages.LobbyStatePayload(
                LobbyPhase.COUNTDOWN, players, /* hostPlayerId */ 0, /* countdownTicks */ 120);

        var decoded = (NetMessages.LobbyStatePayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), original)).payload();

        assertEquals(LobbyPhase.COUNTDOWN, decoded.phase());
        assertEquals(4,   decoded.players().size());
        assertEquals(120, decoded.countdownTicksLeft());

        // Verify every player round-tripped faithfully.
        for (int i = 0; i < 4; i++) {
            assertEquals(players.get(i).playerId(),  decoded.players().get(i).playerId());
            assertEquals(players.get(i).name(),      decoded.players().get(i).name());
            assertEquals(players.get(i).ready(),     decoded.players().get(i).ready());
            assertEquals(players.get(i).connected(), decoded.players().get(i).connected());
        }
    }

    @Test
    void roundtrip_joinAck_phaseRunning() {
        // A client that joins exactly when RUNNING starts will receive a RUNNING-phase ACK.
        var payload = new NetMessages.JoinAckPayload(3, 8, 42L, 180L, LobbyPhase.RUNNING);
        var decoded = (NetMessages.JoinAckPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.JOIN_ACK), payload)).payload();

        assertEquals(LobbyPhase.RUNNING, decoded.currentPhase());
        assertEquals(3,    decoded.assignedPlayerId());
        assertEquals(180L, decoded.serverTick());
    }

    @Test
    void roundtrip_joinAck_phaseEnd() {
        var payload = new NetMessages.JoinAckPayload(0, 1, 0L, 600L, LobbyPhase.END);
        var decoded = (NetMessages.JoinAckPayload) roundtrip(
                new NetMessages.NetPacket(header(MessageType.JOIN_ACK), payload)).payload();

        assertEquals(LobbyPhase.END, decoded.currentPhase());
        assertEquals(600L, decoded.serverTick());
    }

    // ---- PacketValidator --------------------------------------------------------------------

    @Test
    void validator_acceptsValidLobbyState() {
        var players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "Host", true, true));
        var payload = new NetMessages.LobbyStatePayload(LobbyPhase.COUNTDOWN, players, 0, 60);
        var result = PacketValidator.validateDecoded(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), payload));
        assertTrue(result.valid(), result.reason());
    }

    @Test
    void validator_rejectsLobbyStateWithTooManyPlayers() {
        var players = List.of(
                new NetMessages.LobbyPlayerEntry(0, "A", false, true),
                new NetMessages.LobbyPlayerEntry(1, "B", false, true),
                new NetMessages.LobbyPlayerEntry(2, "C", false, true),
                new NetMessages.LobbyPlayerEntry(3, "D", false, true),
                new NetMessages.LobbyPlayerEntry(0, "E", false, true)  // 5th entry — invalid
        );
        var payload = new NetMessages.LobbyStatePayload(LobbyPhase.LOBBY, players, 0, 0);
        var result = PacketValidator.validateDecoded(
                new NetMessages.NetPacket(header(MessageType.LOBBY_STATE), payload));
        assertFalse(result.valid());
    }

    @Test
    void validator_acceptsValidStartMatch() {
        var result = PacketValidator.validateDecoded(
                new NetMessages.NetPacket(header(MessageType.START_MATCH),
                        new NetMessages.StartMatchPayload()));
        assertTrue(result.valid(), result.reason());
    }

    @Test
    void validator_acceptsValidSetReady() {
        var result = PacketValidator.validateDecoded(
                new NetMessages.NetPacket(header(MessageType.SET_READY),
                        new NetMessages.SetReadyPayload(true)));
        assertTrue(result.valid(), result.reason());
    }

    @Test
    void validator_rejectsJoinAckWithNullPhase() {
        // Construct a JoinAckPayload with a null phase (bypassing the record constructor check
        // — we test that validateDecoded catches this before it reaches the codec).
        var payload = new NetMessages.JoinAckPayload(0, 1, 0L, 0L, null);
        var result = PacketValidator.validateDecoded(
                new NetMessages.NetPacket(header(MessageType.JOIN_ACK), payload));
        assertFalse(result.valid());
    }
}
