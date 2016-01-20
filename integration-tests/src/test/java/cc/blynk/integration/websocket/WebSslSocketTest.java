package cc.blynk.integration.websocket;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.websocket.WebSocketClient;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.websocket.WebSocketSSLServer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static cc.blynk.server.core.protocol.enums.Response.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 13.01.16.
 */
@RunWith(MockitoJUnitRunner.class)
public class WebSslSocketTest extends IntegrationBase {

    private static BaseServer webSocketServer;

    @Before
    public void init() throws Exception {
        if (webSocketServer == null) {
            properties.setProperty("data.folder", getProfileFolder());

            webSocketServer = new WebSocketSSLServer(holder).start();

            sleep(500);
        }
    }

    @Test
    public void testBasicWebSocketCommandsOk() throws Exception{
        WebSocketClient webSocketClient = new WebSocketClient("localhost", properties.getIntProperty("ssl.websocket.port"), true);
        webSocketClient.start();
        webSocketClient.send("login 4ae3851817194e2596cf1b7103603ef8");
        verify(webSocketClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(1, OK)));
        webSocketClient.send("ping");
        verify(webSocketClient.responseMock, timeout(500)).channelRead(any(), eq(new ResponseMessage(2, OK)));
        sleep(600);
    }

}
