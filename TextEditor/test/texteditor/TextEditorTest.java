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
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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
 
    private class JUnitDocumentEditor extends TextDocumentEditor {
        private byte[] lastColors;
        public JUnitDocumentEditor(TextEditor parent, String path) {
            super(parent, path);
        }
        
        @Override
        public void updateColors(long revision, int start, byte[] colors) {
            lastColors = colors;
        }
        
        public byte[] getLastColors() {
            return lastColors;
        }
    }

    @Test
    public void testSendingMessage() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);
        
        documentEditor.textPane().setText("sometext");
        
        int count = 0;
        while (count < 6) {
            if (documentEditor.getLastColors() != null) {
                textEditor.removeDocumentEditor(documentEditor);
                return;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TextEditorTest.class.getName()).log(Level.SEVERE, null, ex);
            }
            ++count;
        }

        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(false);
    }

    @Test
    public void testColorsSimple() {
        textEditor.launchColorServer();
        textEditor.startReadingMessages();
        JUnitDocumentEditor documentEditor = new JUnitDocumentEditor(textEditor, "path");
        textEditor.addDocumentEditor(documentEditor);
        
        documentEditor.textPane().setText("12345abcde");

        int count = 0;
        while (count < 6) {
            if (documentEditor.getLastColors() != null) {
                byte[] colors = documentEditor.getLastColors();
                textEditor.removeDocumentEditor(documentEditor);
                for (int i = 0; i < 5; ++i) {
                    assertTrue(colors[i * 3] == 0 && colors[i * 3 + 1] == 0 && (colors[i * 3 + 2] & 0xFF) == 255);
                }
                for (int i = 5; i < 10; ++i) {
                    assertTrue(colors[i * 3] == 0 && colors[i * 3 + 1] == 0 && colors[i * 3 + 2] == 0);
                }
                return;
            }
            
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(TextEditorTest.class.getName()).log(Level.SEVERE, null, ex);
            }
            ++count;
        }

        textEditor.removeDocumentEditor(documentEditor);
        assertTrue(false);
    }
}
