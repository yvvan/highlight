package texteditor;

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.BadLocationException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TextEditorTest {
    private static TextEditor textEditor;
    private Lock sequential = new ReentrantLock();

    public TextEditorTest() {
    }

    @BeforeClass
    public static void setUpClass() {
        textEditor = new TextEditor();
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        sequential.lock();
        textEditor.createServerSocket();
    }

    @After
    public void tearDown() {
        textEditor.disconnect();
        if (!textEditor.hasColorServer()) {
            return;
        }

        // Give some time to the server to finish.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
        }
        sequential.unlock();
    }

    @Test
    public void test001RangesBasic() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");

        editor.addRange(0, 10);

        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 10);
    }

    @Test
    public void test002RangesAddTwice() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(20, 30);

        editor.addRange(0, 10);

        assertTrue(editor.getRanges().size() == 2);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 10);
    }

    @Test
    public void test003RangesAddConnected() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(10, 20);

        editor.addRange(0, 10);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 20);
    }

    @Test
    public void test004RangesRemoveBeginning() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 20);

        editor.removeRange(0, 5);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 5);
        assertTrue(range.end == 20);
    }

    @Test
    public void test005RangesRemoveEnd() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 20);
        editor.removeRange(15, 20);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 15);
    }

    @Test
    public void test006RangesRemoveMiddle() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 20);

        editor.removeRange(10, 15);

        assertTrue(editor.getRanges().size() == 2);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 10);
    }

    @Test
    public void test007RangesRemoveAll() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 20);

        editor.removeRange(0, 20);

        assertTrue(editor.getRanges().isEmpty());
    }

    @Test
    public void test008RangesRemoveMoreThanExists() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(10, 20);

        editor.removeRange(0, 20);

        assertTrue(editor.getRanges().isEmpty());
    }

    @Test
    public void test009RangesAddToConnect() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(10, 20);
        editor.addRange(30, 40);

        editor.addRange(20, 35);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 10);
        assertTrue(range.end == 40);
    }

    @Test
    public void test010RangesAddSwallowInside() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 10);
        editor.addRange(20, 30);

        editor.addRange(15, 40);

        assertTrue(editor.getRanges().size() == 2);
        TextDocumentEditor.Range range = editor.getRanges().get(1);
        assertTrue(range.start == 15);
        assertTrue(range.end == 40);
    }

    @Test
    public void test011RangesRemove() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 10);
        editor.addRange(20, 30);

        editor.addRange(15, 40);

        assertTrue(editor.getRanges().size() == 2);
        TextDocumentEditor.Range range = editor.getRanges().get(1);
        assertTrue(range.start == 15);
        assertTrue(range.end == 40);
    }

    @Test
    public void test012RangesRemoveAll() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 10);
        editor.addRange(20, 30);

        editor.removeRange(0, 30);

        assertTrue(editor.getRanges().isEmpty());
    }

    @Test
    public void test013RangesShiftAndAdd() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(20, 30);

        editor.shiftRanges(20, 5);
        editor.addRange(20, 25);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 20);
        assertTrue(range.end == 35);
    }

    @Test
    public void test014RangesRemoveAndShift() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(20, 30);

        editor.removeRange(25, 26);
        editor.shiftRanges(26, -1);

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 20);
        assertTrue(range.end == 29);
    }

    @Test
    public void test015RangesComplexExample() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        editor.addRange(0, 905);

        editor.shiftRanges(393, 90);
        editor.addRange(393, 483);
        //0, 995
        assertTrue(editor.getRanges().size() == 1);

        editor.removeRange(729, 832);
        //0, 729 and 832, 995
        editor.shiftRanges(832, -103);
        //0, 892

        assertTrue(editor.getRanges().size() == 1);
        TextDocumentEditor.Range range = editor.getRanges().get(0);
        assertTrue(range.start == 0);
        assertTrue(range.end == 892);
    }

    private boolean checkRanges(ArrayList<TextDocumentEditor.Range> ranges) {
        for (int i = 0; i < ranges.size(); ++i) {
            TextDocumentEditor.Range currentRange = ranges.get(i);
            if (currentRange.end <= currentRange.start) {
                System.out.println("Wrong range is " + currentRange.start + " , " + currentRange.end);
                return false;
            }

            if (i > 0 && currentRange.start <= ranges.get(i - 1).end) {
                System.out.println("Wrong ranges are " + currentRange.start + " , " + currentRange.end
                    + " and " + ranges.get(i - 1).start + ", " + ranges.get(i - 1).end);
                return false;
            }
        }
        return true;
    }

    private void printLog(ArrayList<TextDocumentEditor.Range> ranges) {
        System.out.println("List of operations:");
        for (TextDocumentEditor.Range range : ranges) {
            if (range.start < 0) {
                System.out.println("Remove " + (- range.start - 1) + ", " + range.end);
            } else {
                System.out.println("Insert " + range.start + ", " + range.end);
            }
        }
    }

    @Test
    public void test016RangesRandom() {
        TextDocumentEditor editor = new TextDocumentEditor(null, "");
        int documentLength = 0;
        ArrayList<TextDocumentEditor.Range> log = new ArrayList<TextDocumentEditor.Range>();
        for (int i = 0; i < 100000; ++i) {
            boolean operation = (Math.round(Math.random()) == 0);
            int pos = (int)(Math.random() * documentLength);
            int length = 1;
            if (operation) {
                // Insert.
                length = (int) (Math.random() * 1000);
                editor.shiftRanges(pos, length);
                editor.addRange(pos, pos + length);
                documentLength += length;
            } else {
                //Remove, limit by the text size.
                length = (int)(Math.random() * (documentLength - pos));
                editor.removeRange(pos, pos + length);
                editor.shiftRanges(pos + length, -length);
                documentLength -= length;
            }
            TextDocumentEditor.Range range = editor.new Range();
            range.start = operation ? pos : (- pos - 1);
            range.end = pos + length;
            log.add(range);

            if (!checkRanges(editor.getRanges())) {
                printLog(log);
                assertTrue(false);
            }
        }
    }

    @Test
    public void test0Init() {
        assertTrue(textEditor.isConnectionClosed());
    }

    @Test
    public void test1LaunchColorServer() {
        textEditor.launchColorServer();

        assertFalse(textEditor.isConnectionClosed());
    }

    @Test
    public void test2WithMessages() {
        textEditor.launchColorServer();

        textEditor.startReadingMessages();
    }

    private class JUnitDocumentEditor extends TextDocumentEditor {
        private Vector<Byte> lastColors = new Vector<Byte>();
        public JUnitDocumentEditor(TextEditor parent, String path) {
            super(parent, path);
        }

        @Override
        public void updateColors(long revision, int start, byte[] colors) {
            super.updateColors(revision, start, colors);
            int colorsStart = start * 3;
            if (colorsStart + colors.length > lastColors.size()) {
                lastColors.setSize(colorsStart + colors.length);
            }
            for (int i = 0; i < colors.length; ++i) {
                lastColors.set(i + colorsStart, colors[i]);
            }
        }

        public Vector<Byte> getLastColors() {
            return lastColors;
        }
    }

    private boolean waitForAllMessages(JUnitDocumentEditor documentEditor, int seconds) {
        int count = 0;
        while (count < seconds) {
            if (documentEditor.areRangesEmpty()) {
                return true;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
            }
            ++count;
        }
        return false;
    }

    @Test
    public void test3SendingMessage() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);

        documentEditor.textPane().setText("sometext");

        boolean colorsReceived = waitForAllMessages(documentEditor, 10);
        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(colorsReceived);
    }

    @Test
    public void test4ColorsSimple() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);

        documentEditor.textPane().setText("12345abcde");

        boolean colorsReceived = waitForAllMessages(documentEditor, 10);
        Vector<Byte> colors = documentEditor.getLastColors();
        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(colorsReceived);
        for (int i = 0; i < 5; ++i) {
            assertTrue(colors.get(i * 3) == 0 && colors.get(i * 3 + 1) == 0 && (colors.get(i * 3 + 2) & 0xFF) == 255);
        }
        for (int i = 5; i < 10; ++i) {
            assertTrue(colors.get(i * 3) == 0 && colors.get(i * 3 + 1) == 0 && colors.get(i * 3 + 2) == 0);
        }
    }

    @Test
    public void test5BigMessage() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);

        StringBuilder testText = new StringBuilder();
        for (int i = 0; i < 50000; ++i) {
            testText.append('1');
        }
        documentEditor.textPane().setText(testText.toString());

        boolean colorsReceived = waitForAllMessages(documentEditor, 20);
        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(colorsReceived);
    }

    @Test
    public void test6MultipleMessages() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);

        StringBuilder testText = new StringBuilder();
        for (int i = 0; i < 50000; ++i) {
            testText.append('1');
        }
        try {
            documentEditor.textPane().getStyledDocument().insertString(0, testText.toString(), null);
            // Not reliable, but sometimes triggers in between messages.
            Thread.sleep(500);
            documentEditor.textPane().getStyledDocument().insertString(100, testText.toString(), null);
        } catch (BadLocationException | InterruptedException ex) {
        }

        boolean colorsReceived = waitForAllMessages(documentEditor, 20);
        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(colorsReceived);
    }

    @Test
    public void test7MultipleSimpleMessages() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);

        try {
            documentEditor.textPane().setText("abcdh");
            documentEditor.textPane().getStyledDocument().insertString(0, "11111", null);
        } catch (BadLocationException ex) {
        }

        boolean colorsReceived = waitForAllMessages(documentEditor, 10);
        Vector<Byte> colors = documentEditor.getLastColors();
        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(colorsReceived);
        for (int i = 0; i < 5; ++i) {
            assertTrue(colors.get(i * 3) == 0 && colors.get(i * 3 + 1) == 0 && (colors.get(i * 3 + 2) & 0xFF) == 255);
        }
        for (int i = 5; i < 10; ++i) {
            assertTrue(colors.get(i * 3) == 0 && colors.get(i * 3 + 1) == 0 && colors.get(i * 3 + 2) == 0);
        }
    }
}
