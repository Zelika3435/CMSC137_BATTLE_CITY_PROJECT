import com.battlecity.net.client.GameClient;
import com.battlecity.game.Direction;

public class TestClient {
    public static void main(String[] args) throws Exception {
        GameClient client = new GameClient("127.0.0.1", 9000, "TestPlayer");
        client.connect();
        
        System.out.println("JOIN sent. Polling...");
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < 5000) {
            client.poll();
            if (client.isConnected()) {
                System.out.println("CONNECTED! Client ID: " + client.playerId());
                if (client.currentSnapshot() != null) {
                    System.out.println("Snapshot received!");
                    break;
                }
            }
            Thread.sleep(10);
        }
        System.out.println("Done. Connected=" + client.isConnected() + " Snapshot=" + (client.currentSnapshot() != null));
        client.close();
        System.exit(0);
    }
}
