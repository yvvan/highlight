package texteditor;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JTextPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class TextEditorTest {
    private static TextEditor textEditor;

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
        textEditor.createServerSocket();
    }

    @After
    public void tearDown() {
        textEditor.disconnect();
    }

    @Test
    public void testInit() {
        assertTrue(textEditor.isConnectionClosed());
    }

    @Test
    public void testLaunchColorServer() {
        textEditor.launchColorServer();

        assertFalse(textEditor.isConnectionClosed());
    }

    @Test
    public void testWithMessages() {
        textEditor.launchColorServer();

        textEditor.startReadingMessages();
    }

    private boolean waitForResponse(String str) {
        // Ideally it should be Mock to handle this.
        final AtomicBoolean called = new AtomicBoolean(false);
        JTextPane text = textEditor.getFirstTextPane();
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void removeUpdate(DocumentEvent e) {}
            @Override public void insertUpdate(DocumentEvent e) { }

            @Override public void changedUpdate(DocumentEvent arg0) {
                called.set(true);
            }
        });

        text.setText(str);

        int count = 0;
        // Wait for 6 seconds in the worst case
        while (called.get() == false && count < 6) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TextEditorTest.class.getName()).log(Level.SEVERE, null, ex);
            }
            ++count;
        }

        text.setText("");
        return called.get();
    }

    @Test
    public void testSendingMessage() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();

        if (!textEditor.hasDocumentEditor())
            textEditor.addDocumentEditor("path");
        boolean hasResponse = waitForResponse("anything");

        assertTrue(hasResponse);
    }

    @Test
    public void testColorsSimple() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        if (!textEditor.hasDocumentEditor())
            textEditor.addDocumentEditor("path");
        JTextPane text = textEditor.getFirstTextPane();
        waitForResponse("12345abcde");

        // TODO: test colors properly
        for (int i = 0; i < 5; ++i) {
            Element elem = text.getStyledDocument().getCharacterElement(i);
            System.out.println(elem.getAttributes());
            //assertTrue(text.getCharacterAttributes().getAttribute(StyleConstants.Foreground) == Color.BLUE);
        }
        for (int i = 5; i < 10; ++i) {
            Element elem = text.getStyledDocument().getCharacterElement(i);
            System.out.println(elem.getAttributes());
            //assertTrue(text.getCharacterAttributes().getAttribute(StyleConstants.Foreground) == Color.BLACK);
        }
    }
}
