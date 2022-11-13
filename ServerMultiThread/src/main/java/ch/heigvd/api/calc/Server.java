package ch.heigvd.api.calc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Calculator server implementation - multi-thread
 */
public class Server {

    private final static Logger LOG = Logger.getLogger(Server.class.getName());
    private final int PORT = 2600; // Fixed by NRPC protocol

    /**
     * Main function to start the server
     */
    public static void main(String[] args) {
        // Log output on a single line
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%6$s%n");

        (new Server()).start();
    }

    /**
     * Start the server on a listening socket.
     */
    private void start() {
        /* DONE: implement the receptionist server here.
         *  The receptionist just creates a server socket and accepts new client connections.
         *  For a new client connection, the actual work is done in a new thread
         *  by a new ServerWorker.
         */
        ServerSocket serverSocket;

        try {
            serverSocket = new ServerSocket(PORT);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
            return;
        }

        while (true) {
            LOG.log(Level.INFO, "Multi-threaded: Waiting for a new client on port {0}", PORT);
            try {
                Socket clientSocket = serverSocket.accept();
                LOG.info("A new client has arrived. Starting a new thread and delegating work to a new servant...");
                new Thread(new ServerWorker(clientSocket)).start();
            } catch (IOException ex) {
                Logger.getLogger(Server.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
