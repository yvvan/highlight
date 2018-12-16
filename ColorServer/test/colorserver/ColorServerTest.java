package colorserver;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class ColorServerTest {
    private ServerSocket serverSocket;

    public ColorServerTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testConnectAndDisconnect() throws IOException {
        // Test that it does not hang.
        serverSocket = new ServerSocket(0);
        new Thread(() -> {
            try {
                Socket clientSocket = serverSocket.accept();
                DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                Thread.sleep(1000);
                out.writeInt(ColorServer.MESSAGE_CLOSE);
                serverSocket.close();
            } catch (IOException | InterruptedException ex) {
                Logger.getLogger(ColorServerTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();
        new ColorServer(serverSocket.getLocalPort()).start();
    }
}
