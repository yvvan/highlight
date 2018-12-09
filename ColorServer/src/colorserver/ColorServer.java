package colorserver;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Vector;
import java.net.*;
import java.io.*;

public class ColorServer {
    static {
        System.loadLibrary("libColor");
    }

    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private Vector<String> documentPaths;
    private Vector<AtomicLong> currentRevisions;

    public ColorServer() {
        this.currentRevisions = new Vector<AtomicLong>();
        this.documentPaths = new Vector<String>();
    }

    boolean connect(String ip, int port) {
        try {
            clientSocket = new Socket(ip, port);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean initStreams() {
        try {
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        } catch (IOException ex) {
            System.out.println("Failed to create streams, ex = " + ex.getMessage());
            return false;
        }
        return true;
    }

    private void makeRequest(int index, long revision, int start, String text) {
        byte result[] = new byte[text.length() * 3];
        requestColors(revision, text, result);
        if (revision < currentRevisions.elementAt(index).get()) {
            return;
        }
        synchronized(this) {
            try {
                out.writeUTF(documentPaths.elementAt(index));
                out.writeLong(revision);
                out.writeInt(start);
                out.writeInt(result.length);
                out.write(result);
                out.flush();
            } catch (IOException ex) {
                System.out.println("Writing the answer failed, ex = " + ex.getMessage());
            }
        }
    }

    public void start() {
        while (!connect("127.0.0.1", 6000)) {}
        if (!initStreams()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);

        while (clientSocket.isConnected()) {
            int start;
            int index;
            String text;
            try {
                String documentPath = in.readUTF();
                index = documentPaths.indexOf(documentPath);
                if (index < 0) {
                    documentPaths.addElement(documentPath);
                    currentRevisions.addElement(new AtomicLong(in.readLong()));
                    index = currentRevisions.size() - 1;
                } else {
                    currentRevisions.elementAt(index).set(in.readLong());
                }
                start = in.readInt();
                text = in.readUTF();
            } catch (IOException ex) {
                System.out.println("Read failed, ex = " + ex.getMessage());
                return;
            }

            final long r = currentRevisions.elementAt(index).get();
            final int i = index;
            updateRevision(r);
            pool.execute(() -> {
                makeRequest(i, r, start, text);
            });
        }
        pool.shutdown();
        stopConnection();
    }

    public void stopConnection() {
        try {
            in.close();
            out.close();
            clientSocket.close();
        } catch (IOException ex) {
            System.out.println("Closing connection failed, ex = " + ex.getMessage());
        }
    }

    private static native void requestColors(long revision, String text, byte[] result);
    private static native void updateRevision(long revision);

    public static void main(String[] args) {
        new ColorServer().start();
    }

}
