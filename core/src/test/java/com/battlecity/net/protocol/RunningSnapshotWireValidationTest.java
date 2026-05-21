package com.battlecity.net.protocol;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.battlecity.game.Simulation;
import com.battlecity.game.World;
import com.battlecity.game.snapshot.GameSnapshot;
import com.battlecity.game.snapshot.SnapshotBuilder;
import com.battlecity.net.protocol.NetMessages.SnapshotPayload;
import org.junit.jupiter.api.Test;

final class RunningSnapshotWireValidationTest {

    @Test
    void fullMapSnapshotFromDefaultWorld_passesValidator() {
        Simulation sim = new Simulation(World.createDefault(), false, 0L);
        sim.updateTick();
        GameSnapshot entitySnapshot = SnapshotBuilder.build(sim.world(), false);
        GameSnapshot full = new GameSnapshot(
                entitySnapshot.serverTick(),
                entitySnapshot.stateHash(),
                entitySnapshot.mapWidthTiles(),
                entitySnapshot.mapHeightTiles(),
                entitySnapshot.tileSize(),
                sim.world().map.copyTiles(),
                entitySnapshot.tanks(),
                entitySnapshot.projectiles(),
                entitySnapshot.baseDestroyed(),
                entitySnapshot.matchOver()
        );
        SnapshotPayload payload = SnapshotPayload.fullMap(full);
        PacketHeader header = new PacketHeader(
                ProtocolConstants.PROTOCOL_VERSION,
                MessageType.SNAPSHOT,
                1,
                0,
                1,
                0,
                full.serverTick()
        );
        byte[] encoded = MessageCodec.encode(new NetMessages.NetPacket(header, payload));
        assertTrue(encoded.length <= ProtocolConstants.MAX_PACKET_BYTES,
                "encoded snapshot size=" + encoded.length);
        PacketValidator.ValidationResult result = PacketValidator.validateRaw(encoded);
        assertTrue(result.valid(), () -> "validation failed: " + result.reason());
    }
}
