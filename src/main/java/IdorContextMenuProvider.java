import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ToolType;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.*;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class IdorContextMenuProvider implements ContextMenuItemsProvider {

    private final MontoyaApi api;

    public IdorContextMenuProvider(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent event) {
        // Mostramos el menú solo si viene de herramientas que tienen sentido
        if (event.isFromTool(ToolType.PROXY, ToolType.REPEATER, ToolType.TARGET, ToolType.LOGGER)) {
            List<Component> menuItems = new ArrayList<>();

            JMenuItem sendToIdor = new JMenuItem("Send to Bulk IDOR Tester");

            sendToIdor.addActionListener(e -> {
                List<HttpRequestResponse> selected = event.selectedRequestResponses();

                if ((selected == null || selected.isEmpty()) && event.messageEditorRequestResponse().isPresent()) {
                    selected = List.of(event.messageEditorRequestResponse().get().requestResponse());
                }

                for (HttpRequestResponse reqResp : selected) {
                    IdorTesterExtension.addRequest(reqResp);
                    api.logging().logToOutput("[IDOR Tester] Added: " + reqResp.request().url());
                    api.logging().logToOutput("[AGUS] Response:\n" + reqResp.response());
                }

                api.logging().logToOutput("[IDOR Tester] Total requests added: " + selected.size());
            });

            menuItems.add(sendToIdor);
            return menuItems;
        }

        return null;
    }
}
