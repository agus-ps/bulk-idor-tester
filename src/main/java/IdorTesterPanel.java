import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.editor.HttpRequestEditor;
import burp.api.montoya.ui.editor.HttpResponseEditor;
import burp.api.montoya.ui.editor.EditorOptions;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;
import java.util.ArrayList;

public class IdorTesterPanel extends JPanel {

    // Variables UI
    private final JTable requestTable;
    private final DefaultTableModel tableModel;
    private final List<RequestEntry> requestListRef;

    private final HttpRequestEditor requestEditor;
    private final HttpRequestEditor editedRequestEditor;
    private final HttpResponseEditor responseEditor;
    private final HttpResponseEditor originalResponseEditor;

    private final JPanel requestEditorContainer = new JPanel(new BorderLayout());
    private final JButton requestViewButton = new JButton("Original Request ⌄");
    private final JPopupMenu requestViewMenu = new JPopupMenu();

    private final JPanel responseEditorContainer = new JPanel(new BorderLayout());
    private final JButton responseViewButton = new JButton("Original Response ⌄");
    private final JPopupMenu responseViewMenu = new JPopupMenu();

    // Sidebar
    private final JPanel drawerPanel;
    private final ButtonGroup sidebarGroup;
    private final JSplitPane drawerSplitPane;

    public IdorTesterPanel(MontoyaApi api, List<RequestEntry> sharedList) {
        setLayout(new BorderLayout());
        this.requestListRef = sharedList;

        // Editores
        requestEditor = api.userInterface().createHttpRequestEditor();
        editedRequestEditor = api.userInterface().createHttpRequestEditor(EditorOptions.READ_ONLY);
        responseEditor = api.userInterface().createHttpResponseEditor();
        originalResponseEditor = api.userInterface().createHttpResponseEditor(EditorOptions.READ_ONLY);

        setupRequestViewButton();
        setupResponseViewButton();
        setupRequestEditorContainer();
        setupResponseEditorContainer();

        // TABLA Y RENDERER
        tableModel = createRequestTableModel();
        requestTable = new JTable(tableModel);
        requestTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        requestTable.setAutoCreateRowSorter(true);

        // --- APLICAR EL RENDERER DE SEMÁFORO ---
        requestTable.setDefaultRenderer(Object.class, new IdorResultsRenderer());
        requestTable.setRowHeight(25); // Un poco más de aire
        // ---------------------------------------

        setupRequestTableContextMenu(api);
        setupRequestTableSelectionListener();

        JScrollPane listScrollPane = new JScrollPane(requestTable);

        // Paneles inferiores
        JPanel requestPanelWrapper = new JPanel(new BorderLayout());
        requestPanelWrapper.add(requestEditorContainer, BorderLayout.CENTER);
        JPanel responsePanelWrapper = new JPanel(new BorderLayout());
        responsePanelWrapper.add(responseEditorContainer, BorderLayout.CENTER);

        JSplitPane editorsSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestPanelWrapper, responsePanelWrapper);
        editorsSplitPane.setResizeWeight(0.5);
        editorsSplitPane.setBorder(null);

