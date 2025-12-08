import burp.api.montoya.MontoyaApi;
import burp.api.montoya.BurpExtension;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

public class IdorTesterExtension implements BurpExtension {

    private static MontoyaApi api;
    private static List<RequestEntry> requestList = new ArrayList<>();
    private static IdorTesterPanel panel;
    // Ahora guardaremos las reglas en formato interno: "ACTION|HEADER|VALUE"
    private static List<String> customHeaders = new ArrayList<>();

    @Override
    public void initialize(MontoyaApi api) {
        IdorTesterExtension.api = api;
        api.extension().setName("Bulk IDOR Tester");

        panel = new IdorTesterPanel(api, requestList);
        api.userInterface().registerSuiteTab("Bulk IDOR Tester", panel);
        api.userInterface().registerContextMenuItemsProvider(new IdorContextMenuProvider(api));
    }

    public static void addRequest(HttpRequestResponse reqResp) {
        RequestEntry entry = new RequestEntry(reqResp.request());
        entry.setOriginalResponse(reqResp.response());
        requestList.add(entry);

        SwingUtilities.invokeLater(() -> {
            if (panel != null) panel.addRequest(entry);
        });
    }

    public static List<String> getCustomHeaders() { return customHeaders; }
    public static void setCustomHeaders(List<String> headers) { customHeaders = headers; }

    public static void startAttack() {
        // Limpiar estado previo
        for (RequestEntry entry : requestList) {
            entry.setEditedRequest(null);
            entry.setResponse(null);
            entry.setSimilarity(0);
        }

        new Thread(() -> {
            for (int i = 0; i < requestList.size(); i++) {
                RequestEntry entry = requestList.get(i);
                HttpRequest originalReq = entry.getRequest();
                HttpRequest editedReq = originalReq;

                // --- NUEVA LÓGICA DE REGLAS ---
                for (String rule : customHeaders) {
                    // Formato esperado: ACTION|NAME|VALUE
                    String[] parts = rule.split("\\|", 3);
                    if (parts.length >= 2) {
                        String action = parts[0];
                        String name = parts[1];
                        String value = parts.length > 2 ? parts[2] : "";

                        if ("REMOVE".equals(action)) {
                            // Acción de borrado
                            if (editedReq.headerValue(name) != null) {
                                editedReq = editedReq.withRemovedHeader(name);
                            }
                        } else {
                            // Acción de Agregar/Reemplazar (Default)
                            if (editedReq.headerValue(name) != null) {
                                editedReq = editedReq.withUpdatedHeader(name, value);
                            } else {
                                editedReq = editedReq.withAddedHeader(name, value);
                            }
                        }
                    }
                }
                // -----------------------------
                
                entry.setEditedRequest(editedReq);

                // Enviar petición
                long start = System.currentTimeMillis();
                var responseResult = api.http().sendRequest(editedReq);
                long duration = System.currentTimeMillis() - start;

                var response = responseResult.response();
                entry.setResponse(response);

                // Calcular Similitud
                int similarity = 0;
                if (entry.getOriginalResponse() != null && response != null) {
                    String body1 = entry.getOriginalResponse().bodyToString();
                    String body2 = response.bodyToString();
                    similarity = calculateSimilarity(body1, body2);
                }
                entry.setSimilarity(similarity);

                // Actualizar UI
                int finalI = i;
                int finalSim = similarity;
                SwingUtilities.invokeLater(() -> {
                    if (panel != null) {
                        panel.updateTableRow(finalI, response.statusCode(), response.body().length(), duration, finalSim);
                        panel.refreshEditorIfSelected(finalI);
                    }
                });
            }
        }).start();
    }

    private static int calculateSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0;
        if (s1.equals(s2)) return 100;
        if (s1.isEmpty() || s2.isEmpty()) return 0;

        if (s1.length() > 5000) s1 = s1.substring(0, 5000);
        if (s2.length() > 5000) s2 = s2.substring(0, 5000);

        int distance = levenshtein(s1, s2);
        int maxLength = Math.max(s1.length(), s2.length());

        return (int) ((1.0 - (double) distance / maxLength) * 100);
    }

    private static int levenshtein(String s1, String s2) {
        int[] costs = new int[s2.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= s1.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= s2.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), s1.charAt(i - 1) == s2.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[s2.length()];
    }
}