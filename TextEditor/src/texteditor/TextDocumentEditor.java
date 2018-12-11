package texteditor;

import java.awt.Color;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import static java.util.stream.Collectors.toCollection;
import javax.swing.JScrollPane;
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
    private JScrollPane scrollArea;
    private JTextPane text;
    private String filePath;
    private class Range implements Comparable<Integer> {
        public int start;
        public int end;

        public Range clone() {
            Range range = new Range();
            range.start = this.start;
            range.end = this.end;
            return range;
        }

        @Override
        public int compareTo(Integer val) {
            if (val < start)
                return 1;
            if (val > end)
                return -1;
            return 0;
        }

    }
    private ArrayList<Range> ranges = new ArrayList<Range>();
    private long documentRevision = 0;

    private void printRanges() {
        for (int i = 0; i < ranges.size(); ++i) {
            System.out.print(ranges.get(i).start + "," + ranges.get(i).end + "; ");
        }
        System.out.println();
    }

    public TextDocumentEditor(TextEditor parent, String path) {
        this.parent = parent;
        text = new JTextPane();
        filePath = path;
        scrollArea = new JScrollPane(text);

        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void removeUpdate(DocumentEvent e) {
                ++documentRevision;
                removeRange(e.getOffset(), e.getOffset() + e.getLength());
                shiftRanges(e.getOffset() + e.getLength(), -e.getLength());
                sendRequest();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                ++documentRevision;
                shiftRanges(e.getOffset(), e.getLength());
                addRange(e.getOffset(), e.getOffset() + e.getLength());
                sendRequest();
            }

            @Override public void changedUpdate(DocumentEvent arg0) {}
        });
    }

    private void removeRange(int start, int end) {
        int startIndex = Collections.binarySearch(ranges, start);
        int endIndex = Collections.binarySearch(ranges, end);
        if (startIndex >= 0 && startIndex == endIndex) {
            // Split
            if (end != ranges.get(startIndex).end) {
                Range range = new Range();
                range.end = ranges.get(startIndex).end;
                range.start = end;
                ranges.add(startIndex + 1, range);
            }

            if (start != ranges.get(startIndex).start) {
                ranges.get(startIndex).end = start;
            } else {
                ranges.remove(startIndex);
            }

            printRanges();
            return;
        }

        if (startIndex < 0) {
            startIndex = -(startIndex + 1);
        } else {
            if (start != ranges.get(startIndex).start) {
                ranges.get(startIndex).end = start;
                ++startIndex;
            }
        }

        if (endIndex < 0) {
            endIndex = -(endIndex + 1) - 1;
        } else {
            if (end != ranges.get(endIndex).end) {
                ranges.get(endIndex).start = end;
                --endIndex;
            }
        }

        if (endIndex >= startIndex) {
            ranges.subList(startIndex, endIndex + 1).clear();
        }

        printRanges();
    }

    private void addRange(int start, int end) {
        int startIndex = Collections.binarySearch(ranges, start);
        int endIndex = Collections.binarySearch(ranges, end);

        if (startIndex < 0) {
            startIndex = -(startIndex + 1);
        } else {
            start = ranges.get(startIndex).start;
        }
        if (endIndex < 0) {
            endIndex = -(endIndex + 1) - 1;
        } else {
            end = ranges.get(endIndex).end;
        }

        if (endIndex >= startIndex) {
            ranges.subList(startIndex, endIndex + 1).clear();
        }
        Range range = new Range();
        range.start = start;
        range.end = end;
        ranges.add(startIndex, range);

        printRanges();
    }

    private void shiftRanges(int start, int shift) {
        for (Range range : ranges) {
            if (start <= range.start) {
                range.start += shift;
            }
            if (start < range.end) {
                range.end += shift;
            }
        }

        printRanges();
    }

    private void splitTooLongChunks(ArrayList<Range> toSend, int maxSize) {
        for (int i = 0; i < toSend.size(); ++i) {
            Range current = toSend.get(i);
            int size = current.end - current.start;
            if (size <= maxSize) {
                continue;
            }

            current.end = current.start + maxSize;
            size -= maxSize;
            while (size > 0) {
                Range newRange = new Range();
                newRange.start = toSend.get(i).end;
                newRange.end = newRange.start + Math.min(maxSize, size);
                toSend.add(i + 1, newRange);

                ++i;
                size -= maxSize;
            }
        }
    }

    public void sendRequest() {
        if (!parent.isOpened()) {
            return;
        }
        try {
            ArrayList<Range> toSend = ranges.stream()
                    .map(range -> range.clone())
                    .collect(toCollection(ArrayList::new));
            splitTooLongChunks(toSend, 5000);

            for (int i = 0; i < toSend.size(); ++i) {
                Range range = toSend.get(i);
                DataOutputStream out = parent.getOutStream();
                out.writeInt(TextEditor.MESSAGE_REQUEST);
                out.writeUTF(filePath);
                out.writeLong(documentRevision);
                out.writeInt(range.start);
                out.writeUTF(text.getDocument().getText(range.start, range.end - range.start));
            }
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
        removeRange(start, start + colors.length / 3);
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

    public JScrollPane scrollPane() {
        return scrollArea;
    }

    public boolean areRangesEmpty() {
        return ranges.isEmpty();
    }
}
