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

        requestTable.setDefaultRenderer(Object.class, new IdorResultsRenderer());
        requestTable.setRowHeight(25);

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

        // Barra Lateral (FIX: Colores dinámicos)
        JPanel buttonStrip = new JPanel();
        buttonStrip.setLayout(new BoxLayout(buttonStrip, BoxLayout.Y_AXIS));
        // FIX: Usamos el color de borde del sistema, no hardcodeado
        buttonStrip.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UIManager.getColor("Component.borderColor")));
        // FIX: No seteamos background hardcodeado, dejamos que tome el del tema (o null)

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
    // RENDERER SEMÁFORO (ADAPTADO A DARK MODE)
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
                        // FIX: Usar colores de tabla por defecto
                        c.setBackground(table.getBackground());
                        c.setForeground(table.getForeground());
                    } else {
                        int origStatus = entry.getOriginalResponse() != null ? entry.getOriginalResponse().statusCode() : 0;
                        int newStatus = entry.getStatusCode();

                        // Colores un poco más oscuros/saturados para que se vean bien en ambos temas
                        if (isSuccess(origStatus) && isSuccess(newStatus)) {
                            // Rojo: Potencial vulnerabilidad
                            c.setBackground(new Color(255, 100, 100, 100)); // Rojo semi-transparente
                            c.setForeground(UIManager.getColor("Table.foreground"));
                        } else if (isSuccess(origStatus) && (newStatus == 401 || newStatus == 403)) {
                            // Verde: Seguro
                            c.setBackground(new Color(100, 255, 100, 100)); // Verde semi-transparente
                            c.setForeground(UIManager.getColor("Table.foreground"));
                        } else if (origStatus != newStatus) {
                            // Amarillo: Warning
                            c.setBackground(new Color(255, 255, 100, 100)); // Amarillo semi-transparente
                            c.setForeground(UIManager.getColor("Table.foreground"));
                        } else {
                            c.setBackground(table.getBackground());
                            c.setForeground(table.getForeground());
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
    // PANEL DE HEADERS (FIXED THEME)
    // ========================================
    private JPanel createHeadersPanel() {
        JPanel container = new JPanel(new BorderLayout());
        // FIX: Borde dinámico
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 1, 0, 0, UIManager.getColor("Component.borderColor")),
                new EmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel("Headers Configuration");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
        title.setBorder(new EmptyBorder(0,0,10,0));
        container.add(title, BorderLayout.NORTH);

        DefaultTableModel headersModel = new DefaultTableModel(new Object[]{"Action", "Header", "Value"}, 0);
        JTable headersTable = new JTable(headersModel);
        headersTable.setRowHeight(22);

        headersTable.getColumnModel().getColumn(0).setMaxWidth(100);
        headersTable.getColumnModel().getColumn(0).setPreferredWidth(90);

        container.add(new JScrollPane(headersTable), BorderLayout.CENTER);

        JPanel buttonsGrid = new JPanel(new GridLayout(0, 1, 5, 5));
        JButton btnAdd = new JButton("Add");
        JButton btnEdit = new JButton("Edit");
        JButton btnRemove = new JButton("Delete Rule");
        JButton btnPaste = new JButton("Paste");
        btnAdd.setPreferredSize(new Dimension(80, 25));
        buttonsGrid.add(btnAdd); buttonsGrid.add(btnEdit); buttonsGrid.add(btnRemove); buttonsGrid.add(btnPaste);

        JPanel flowWrapper = new JPanel(new BorderLayout()); flowWrapper.add(buttonsGrid, BorderLayout.NORTH);
        flowWrapper.setBorder(new EmptyBorder(0, 0, 0, 5));
        container.add(flowWrapper, BorderLayout.WEST);

        // Cargar reglas...
        for (String rule : IdorTesterExtension.getCustomHeaders()) {
            String[] parts = rule.split("\\|", 3);
            if (parts.length >= 2) {
                String action = parts[0];
                String name = parts[1];
                String val = parts.length > 2 ? parts[2] : "";
                if(!action.equals("REMOVE") && !action.equals("ADD")) { /* compatibility */ }
                headersModel.addRow(new Object[]{action, name, val});
            }
        }

        Runnable saveLogic = () -> {
            List<String> newHeaders = new ArrayList<>();
            for (int i = 0; i < headersModel.getRowCount(); i++) {
                String action = (String) headersModel.getValueAt(i, 0);
                String h = (String) headersModel.getValueAt(i, 1);
                String v = (String) headersModel.getValueAt(i, 2);
                if (h != null && !h.isEmpty()) {
                    newHeaders.add(action + "|" + h + "|" + (v == null ? "" : v));
                }
            }
            IdorTesterExtension.setCustomHeaders(newHeaders);
        };

        btnAdd.addActionListener(e -> showHeaderDialog(headersModel, -1, saveLogic));

        btnEdit.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) showHeaderDialog(headersModel, row, saveLogic);
        });

        btnRemove.addActionListener(e -> {
            int row = headersTable.getSelectedRow();
            if (row >= 0) { headersModel.removeRow(row); saveLogic.run(); }
        });

        btnPaste.addActionListener(e -> {
            try {
                String data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor).toString();
                if(data.contains(":")) {
                    String[] parts = data.split(":", 2);
                    headersModel.addRow(new Object[]{"ADD", parts[0].trim(), parts[1].trim()});
                    saveLogic.run();
                }
            } catch (Exception ex) {}
        });

        return container;
    }

    private void showHeaderDialog(DefaultTableModel model, int row, Runnable saveCallback) {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Header Rule", true);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JComboBox<String> actionBox = new JComboBox<>(new String[]{"ADD / REPLACE", "REMOVE"});
        JTextField nameField = new JTextField(20);
        JTextField valueField = new JTextField(20);

        if (row >= 0) {
            String act = (String) model.getValueAt(row, 0);
            actionBox.setSelectedItem(act.equals("REMOVE") ? "REMOVE" : "ADD / REPLACE");
            nameField.setText((String) model.getValueAt(row, 1));
            valueField.setText((String) model.getValueAt(row, 2));
            if (act.equals("REMOVE")) valueField.setEnabled(false);
        }

        actionBox.addActionListener(e -> {
            valueField.setEnabled(!actionBox.getSelectedItem().equals("REMOVE"));
            if (actionBox.getSelectedItem().equals("REMOVE")) valueField.setText("");
        });

        gbc.gridx=0; gbc.gridy=0; dialog.add(new JLabel("Action:"), gbc);
        gbc.gridx=1; dialog.add(actionBox, gbc);
        gbc.gridx=0; gbc.gridy=1; dialog.add(new JLabel("Header Name:"), gbc);
        gbc.gridx=1; dialog.add(nameField, gbc);
        gbc.gridx=0; gbc.gridy=2; dialog.add(new JLabel("Value:"), gbc);
        gbc.gridx=1; dialog.add(valueField, gbc);

        JPanel btnPanel = new JPanel();
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        btnPanel.add(okBtn); btnPanel.add(cancelBtn);

        gbc.gridx=0; gbc.gridy=3; gbc.gridwidth=2; dialog.add(btnPanel, gbc);

        okBtn.addActionListener(e -> {
            String action = actionBox.getSelectedItem().equals("REMOVE") ? "REMOVE" : "ADD";
            String name = nameField.getText().trim();
            String value = valueField.getText().trim();

            if (!name.isEmpty()) {
                if (row >= 0) {
                    model.setValueAt(action, row, 0);
                    model.setValueAt(name, row, 1);
                    model.setValueAt(value, row, 2);
                } else {
                    model.addRow(new Object[]{action, name, value});
                }
                saveCallback.run();
                dialog.dispose();
            }
        });

        cancelBtn.addActionListener(e -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ========================================
    // MÉTODOS AUXILIARES
    // ========================================
    private DefaultTableModel createRequestTableModel() {
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
        tableModel.setValueAt(length + " " + deltaStr, rowIndex, 5);
        tableModel.setValueAt(responseTimeMs + " ms", rowIndex, 6);
        tableModel.setValueAt(similarity + "%", rowIndex, 7);
        tableModel.fireTableRowsUpdated(rowIndex, rowIndex);
    }
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
        sidebarGroup.clearSelection(); drawerPanel.setVisible(false); drawerSplitPane.setDividerSize(0);
    }

    // ========================================
    // CLASE BOTÓN VERTICAL (REMASTERIZADA)
    // ========================================
    class VerticalToggleButton extends JToggleButton {
        public VerticalToggleButton(String text) {
            super(text);
            setFont(new Font("SansSerif", Font.PLAIN, 12));
            setFocusPainted(false);
            setContentAreaFilled(false);
            // Ajustamos el borde: Top/Bottom define el ancho visual, Left/Right el alto visual
            setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        }
        @Override public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); return new Dimension(d.height, d.width); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // FIX: Usar colores del UIManager para adaptarse al tema
            if (isSelected()) {
                // Color de fondo cuando está seleccionado (Usualmente igual que el Panel)
                g2.setColor(UIManager.getColor("Panel.background"));
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Indicador naranja (Este sí lo dejamos fijo por marca, o usamos Accent Color si Burp expone uno)
                g2.setColor(new Color(231, 111, 67));
                g2.fillRect(getWidth() - 3, 0, 3, getHeight());
            }

            AffineTransform original = g2.getTransform();
            g2.translate(getWidth() / 2.0, getHeight() / 2.0); g2.rotate(Math.toRadians(90));

            // FIX: Color de texto dinámico (blanco en Dark, negro en Light)
            g2.setColor(UIManager.getColor("Label.foreground"));

            FontMetrics fm = g2.getFontMetrics(); String text = getText();
            g2.drawString(text, -fm.stringWidth(text) / 2, fm.getAscent() / 2 - 2);
            g2.setTransform(original); g2.dispose();
        }
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