        JSplitPane mainCenterSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listScrollPane, editorsSplitPane);
        mainCenterSplit.setResizeWeight(0.3);
        mainCenterSplit.setBorder(null);

        // App Principal
        JPanel mainAppPanel = new JPanel(new BorderLayout());
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new EmptyBorder(5, 5, 5, 5));
        JButton attackButton = createAttackButton();
        JPanel rightTop = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTop.add(attackButton);
        topBar.add(rightTop, BorderLayout.EAST);

        mainAppPanel.add(topBar, BorderLayout.NORTH);
        mainAppPanel.add(mainCenterSplit, BorderLayout.CENTER);

        // Drawer
        drawerPanel = new JPanel(new BorderLayout());
        drawerPanel.setMinimumSize(new Dimension(0, 0));
        drawerPanel.setVisible(false);

        // Split Global
        drawerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mainAppPanel, drawerPanel);
        drawerSplitPane.setResizeWeight(1.0);
        drawerSplitPane.setDividerSize(0);
        drawerSplitPane.setBorder(null);

        // Barra Lateral
        JPanel buttonStrip = new JPanel();
        buttonStrip.setLayout(new BoxLayout(buttonStrip, BoxLayout.Y_AXIS));
        buttonStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Color.decode("#CCCCCC")));
        buttonStrip.setBackground(new Color(245, 245, 245));

        sidebarGroup = new ButtonGroup();
        JToggleButton btnHeaders = new VerticalToggleButton("Headers");
        JToggleButton btnSettings = new VerticalToggleButton("Settings");
        configureSidebarButton(btnHeaders, createHeadersPanel());
        configureSidebarButton(btnSettings, new JPanel());

        buttonStrip.add(btnHeaders);
        buttonStrip.add(btnSettings);
        buttonStrip.add(Box.createVerticalGlue());

        add(drawerSplitPane, BorderLayout.CENTER);
        add(buttonStrip, BorderLayout.EAST);

        for (RequestEntry entry : sharedList) addRequest(entry);
    }

    // ========================================
    // RENDERER PERSONALIZADO (SEMÁFORO)
    // ========================================
    class IdorResultsRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (!isSelected) {
                int modelRow = table.convertRowIndexToModel(row);
                if (modelRow < requestListRef.size()) {
                    RequestEntry entry = requestListRef.get(modelRow);

                    if (entry.getResponse() == null) {
                        c.setBackground(Color.WHITE);
                        c.setForeground(Color.BLACK);
                    } else {
                        int origStatus = entry.getOriginalResponse() != null ? entry.getOriginalResponse().statusCode() : 0;
                        int newStatus = entry.getStatusCode();

                        // Lógica de colores IDOR
                        if (isSuccess(origStatus) && isSuccess(newStatus)) {
                            // Peligro: Ambos 200 OK (Rojo suave)
                            c.setBackground(new Color(255, 220, 220));
                            c.setForeground(new Color(180, 0, 0));
                        } else if (isSuccess(origStatus) && (newStatus == 401 || newStatus == 403)) {
                            // Seguro: Bloqueado (Verde suave)
                            c.setBackground(new Color(220, 255, 220));
                            c.setForeground(new Color(0, 100, 0));
                        } else if (origStatus != newStatus) {
                            // Diferente: Amarillo
                            c.setBackground(new Color(255, 250, 200));
                            c.setForeground(Color.BLACK);
                        } else {
                            // Igualdad neutral (ej: ambos 403)
                            c.setBackground(Color.WHITE);
                            c.setForeground(Color.BLACK);
                        }
                    }
                }
            }
            ((JLabel) c).setHorizontalAlignment(SwingConstants.CENTER);
            return c;
        }

        private boolean isSuccess(int status) { return status >= 200 && status < 300; }
    }

    // ========================================
    // MÉTODOS DE TABLA (UPDATED)
    // ========================================
    private DefaultTableModel createRequestTableModel() {
        // Cambiamos "Same body" por "Similarity"
        String[] columns = {"ID", "Host", "Method", "URL", "Status", "Length", "Time", "Similarity"};
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public void addRequest(RequestEntry entry) {
        tableModel.addRow(new Object[]{
                entry.getId(), entry.getHost(), entry.getMethod(), entry.getRequest().path(), "", "", "", ""
        });
    }

    public void updateTableRow(int rowIndex, int status, int length, long responseTimeMs, int similarity) {
        RequestEntry entry = requestListRef.get(rowIndex);
        int origLength = entry.getOriginalResponse() != null ? entry.getOriginalResponse().body().length() : 0;
        int delta = length - origLength;
        String deltaStr = delta == 0 ? "(0)" : (delta > 0 ? "(+" + delta + ")" : "(" + delta + ")");

        tableModel.setValueAt(status, rowIndex, 4);
        tableModel.setValueAt(length + " " + deltaStr, rowIndex, 5); // Length con Delta
        tableModel.setValueAt(responseTimeMs + " ms", rowIndex, 6);
        tableModel.setValueAt(similarity + "%", rowIndex, 7); // Similitud

        // Disparar repintado para que el Renderer actualice colores
        tableModel.fireTableRowsUpdated(rowIndex, rowIndex);
    }

    // ========================================
    // OTROS COMPONENTES (IGUAL QUE ANTES)
    // ========================================

    // ... [Mantén aquí el resto de métodos: configureSidebarButton, closeDrawer, refreshEditors, etc.]
    // ... [Mantén la clase VerticalToggleButton tal cual te la pasé en el mensaje anterior]
    // ... [Mantén createHeadersPanel, setupRequestViewButton, etc.]

    // (Para no hacer el código gigante, asumo que copias los métodos auxiliares del archivo anterior
    //  o si prefieres te paso TODO el archivo junto de nuevo, pero solo cambiaron las partes de arriba
    //  y la inner class del Renderer).

    // A continuación pego los métodos auxiliares esenciales para que compile si copias todo:

    private void configureSidebarButton(JToggleButton button, JPanel content) {
        sidebarGroup.add(button);
        button.addActionListener(e -> {
            boolean isAlreadyOpen = drawerPanel.isVisible();
            Component currentContent = (drawerPanel.getComponentCount() > 0) ? drawerPanel.getComponent(0) : null;
            if (button.isSelected()) {
                if (!isAlreadyOpen) {
                    drawerPanel.removeAll(); drawerPanel.add(content, BorderLayout.CENTER);
                    drawerPanel.setVisible(true); drawerSplitPane.setDividerSize(4);
                    drawerSplitPane.setDividerLocation(getWidth() - 450);
                } else if (currentContent != content) {
                    drawerPanel.removeAll(); drawerPanel.add(content, BorderLayout.CENTER);
                    drawerPanel.revalidate(); drawerPanel.repaint();
                } else closeDrawer();
            } else closeDrawer();
        });
    }

    private void closeDrawer() {
        sidebarGroup.clearSelection();
        drawerPanel.setVisible(false);
        drawerSplitPane.setDividerSize(0);
    }

    class VerticalToggleButton extends JToggleButton {
        public VerticalToggleButton(String text) {
            super(text);
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setForeground(Color.decode("#333333"));
            setFocusPainted(false); setContentAreaFilled(false);
            setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        }
        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize(); return new Dimension(d.height, d.width);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected()) {
                g2.setColor(Color.WHITE); g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(231, 111, 67)); g2.fillRect(getWidth() - 3, 0, 3, getHeight());
            }
            AffineTransform original = g2.getTransform();
            g2.translate(getWidth() / 2.0, getHeight() / 2.0); g2.rotate(Math.toRadians(90));
            g2.setColor(getForeground());
            FontMetrics fm = g2.getFontMetrics(); String text = getText();
            g2.drawString(text, -fm.stringWidth(text) / 2, fm.getAscent() / 2 - 2);
            g2.setTransform(original); g2.dispose();
        }
    }

    private JPanel createHeadersPanel() {
        JPanel container = new JPanel(new BorderLayout());
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, Color.LIGHT_GRAY), new EmptyBorder(10, 10, 10, 10)));
        JLabel title = new JLabel("Headers Configuration");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f)); title.setBorder(new EmptyBorder(0,0,10,0));
        container.add(title, BorderLayout.NORTH);
        DefaultTableModel headersModel = new DefaultTableModel(new Object[]{"Header", "Value"}, 0);
        JTable headersTable = new JTable(headersModel); headersTable.setRowHeight(22);
        container.add(new JScrollPane(headersTable), BorderLayout.CENTER);

        JPanel buttonsGrid = new JPanel(new GridLayout(0, 1, 5, 5));
        JButton btnAdd = new JButton("Add"); JButton btnEdit = new JButton("Edit");
        JButton btnRemove = new JButton("Remove"); JButton btnPaste = new JButton("Paste");
        btnAdd.setPreferredSize(new Dimension(80, 25));
        buttonsGrid.add(btnAdd); buttonsGrid.add(btnEdit); buttonsGrid.add(btnRemove); buttonsGrid.add(btnPaste);
        JPanel flowWrapper = new JPanel(new BorderLayout()); flowWrapper.add(buttonsGrid, BorderLayout.NORTH);
        flowWrapper.setBorder(new EmptyBorder(0, 0, 0, 5));
        container.add(flowWrapper, BorderLayout.WEST);

        for (String h : IdorTesterExtension.getCustomHeaders()) {
            int idx = h.indexOf(":");
            if (idx > 0) headersModel.addRow(new Object[]{h.substring(0, idx).trim(), h.substring(idx + 1).trim()});
        }
        Runnable saveLogic = () -> {
            List<String> newHeaders = new ArrayList<>();
            for (int i = 0; i < headersModel.getRowCount(); i++) {
                String h = (String) headersModel.getValueAt(i, 0); String v = (String) headersModel.getValueAt(i, 1);
                if (h != null && !h.isEmpty()) newHeaders.add(h + ": " + v);
            }
            IdorTesterExtension.setCustomHeaders(newHeaders);
        };
        btnAdd.addActionListener(e -> {
            String in = JOptionPane.showInputDialog(this, "Format: Header: Value");
            if (in != null && in.contains(":")) {
                String[] p = in.split(":", 2); headersModel.addRow(new Object[]{p[0].trim(), p[1].trim()}); saveLogic.run();
            }
        });
        btnEdit.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) {
                String oldH = (String) headersModel.getValueAt(row, 0); String oldV = (String) headersModel.getValueAt(row, 1);
                String in = JOptionPane.showInputDialog(this, "Edit:", oldH + ": " + oldV);
                if (in != null && in.contains(":")) {
                    String[] p = in.split(":", 2); headersModel.setValueAt(p[0].trim(), row, 0);
                    headersModel.setValueAt(p[1].trim(), row, 1); saveLogic.run();
                }
            }
        });
        btnRemove.addActionListener(e -> {
            int row = headersTable.getSelectedRow(); if (row >= 0) { headersModel.removeRow(row); saveLogic.run(); }
        });
        btnPaste.addActionListener(e -> {
            try {
                String data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor).toString();
                if(data.contains(":")) {
                    String[] parts = data.split(":", 2); headersModel.addRow(new Object[]{parts[0].trim(), parts[1].trim()}); saveLogic.run();
                }
            } catch (Exception ex) {}
        });
        return container;
    }

    public void refreshEditorIfSelected(int updatedRowIndex) {
        int viewRow = requestTable.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = requestTable.convertRowIndexToModel(viewRow);
            if (modelRow == updatedRowIndex) refreshEditors();
        }
    }
    public void refreshEditors() {
        int row = requestTable.getSelectedRow();
        if (row >= 0 && row < requestListRef.size()) {
            RequestEntry selected = requestListRef.get(row);
            requestEditor.setRequest(selected.getRequest());
            editedRequestEditor.setRequest(selected.getEditedRequest() != null ? selected.getEditedRequest() : null);
            responseEditor.setResponse(selected.getResponse());
            originalResponseEditor.setResponse(selected.getOriginalResponse());
        }
    }

    private void setupRequestViewButton() {
        requestViewButton.addActionListener(e -> requestViewMenu.show(requestViewButton, 0, requestViewButton.getHeight()));
        requestViewMenu.add(new JMenuItem("Original Request")).addActionListener(e -> switchEditor(requestEditorContainer, requestEditor, requestViewButton, "Original Request ⌄"));
        requestViewMenu.add(new JMenuItem("Edited Request")).addActionListener(e -> switchEditor(requestEditorContainer, editedRequestEditor, requestViewButton, "Edited Request ⌄"));
    }
    private void setupResponseViewButton() {
        responseViewButton.addActionListener(e -> responseViewMenu.show(responseViewButton, 0, responseViewButton.getHeight()));
        responseViewMenu.add(new JMenuItem("Original Response")).addActionListener(e -> switchEditor(responseEditorContainer, originalResponseEditor, responseViewButton, "Original Response ⌄"));
        responseViewMenu.add(new JMenuItem("Edited Response")).addActionListener(e -> switchEditor(responseEditorContainer, responseEditor, responseViewButton, "Edited Response ⌄"));
    }
    private void switchEditor(JPanel container, Object editorObj, JButton btn, String text) {
        container.removeAll(); container.add(createHeaderPanel(btn), BorderLayout.NORTH);
        if (editorObj instanceof HttpRequestEditor) container.add(((HttpRequestEditor)editorObj).uiComponent(), BorderLayout.CENTER);
        else container.add(((HttpResponseEditor)editorObj).uiComponent(), BorderLayout.CENTER);
        btn.setText(text); container.revalidate(); container.repaint();
    }
    private JPanel createHeaderPanel(JButton btn) {
        JPanel p = new JPanel(new BorderLayout()); p.setBorder(BorderFactory.createEmptyBorder(2,5,2,5));
        p.add(btn, BorderLayout.WEST); return p;
    }
    private void setupRequestEditorContainer() {
        requestEditorContainer.add(createHeaderPanel(requestViewButton), BorderLayout.NORTH);
        requestEditorContainer.add(requestEditor.uiComponent(), BorderLayout.CENTER);
    }
    private void setupResponseEditorContainer() {
        responseEditorContainer.add(createHeaderPanel(responseViewButton), BorderLayout.NORTH);
        responseEditorContainer.add(originalResponseEditor.uiComponent(), BorderLayout.CENTER);
    }
    private void setupRequestTableSelectionListener() {
        requestTable.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) refreshEditors(); });
    }
    private void setupRequestTableContextMenu(MontoyaApi api) {
        JPopupMenu menu = new JPopupMenu();
        menu.add(new JMenuItem("Clear All")).addActionListener(e -> { requestListRef.clear(); tableModel.setRowCount(0); });
        requestTable.setComponentPopupMenu(menu);
    }
    private JButton createAttackButton() {
        JButton attackButton = new JButton("Start Attack");
        attackButton.setFont(attackButton.getFont().deriveFont(Font.BOLD));
        attackButton.setBackground(Color.decode("#E76F43")); attackButton.setForeground(Color.white);
        attackButton.setFocusPainted(false); attackButton.addActionListener(e -> IdorTesterExtension.startAttack());
        return attackButton;
    }
}