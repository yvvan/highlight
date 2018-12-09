package texteditor;

import java.awt.Color;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

import java.net.*;
import java.io.*;

public class TextEditor {
    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static boolean opened = true;
    private static Process colorServer;
    private static ArrayList<TextEditor> editors = new ArrayList<TextEditor>();

    private JTextPane text;
    private String filePath;
    private int offsetStart = 0;
    private int offsetEnd = 0;
    private long documentRevision = 0;
    private int chunksSent = 0;

    public TextEditor(JFrame frame, String path) {
        JTabbedPane tabbedPane = new JTabbedPane();
        text = new JTextPane();
        filePath = path;
        JScrollPane jsp = new JScrollPane(text);
        frame.add(tabbedPane);
        tabbedPane.addTab(path, jsp);

        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                ++documentRevision;
                mergeOffsets(e.getOffset(), e.getOffset() + e.getLength());
                sendRequest();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                ++documentRevision;
                mergeOffsets(e.getOffset(), e.getOffset() + e.getLength());
                sendRequest();
            }

            @Override public void changedUpdate(DocumentEvent arg0) {}
        });
    }

    private void clearOffsets() {
        offsetStart = 0;
        offsetEnd = 0;
    }

    private void mergeOffsets(int start, int end) {
        if (offsetEnd == 0) {
            offsetStart = start;
            offsetEnd = end;
            return;
        }
        offsetStart = Math.min(offsetStart, start);
        offsetEnd = Math.max(offsetEnd, end);
    }

    private void sendRequest() {
        if (!opened) {
            return;
        }
        try {
            int start = 0;
            int end = text.getDocument().getLength();
            if (offsetEnd != 0) {
                start = offsetStart;
                end = Math.min(end, offsetEnd);
            }

            chunksSent = Math.max(0, end - start - 1) / 5000 + 1;
            do {
                out.writeUTF(filePath);
                out.writeLong(documentRevision);
                out.writeInt(start);
                out.writeUTF(text.getDocument().getText(start, Math.min(5000, end - start)));
                start += 5000;
            } while (end - start > 5000);
        } catch (IOException | BadLocationException ex) {
            System.out.println("Error sending request, retry. ex = " + ex.getMessage());
            SwingUtilities.invokeLater(() -> { sendRequest(); });
        }
    }

    private static class ReadingRunnable implements Runnable {
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
                        editors.get(0).sendRequest();
                    });
                    continue;
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
                        editors.get(0).sendRequest();
                    });
                    continue;
                }

                final int startFinal = start;
                final long revisionFinal = revision;
                SwingUtilities.invokeLater(() -> {
                    editors.get(0).updateColors(revisionFinal, startFinal, buffer);
                });
            }
        }
    }

    // Must be called in Swing thread.
    void updateColors(long revision, int start, byte[] colors) {
        if (revision < documentRevision) {
            // We've already sent another request.
            return;
        }
        --chunksSent;
        if (chunksSent == 0) {
            clearOffsets();
        }
        StyleContext sc = StyleContext.getDefaultStyleContext();
        AttributeSet as = text.getCharacterAttributes();
        for (int i = 0; i < colors.length / 3; ++i) {
            as = sc.addAttribute(as,  StyleConstants.Foreground,
                    new Color(colors[i * 3] & 0xFF,
                            colors[i * 3 + 1] & 0xFF,
                            colors[i * 3 + 2] & 0xFF));
            text.getStyledDocument().setCharacterAttributes(start + i, 1, as, true);
        }
    }

    static void launchColorServer() {
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

    public static void main(String[] args) {
        JFrame frame = new JFrame("test window");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setVisible(true);

        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowClosing(WindowEvent e) {
                opened = false;
                try {
                    in.close();
                    out.close();
                    clientSocket.close();
                    serverSocket.close();
                    colorServer.destroyForcibly();
                } catch (IOException ex) {
                    System.out.println(ex.getMessage());
                    colorServer.destroyForcibly();
                }
            }

            @Override public void windowOpened(WindowEvent e) {}
            @Override public void windowClosed(WindowEvent e) {}
            @Override public void windowIconified(WindowEvent e) {}
            @Override public void windowDeiconified(WindowEvent e) {}
            @Override public void windowActivated(WindowEvent e) {}
            @Override public void windowDeactivated(WindowEvent e) {}
        });

        try {
            serverSocket = new ServerSocket(0);
        } catch (IOException ex) {
            System.out.println("Can't open socket, exiting: " + ex.getMessage());
            return;
        }
        launchColorServer();

        new Thread(new ReadingRunnable()).start();

        editors.add(new TextEditor(frame, "path"));
    }
}
