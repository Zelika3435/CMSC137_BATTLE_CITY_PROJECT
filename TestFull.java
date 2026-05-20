import com.battlecity.net.client.GameClient;
import com.battlecity.net.server.GameServer;
import com.battlecity.net.protocol.PacketValidator;
import com.battlecity.net.protocol.NetMessages;

public class TestFull {
    public static void main(String[] args) throws Exception {
        byte[] data = com.battlecity.net.protocol.MessageCodec.encode(
            new NetMessages.NetPacket(
                new com.battlecity.net.protocol.PacketHeader((byte)1, com.battlecity.net.protocol.MessageType.JOIN, 0, -1, 1, 0, 0L),
                new NetMessages.JoinPayload("TestPlayer")
            )
        );
        PacketValidator.ValidationResult result = PacketValidator.validateRaw(data);
        System.out.println("VALIDATION: " + result);
    }
}
