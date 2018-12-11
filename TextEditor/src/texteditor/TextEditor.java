package texteditor;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import javax.swing.*;

import java.net.*;
import java.io.*;

public class TextEditor {
    public final static int MESSAGE_REQUEST = 0;
    public final static int MESSAGE_CLOSE = 1;

    private JFrame frame;
    private JTabbedPane tabbedPane;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private boolean opened;
    private Process colorServer;
    private Thread readingThread;

    public DataOutputStream getOutStream() {
        return out;
    }

    public DataInputStream getInStream() {
        return in;
    }

    public boolean isOpened() {
        return opened;
    }

    private ArrayList<TextDocumentEditor> editors = new ArrayList<TextDocumentEditor>();

    private void sendCloseRequest() {
        try {
            out.writeInt(MESSAGE_CLOSE);
        } catch (IOException | NullPointerException ex) {
            System.out.println("Sending close message failed. " + ex.getMessage());
        }
    }

    private class ReadingRunnable implements Runnable {
        @Override
        public void run() {
            while(opened) {
                int bufferSize;
                int start;
                long revision;
                try {
                    // fileName is not used for now
                    String fileName = in.readUTF();
                    revision = in.readLong();
                    start = in.readInt();
                    bufferSize = in.readInt();
                } catch (IOException ex) {
                    System.out.println("Can't read the header, restart" + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        launchColorServer();
                        startReadingMessages();
                        if (!editors.isEmpty())
                            editors.get(0).sendRequest();
                    });
                    return;
                }

                byte[] buffer = new byte[bufferSize];
                int bufferOffset = 0;
                try {
                    // Read buffer until it's end.
                    while (bufferOffset < bufferSize) {
                        bufferOffset += in.read(buffer, bufferOffset, bufferSize - bufferOffset);
                    }
                } catch (IOException ex) {
                    System.out.println("Can't read buffer, restart: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        launchColorServer();
                        startReadingMessages();
                        if (!editors.isEmpty())
                            editors.get(0).sendRequest();
                    });
                    return;
                }

                final int startFinal = start;
                final long revisionFinal = revision;
                SwingUtilities.invokeLater(() -> {
                    if (!editors.isEmpty())
                        editors.get(0).updateColors(revisionFinal, startFinal, buffer);
                });
            }
        }
    }

    void launchColorServer() {
        if (!opened) {
            return;
        }
        try {
            if (colorServer != null && colorServer.isAlive())
                colorServer.destroyForcibly();
            colorServer = Runtime.getRuntime().exec(
                    "java -jar ../ColorServer/dist/ColorServer.jar "
                            + serverSocket.getLocalPort());
        } catch (IOException ex) {
            System.out.println("Failed to run the server, no colors. ex = " + ex.getMessage());
        }
        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
            clientSocket = serverSocket.accept();
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        } catch (IOException ex) {
            System.out.println("Failed to connect, no colors. ex = " + ex.getMessage());
        }
    }

    public void disconnect() {
        opened = false;
        sendCloseRequest();

        try {
            in.close();
            out.close();
            clientSocket.close();
            serverSocket.close();
            if (readingThread != null)
                readingThread.join();
        } catch (IOException | NullPointerException | InterruptedException ex) {
            System.out.println(ex.getMessage());
        }
    }

    public TextEditor() {
        tabbedPane = new JTabbedPane();
        frame = new JFrame("test window");
        frame.setSize(800, 600);
        frame.setVisible(true);
        frame.add(tabbedPane);

        frame.addWindowListener(new WindowListener() {
            @Override public void windowClosing(WindowEvent e) {
                disconnect();
                Runtime.getRuntime().exit(0);
            }
            @Override public void windowClosed(WindowEvent e) {}
            @Override public void windowOpened(WindowEvent e) {}
            @Override public void windowIconified(WindowEvent e) {}
            @Override public void windowDeiconified(WindowEvent e) {}
            @Override public void windowActivated(WindowEvent e) {}
            @Override public void windowDeactivated(WindowEvent e) {}
        });
    }

    public void createServerSocket() {
        opened = true;
        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException ex) {
            System.out.println("Can't open socket, exiting: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        TextEditor textEditor = new TextEditor();

        textEditor.createServerSocket();

        textEditor.launchColorServer();

        textEditor.startReadingMessages();

        textEditor.addDocumentEditor(new TextDocumentEditor(textEditor, "path"));
    }

    public void startReadingMessages() {
        if (!opened) {
            return;
        }
        readingThread = new Thread(new ReadingRunnable());
        readingThread.start();
    }

    public boolean hasDocumentEditor() {
        return !editors.isEmpty();
    }

    public void addDocumentEditor(TextDocumentEditor documentEditor) {
        editors.add(documentEditor);
        tabbedPane.add(documentEditor.scrollPane());
    }

    public void removeDocumentEditor(TextDocumentEditor documentEditor) {
        tabbedPane.remove(documentEditor.scrollPane());
        editors.remove(documentEditor);
    }

    public boolean isConnectionClosed() {
        return clientSocket == null || clientSocket.isClosed();
    }
}
