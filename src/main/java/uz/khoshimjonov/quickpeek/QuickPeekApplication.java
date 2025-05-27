package uz.khoshimjonov.quickpeek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class QuickPeekApplication extends Application implements NativeKeyListener {
    private static final int MAX_CONTENT_SIZE = 10 * 1024 * 1024;
    private static final String APP_NAME = "JSON/XML Viewer";

    private final JavaObjectFormatter javaObjectFormatter = new JavaObjectFormatter();

    private volatile Stage viewerStage;
    private volatile boolean isShuttingDown = false;
    private TextArea textArea;
    private VBox searchBox;
    private TextField searchField;
    private Label resultLabel;
    private final List<Integer> searchResults = new ArrayList<>();
    private int currentSearchIndex = 0;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private TrayIcon trayIcon;
    private boolean searchVisible = false;


    @Override
    public void start(Stage primaryStage) {
        setupObjectMapper();
        setupTrayIcon();
        setupGlobalHotkey();

        primaryStage.hide();
        Platform.setImplicitExit(false);

        System.out.println("JSON/XML Viewer is running. Press Ctrl+Shift+Y to view clipboard content.");
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "false");
        System.setProperty("prism.lcdtext", "false");
        launch(args);
    }

    private void setupObjectMapper() {
        objectMapper.getFactory().setCodec(objectMapper);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private void setupTrayIcon() {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTray is not supported");
            return;
        }

        try {
            SystemTray tray = SystemTray.getSystemTray();

            Image image = createTrayIconImage();

            PopupMenu popup = new PopupMenu();
            MenuItem exitItem = new MenuItem("Exit");
            exitItem.addActionListener(e -> {
                Platform.exit();
                System.exit(0);
            });

            MenuItem showItem = new MenuItem("Show Viewer");
            showItem.addActionListener(e -> Platform.runLater(() -> {
                String clipboardText = getClipboardText();
                if (clipboardText != null && !clipboardText.trim().isEmpty()) {
                    showViewer(clipboardText);
                }
            }));

            popup.add(showItem);
            popup.add(exitItem);

            trayIcon = new TrayIcon(image, "JSON/XML Viewer", popup);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> Platform.runLater(() -> {
                String clipboardText = getClipboardText();
                if (clipboardText != null && !clipboardText.trim().isEmpty()) {
                    showViewer(clipboardText);
                }
            }));

            tray.add(trayIcon);
        } catch (AWTException e) {
            System.out.println("TrayIcon could not be added.");
        }
    }

    private Image createTrayIconImage() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = image.createGraphics();

        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            Font font = new Font(Font.SANS_SERIF, Font.PLAIN, size); // Much larger font
            g2d.setFont(font);
            g2d.setColor(java.awt.Color.GREEN); // Blue color for the text

            FontMetrics fm = g2d.getFontMetrics();
            String text = "{ }";
            int textWidth = fm.stringWidth(text);
            int textHeight = fm.getAscent();

            int x = (size - textWidth) / 2;
            int y = (size - textHeight) / 2 + textHeight - 8;

            g2d.drawString(text, x, y);
        } finally {
            g2d.dispose();
        }

        return image.getScaledInstance(16, 16, Image.SCALE_SMOOTH);
    }

    private void setupGlobalHotkey() {
        try {
            Logger logger = Logger.getLogger(GlobalScreen.class.getPackage().getName());
            logger.setLevel(Level.OFF);

            GlobalScreen.registerNativeHook();
            GlobalScreen.addNativeKeyListener(this);
        } catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());
        }
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        if (isShuttingDown) return;
        // Alt + Shift + F
        if ((e.getModifiers() & NativeKeyEvent.ALT_L_MASK) != 0 &&
                (e.getModifiers() & NativeKeyEvent.SHIFT_MASK) != 0 &&
                e.getKeyCode() == NativeKeyEvent.VC_F) {

            Platform.runLater(() -> {
                String clipboardText = getClipboardText();
                if (clipboardText != null && !clipboardText.trim().isEmpty()) {
                    showViewer(clipboardText);
                }
            });
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
    }

    private String getClipboardText() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            String content = (String) clipboard.getData(DataFlavor.stringFlavor);
            if (content != null && content.length() > MAX_CONTENT_SIZE) {
                showWarningNotification("Content too large", "Clipboard content exceeds " + (MAX_CONTENT_SIZE / 1024 / 1024) + "MB limit");
                return null;
            }
            return content;
        } catch (Exception e) {
            return null;
        }
    }

    private void showWarningNotification(String title, String message) {
        if (trayIcon != null) {
            try {
                trayIcon.displayMessage(title, message, TrayIcon.MessageType.WARNING);
            } catch (Exception ignored) {
            }
        }
    }

    private void showViewer(String text) {
        if (viewerStage != null) {
            viewerStage.close();
            viewerStage = null;
        }

        viewerStage = new Stage();
        viewerStage.initStyle(StageStyle.UNDECORATED);
        viewerStage.setTitle(APP_NAME);

        VBox root = new VBox();
        root.setStyle("-fx-background-color: #282c34; -fx-border-color: #3c3c3c; -fx-border-width: 2;");

        searchBox = createSearchBox();
        searchBox.setVisible(false);
        searchBox.setManaged(false);

        textArea = new TextArea();
        textArea.setStyle(
                "-fx-control-inner-background: #282c34; " +
                        "-fx-text-fill: white; " +
                        "-fx-highlight-fill: #264f78; " +
                        "-fx-highlight-text-fill: white; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 16px; " +
                        "-fx-border-color: transparent; " +
                        "-fx-focus-color: transparent; " +
                        "-fx-faint-focus-color: transparent;"
        );
        textArea.setWrapText(false);
        textArea.setEditable(true);

        String formattedText = formatContent(text);
        if (formattedText != null) {
            textArea.setText(formattedText);
            textArea.setPrefRowCount(Math.min(30, Math.max(10, formattedText.split("\n").length + 2)));
            textArea.setPrefColumnCount(Math.min(120, getMaxLineLength(formattedText) + 5));
        } else {
            textArea.setPrefRowCount(Math.min(30, Math.max(10, text.split("\n").length + 2)));
            textArea.setPrefColumnCount(Math.min(120, getMaxLineLength(text) + 5));
            textArea.setText("Invalid JSON/XML format:\n" + text);
        }

        root.getChildren().addAll(searchBox, textArea);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);

        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                if (searchVisible) {
                    hideSearch();
                } else {
                    hideViewer();
                }
            } else if (event.isControlDown() && event.getCode() == KeyCode.F) {
                toggleSearch();
                event.consume();
            }
        });

        viewerStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                Platform.runLater(this::hideViewer);
            }
        });

        viewerStage.setScene(scene);
        viewerStage.setAlwaysOnTop(true);

        viewerStage.show();
        Platform.runLater(() -> {
            viewerStage.sizeToScene();
            Screen targetScreen = getCurrentMouseScreen();
            Rectangle2D screenBounds = targetScreen.getVisualBounds();
            double stageWidth = viewerStage.getWidth();
            double stageHeight = viewerStage.getHeight();

            viewerStage.setX((screenBounds.getMinX() + (screenBounds.getWidth() - stageWidth) / 2));
            viewerStage.setY(screenBounds.getMinY() + (screenBounds.getHeight() - stageHeight) / 2);
        });
        viewerStage.toFront();
        viewerStage.requestFocus();
        textArea.requestFocus();

        Platform.runLater(this::applyCustomScrollbarStyle);
    }

    private Screen getCurrentMouseScreen() {
        try {
            PointerInfo pointerInfo = MouseInfo.getPointerInfo();
            if (pointerInfo == null) {
                return Screen.getPrimary();
            }

            java.awt.Point mouseLocation = pointerInfo.getLocation();

            for (Screen screen : Screen.getScreens()) {
                Rectangle2D bounds = screen.getBounds();
                if (bounds.contains(mouseLocation.x, mouseLocation.y)) {
                    return screen;
                }
            }

            return Screen.getPrimary();

        } catch (Exception e) {
            System.out.println("Could not determine mouse screen: " + e.getMessage());
            return Screen.getPrimary();
        }
    }

    private int getMaxLineLength(String text) {
        return text.lines().mapToInt(String::length).max().orElse(80);
    }

    private void applyCustomScrollbarStyle() {
        try {
            String scrollbarCss =
                    ".scroll-bar:vertical { " +
                            "    -fx-pref-width: 8px; " +
                            "} " +
                            ".scroll-bar:horizontal { " +
                            "    -fx-pref-height: 8px; " +
                            "} " +
                            ".scroll-bar .track { " +
                            "    -fx-background-color: transparent; " +
                            "    -fx-border-color: transparent; " +
                            "} " +
                            ".scroll-bar .thumb { " +
                            "    -fx-background-color: #464647; " +
                            "    -fx-background-radius: 4px; " +
                            "} " +
                            ".scroll-bar .thumb:hover { " +
                            "    -fx-background-color: #5a5a5a; " +
                            "} " +
                            ".scroll-bar .increment-button, .scroll-bar .decrement-button { " +
                            "    -fx-background-color: transparent; " +
                            "    -fx-border-color: transparent; " +
                            "    -fx-pref-width: 0; " +
                            "    -fx-pref-height: 0; " +
                            "}";

            if (viewerStage != null && viewerStage.getScene() != null) {
                viewerStage.getScene().getStylesheets().add("data:text/css;base64," +
                        java.util.Base64.getEncoder().encodeToString(scrollbarCss.getBytes()));
            }
        } catch (Exception e) {
            System.out.println("Could not apply custom scrollbar styling: " + e.getMessage());
        }
    }

    private VBox createSearchBox() {
        VBox searchContainer = new VBox(5);
        searchContainer.setPadding(new Insets(10));
        searchContainer.setStyle("-fx-background-color: #2d2d30; -fx-border-color: #3c3c3c; -fx-border-width: 0 0 1 0;");

        HBox searchControls = new HBox(5);

        searchField = new TextField();
        searchField.setPromptText("Find");
        searchField.setStyle(
                "-fx-background-color: #3c3c3c; " +
                        "-fx-text-fill: white; " +
                        "-fx-prompt-text-fill: #888; " +
                        "-fx-border-color: #666; " +
                        "-fx-font-family: 'Consolas', 'Monaco', monospace; " +
                        "-fx-font-size: 12px;"
        );
        searchField.setPrefWidth(200);

        javafx.scene.control.Button prevBtn = new javafx.scene.control.Button("▲");
        prevBtn.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        prevBtn.setPrefWidth(25);

        javafx.scene.control.Button nextBtn = new javafx.scene.control.Button("▼");
        nextBtn.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white; -fx-font-size: 10px;");
        nextBtn.setPrefWidth(25);

        resultLabel = new Label();
        resultLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        javafx.scene.control.Button closeBtn = new javafx.scene.control.Button("×");
        closeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; -fx-font-size: 16px;");
        closeBtn.setPrefWidth(25);

        prevBtn.setOnAction(e -> navigateSearch(-1));
        nextBtn.setOnAction(e -> navigateSearch(1));
        closeBtn.setOnAction(e -> hideSearch());

        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (!newText.isEmpty()) {
                performSearch();
            } else {
                clearSearchHighlights();
            }
        });

        searchField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) {
                    navigateSearch(-1);
                } else {
                    navigateSearch(1);
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                hideSearch();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchControls.getChildren().addAll(searchField, prevBtn, nextBtn, resultLabel, spacer, closeBtn);
        searchContainer.getChildren().add(searchControls);

        return searchContainer;
    }

    private void toggleSearch() {
        if (searchVisible) {
            hideSearch();
        } else {
            showSearch();
        }
    }

    private void showSearch() {
        searchVisible = true;
        searchBox.setVisible(true);
        searchBox.setManaged(true);
        searchField.requestFocus();
        if (!searchField.getText().isEmpty()) {
            performSearch();
        }
    }

    private void hideSearch() {
        searchVisible = false;
        searchBox.setVisible(false);
        searchBox.setManaged(false);
        clearSearchHighlights();
        textArea.requestFocus();
    }

    private void performSearch() {
        if (textArea == null) return;
        //textArea.requestFocus();
        String query = searchField.getText();
        if (query.isEmpty()) {
            clearSearchHighlights();
            return;
        }

        searchResults.clear();
        currentSearchIndex = 0;

        String text = textArea.getText();
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();

        int index = 0;
        while ((index = lowerText.indexOf(lowerQuery, index)) != -1) {
            searchResults.add(index);
            index += query.length();
        }

        updateResultLabel();

        if (!searchResults.isEmpty()) {
            highlightSearchResults();
            scrollToSearchResult(0);
        }
    }

    private void navigateSearch(int direction) {
        if (searchResults.isEmpty()) return;

        currentSearchIndex += direction;
        if (currentSearchIndex < 0) {
            currentSearchIndex = searchResults.size() - 1;
        } else if (currentSearchIndex >= searchResults.size()) {
            currentSearchIndex = 0;
        }

        updateResultLabel();
        scrollToSearchResult(currentSearchIndex);
    }

    private void scrollToSearchResult(int index) {
        if (index < 0 || index >= searchResults.size()) return;

        int position = searchResults.get(index);
        textArea.selectRange(position, position + searchField.getText().length());
    }

    private void highlightSearchResults() {
        // JavaFX TextArea doesn't support multiple highlights easily
        // The selection will show the current match
    }

    private void clearSearchHighlights() {
        searchResults.clear();
        currentSearchIndex = 0;
        updateResultLabel();
        if (textArea != null) {
            textArea.deselect();
        }
    }

    private void updateResultLabel() {
        if (resultLabel == null) return;
        if (searchResults.isEmpty()) {
            resultLabel.setText("No results");
        } else {
            resultLabel.setText((currentSearchIndex + 1) + " of " + searchResults.size());
        }
    }

    private void hideViewer() {
/*        if (viewerStage != null) {
            viewerStage.hide();
        }
        searchResults.clear();
        currentSearchIndex = 0;
        //*/

        searchVisible = false;
        Platform.runLater(() -> {
            try {
                // 1. Clear UI event handlers first
                clearEventHandlers();

                // 2. Clear and nullify UI components
                clearUIComponents();

                // 3. Clear data structures
                clearDataStructures();

                // 4. Close and nullify stage
                if (viewerStage != null) {
                    viewerStage.close();
                    viewerStage = null;
                }
            } catch (Exception ignored) {}
        });
    }

    private void clearEventHandlers() {
        try {
            if (viewerStage != null && viewerStage.getScene() != null) {
                // Clear scene event handlers
                viewerStage.getScene().setOnKeyPressed(null);
                viewerStage.setOnCloseRequest(null);

                // Clear stage focus listener (this is tricky, but we can set a new empty one)
                viewerStage.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    // Empty listener to replace the old one
                });
            }
        } catch (Exception ignored) {}
    }

    private void clearUIComponents() {
        try {
            // Clear text area
            if (textArea != null) {
                textArea.clear();
                textArea.setOnKeyPressed(null);
                textArea = null;
            }

            // Clear search components
            if (searchField != null) {
                searchField.clear();
                searchField.textProperty().removeListener((obs, oldText, newText) -> {});
                searchField.setOnKeyPressed(null);
                searchField = null;
            }

            // Clear search box and its children
            if (searchBox != null) {
                searchBox.getChildren().forEach(node -> {
                    if (node instanceof javafx.scene.control.Button) {
                        ((javafx.scene.control.Button) node).setOnAction(null);
                    }
                });
                searchBox.getChildren().clear();
                searchBox = null;
            }

            if (resultLabel != null) {
                resultLabel.setText("");
                resultLabel = null;
            }

        } catch (Exception ignored) {}
    }

    private void clearDataStructures() {
        try {
            // Clear collections
            searchResults.clear();
            // Reset counters
            currentSearchIndex = 0;
            searchVisible = false;

        } catch (Exception ignored) {}
    }

    private String formatContent(String content) {
        content = content.trim();

        if (content.startsWith("{") || content.startsWith("[") || ((content.contains("{") || content.contains("}")) && content.contains(":"))) {
            return formatJson(content);
        } else if (content.startsWith("<") || content.contains("/>") || content.contains("</")) {
            return formatXml(content);
        } else if (javaObjectFormatter.isJavaObjectString(content)) {
            return formatJavaObject(content);
        }

        return null;
    }

    private String formatJson(String json) {
        try {
            JsonNode jsonNode = objectMapper.readTree(json);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (Exception e) {
            System.out.println("Could not format JSON: " + e.getMessage());
            System.out.println("Trying to parse manually");
            return formatJsonManually(json);
        }
    }

    private String formatJavaObject(String object) {
        try {
            if (object == null) {
                return null;
            }
            return javaObjectFormatter.formatJavaObject(object);
        } catch (Exception e) {
            return null;
        }
    }

    private String formatJsonManually(String json) {
        try {
            json = json.trim();
            if (json.isEmpty()) {
                return null;
            }

            StringBuilder formatted = new StringBuilder();
            int indentLevel = 0;
            boolean inString = false;
            boolean escaped = false;
            char prevChar = 0;

            for (int i = 0; i < json.length(); i++) {
                char currentChar = json.charAt(i);
                char nextChar = i + 1 < json.length() ? json.charAt(i + 1) : 0;

                if (currentChar == '"' && !escaped) {
                    inString = !inString;
                    formatted.append(currentChar);
                    escaped = false;
                    continue;
                }

                if (inString) {
                    formatted.append(currentChar);
                    escaped = (currentChar == '\\' && !escaped);
                    continue;
                }

                escaped = false;

                switch (currentChar) {
                    case '{':
                    case '[':
                        formatted.append(currentChar);
                        if (nextChar != '}' && nextChar != ']') {
                            indentLevel++;
                            appendNewlineWithIndent(formatted, indentLevel);
                        }
                        break;

                    case '}':
                    case ']':
                        if (prevChar != '{' && prevChar != '[' && !Character.isWhitespace(prevChar)) {
                            indentLevel = Math.max(0, indentLevel - 1);
                            appendNewlineWithIndent(formatted, indentLevel);
                        } else {
                            indentLevel = Math.max(0, indentLevel - 1);
                        }
                        formatted.append(currentChar);
                        break;

                    case ',':
                        formatted.append(currentChar);
                        if (!isClosingBracketNext(json, i)) {
                            appendNewlineWithIndent(formatted, indentLevel);
                        }
                        break;

                    case ':':
                        formatted.append(currentChar);
                        if (nextChar != ' ' && nextChar != '\t') {
                            formatted.append(' ');
                        }
                        break;

                    case ' ':
                    case '\t':
                    case '\n':
                    case '\r':
                        if (!formatted.isEmpty() && formatted.charAt(formatted.length() - 1) != ' ') {
                            char lastChar = formatted.charAt(formatted.length() - 1);
                            if (lastChar != ':' && lastChar != ',' && lastChar != '{' && lastChar != '[') {
                                formatted.append(' ');
                            }
                        }
                        break;

                    default:
                        formatted.append(currentChar);
                        break;
                }

                if (!Character.isWhitespace(currentChar)) {
                    prevChar = currentChar;
                }
            }

            return formatted.toString().trim();

        } catch (Exception e) {
            System.out.println("Manual JSON formatting also failed: " + e.getMessage());
            return json;
        }
    }

    private boolean isClosingBracketNext(String json, int currentIndex) {
        for (int i = currentIndex + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '}' || c == ']') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return false;
    }

    private void appendNewlineWithIndent(StringBuilder sb, int indentLevel) {
        sb.append('\n');
        sb.append("  ".repeat(Math.max(0, indentLevel)));
    }


    private String formatXml(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");

            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            try (StringWriter writer = new StringWriter()) {
                transformer.transform(new DOMSource(doc), new StreamResult(writer));
                return writer.toString();
            }

        } catch (Exception e) {
            System.out.println("Could not format XML: " + e.getMessage());
            System.out.println("Trying to parse manually");
            xml = xml.trim();
            if (xml.isEmpty()) {
                return null;
            }
            String[] tags = xml.split("(?<=>)");
            if (tags.length == 0) {
                return xml;
            }
            StringBuilder formattedXml = new StringBuilder();
            int level = 0;
            for (int i = 0; i < tags.length; i++) {
                String tag = tags[i].trim();
                String nextTag = i + 1 < tags.length ? tags[i + 1] : null;
                boolean appendNextLine = nextTag != null && !nextTag.trim().startsWith("<");
                String currentTag = appendNextLine ? tag + nextTag : tag;
                if (currentTag.startsWith("</")) {
                    level--;
                }
                formattedXml.append("\n").append("    ".repeat(Math.abs(level))).append(tag);
                if (appendNextLine) {
                    formattedXml.append(nextTag);
                    i++;
                }
                if (!currentTag.contains("</") && !currentTag.startsWith("<?") && !currentTag.endsWith("?>") && !currentTag.startsWith("!--")) {
                    level++;
                }
            }
            return formattedXml.isEmpty() ? xml : formattedXml.toString().trim();
        }
    }

    @Override
    public void stop() {
        isShuttingDown = true;
        try {
            if (trayIcon != null) {
                SystemTray.getSystemTray().remove(trayIcon);
            }
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            e.printStackTrace();
        }
    }
}