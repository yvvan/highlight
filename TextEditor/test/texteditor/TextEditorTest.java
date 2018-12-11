package texteditor;

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

        // Give some time to the server to finish.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
        }
        sequential.unlock();
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
