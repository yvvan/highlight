package texteditor;

import java.awt.Color;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

import java.net.*;
import java.io.*;

public class TextEditor {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataOutputStream out;
    private DataInputStream in;
    private JTextPane text;
    private Process colorServer;
    private boolean opened = true;
    
    private int offsetStart = 0;
    private int offsetEnd = 0;
    private long documentRevision = 0;
    
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
            
            do {
                out.writeLong(documentRevision);
                out.writeInt(start);
                out.writeUTF(text.getDocument().getText(start, Math.min(5000, end - start)));
                start += 5000;
            } while (end - start > 5000);
        } catch (Exception ex) {
            System.out.println("Error sending request, retry. ex = " + ex.getMessage());
            SwingUtilities.invokeLater(() -> { sendRequest(); });
        }
    }
    
    private class ReadingRunnable implements Runnable {
        @Override
        public void run() {
            while(opened) {
                int bufferSize = 0;
                int start = 0;
                long revision = 0;
                try {
                    revision = in.readLong();
                    start = in.readInt();
                    bufferSize = in.readInt();
                } catch (IOException ex) {
                    System.out.println("Can't read the header, restart" + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        launchColorServer();
                        sendRequest();
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
                        sendRequest();
                    });
                    continue;
                }
                
                final int startFinal = start;
                final long revisionFinal = revision;
                SwingUtilities.invokeLater(() -> {
                    updateColors(revisionFinal, startFinal, buffer);
                });
            }
        }
    }
    
    public void start() {
        JFrame frame = new JFrame("test window");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        text = new JTextPane();
        JScrollPane jsp = new JScrollPane(text);
        
        frame.add(jsp);

        frame.setSize(800, 600);
        frame.setVisible(true);
        
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
                } catch (Exception ex) {
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
            serverSocket = new ServerSocket(6000); 
        } catch (IOException ex) {
            System.out.println("Can't open socket, exiting: " + ex.getMessage());
            return;
        }
        launchColorServer();
        
        new Thread(new ReadingRunnable()).start();
    }
    
    // Must be called in Swing thread.
    void updateColors(long revision, int start, byte[] colors) {
        if (revision < documentRevision) {
            // We've already sent another request.
            return;
        }
        clearOffsets();
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
    
    void launchColorServer() {
        if (!opened) {
            return;
        }
        try {
            if (colorServer != null && colorServer.isAlive())
                colorServer.destroyForcibly();
            colorServer = Runtime.getRuntime().exec("java -jar ../ColorServer/dist/ColorServer.jar");
        } catch (IOException ex) {
            System.out.println("Failed to run the server, no colors. ex = " + ex.getMessage());
        }
        try {
            if (clientSocket != null)
                clientSocket.close();
            clientSocket = serverSocket.accept();
            out = new DataOutputStream(clientSocket.getOutputStream());
            in = new DataInputStream(clientSocket.getInputStream());
        } catch (Exception ex) {
            System.out.println("Failed to connect, no colors. ex = " + ex.getMessage());
        }
    }
    
    public static void main(String[] args) {
        new TextEditor().start();
    }
}
