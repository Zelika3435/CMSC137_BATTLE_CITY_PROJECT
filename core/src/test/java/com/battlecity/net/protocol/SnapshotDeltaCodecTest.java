package com.battlecity.net.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.Direction;
import com.battlecity.game.GameCommand;
import com.battlecity.game.MapFactory;
import com.battlecity.game.Simulation;
import com.battlecity.game.Tile;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.SnapshotBuilder;
import com.battlecity.game.snapshot.SnapshotTileDelta;
import com.battlecity.game.snapshot.TileChange;
import com.battlecity.net.protocol.NetMessages.NetPacket;
import com.battlecity.net.protocol.NetMessages.SnapshotPayload;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SnapshotDeltaCodecTest {

    @Test
    void roundtrip_fullMapSnapshot() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        simulation.updateTick();
        GameSnapshot full = simulation.snapshot();

        SnapshotPayload original = SnapshotPayload.fullMap(full);
        SnapshotPayload decoded = roundtrip(original, full.serverTick());

        assertEquals(SnapshotFormat.FULL_MAP, decoded.format());
        assertEquals(full.stateHash(), decoded.snapshot().stateHash());
        assertEquals(full.tiles().length, decoded.snapshot().tiles().length);
        assertArrayEquals(full.tiles(), decoded.snapshot().tiles());
    }

    @Test
    void roundtrip_deltaSnapshot_sortedIndices() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        Tile[] before = simulation.world().map.copyTiles();
        simulation.updateTick();
        Tile[] after = simulation.world().map.copyTiles();
        after[100] = Tile.EMPTY;
        after[200] = Tile.BRICK;

        List<TileChange> changes = SnapshotTileDelta.collectChanges(before, after);
        assertTrue(SnapshotTileDelta.isSortedByIndex(changes));

        GameSnapshot entityOnly = SnapshotBuilder.build(simulation.world(), false);
        SnapshotPayload original = SnapshotPayload.delta(entityOnly, changes);
        SnapshotPayload decoded = roundtrip(original, entityOnly.serverTick());

        assertEquals(SnapshotFormat.DELTA, decoded.format());
        assertEquals(0, decoded.snapshot().tiles().length);
        assertEquals(changes.size(), decoded.tileChanges().size());
        for (int i = 0; i < changes.size(); i++) {
            assertEquals(changes.get(i).index(), decoded.tileChanges().get(i).index());
            assertEquals(changes.get(i).tile(), decoded.tileChanges().get(i).tile());
        }
    }

    @Test
    void brickDestruction_deltaUpdatesJoinerTileBuffer() {
        World world = World.createDefault();
        Tile[] joinerTiles = world.map.copyTiles();

        int brickIndex = findFirstBrickIndex(joinerTiles);
        assertTrue(brickIndex >= 0, "default map must contain brick");

        Tile[] serverBefore = world.map.copyTiles();
        world.map.setTile(indexToX(brickIndex), indexToY(brickIndex), Tile.EMPTY);
        simulationStep(world);
        Tile[] serverAfter = world.map.copyTiles();

        List<TileChange> changes = SnapshotTileDelta.collectChanges(serverBefore, serverAfter);
        assertEquals(1, changes.size());
        assertEquals(brickIndex, changes.get(0).index());
        assertEquals(Tile.EMPTY, changes.get(0).tile());

        SnapshotTileDelta.applyChanges(joinerTiles, changes);
        assertEquals(Tile.EMPTY, joinerTiles[brickIndex],
                "joiner view must reflect authoritative brick destruction");
    }

    @Test
    void deltaPacketWithinUdpLimit() {
        Simulation simulation = new Simulation(World.createDefault(), false, 0L);
        Tile[] before = simulation.world().map.copyTiles();
        Tile[] after = before.clone();
        for (int i = 0; i < after.length; i++) {
            after[i] = Tile.EMPTY;
        }
        List<TileChange> allChanges = SnapshotTileDelta.collectChanges(before, after);
        GameSnapshot entityOnly = SnapshotBuilder.build(simulation.world(), false);
        SnapshotPayload payload = SnapshotPayload.delta(entityOnly, allChanges);

        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                1,
                0,
                1,
                0,
                entityOnly.serverTick()
        );
        byte[] encoded = MessageCodec.encode(new NetPacket(header, payload));
        assertTrue(encoded.length <= ProtocolConstants.MAX_PACKET_BYTES,
                "worst-case full-grid delta must fit in MAX_PACKET_BYTES");
    }

    private static SnapshotPayload roundtrip(SnapshotPayload original, long serverTick) {
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                1,
                0,
                1,
                0,
                serverTick
        );
        NetPacket packet = new NetPacket(header, original);
        NetPacket decoded = MessageCodec.decode(MessageCodec.encode(packet));
        assertTrue(PacketValidator.validateDecoded(decoded).valid());
        return (SnapshotPayload) decoded.payload();
    }

    private static int findFirstBrickIndex(Tile[] tiles) {
        for (int i = 0; i < tiles.length; i++) {
            if (tiles[i] == Tile.BRICK) {
                return i;
            }
        }
        return -1;
    }

    private static int indexToX(int index) {
        return index % MapFactory.DEFAULT_WIDTH;
    }

    private static int indexToY(int index) {
        return index / MapFactory.DEFAULT_WIDTH;
    }

    private static void simulationStep(World world) {
        Simulation simulation = new Simulation(world, false, 0L);
        simulation.updateTick();
    }
}
