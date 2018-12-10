package texteditor;

import java.awt.Color;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;

public class TextDocumentEditor {
    private TextEditor parent;
    private JTextPane text;
    private String filePath;
    private int offsetStart = 0;
    private int offsetEnd = 0;
    private long documentRevision = 0;
    private int chunksSent = 0;

    public TextDocumentEditor(TextEditor parent, String path) {
        this.parent = parent;
        JTabbedPane tabbedPane = new JTabbedPane();
        text = new JTextPane();
        filePath = path;
        JScrollPane jsp = new JScrollPane(text);
        parent.getFrame().add(tabbedPane);
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

    public void sendRequest() {
        if (!parent.isOpened()) {
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
                DataOutputStream out = parent.getOutStream();
                out.writeInt(TextEditor.MESSAGE_REQUEST);
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

    // Must be called in Swing thread.
    public void updateColors(long revision, int start, byte[] colors) {
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
    
    public JTextPane textPane() {
        return text;
    }
}
