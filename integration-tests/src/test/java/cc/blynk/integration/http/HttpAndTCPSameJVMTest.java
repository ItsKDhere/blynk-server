package cc.blynk.integration.http;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.server.api.http.HttpAPIServer;
import cc.blynk.server.application.AppServer;
import cc.blynk.server.core.BaseServer;
import cc.blynk.server.hardware.HardwareServer;
import cc.blynk.utils.JsonParser;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.IOException;
import java.util.List;

import static cc.blynk.server.core.protocol.enums.Command.*;
import static cc.blynk.server.core.protocol.model.messages.MessageFactory.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 07.01.16.
 */
@RunWith(MockitoJUnitRunner.class)
public class HttpAndTCPSameJVMTest extends IntegrationBase {

    private static BaseServer httpServer;
    private static BaseServer hardwareServer;
    private static BaseServer appServer;

    private static CloseableHttpClient httpclient;
    private static String httpsServerUrl;

    @AfterClass
    public static void shutdown() throws Exception {
        httpclient.close();
        httpServer.stop();
        hardwareServer.stop();
        appServer.stop();
    }

    @Before
    public void init() throws Exception {
        if (httpServer == null) {
            properties.setProperty("data.folder", getProfileFolder());

            httpServer = new HttpAPIServer(holder).start();
            hardwareServer = new HardwareServer(holder).start();
            appServer = new AppServer(holder).start();

            sleep(500);

            int httpPort = holder.props.getIntProperty("http.port");

            httpsServerUrl = "http://localhost:" + httpPort + "/";

            httpclient = HttpClients.createDefault();
        }
    }

    @Test
    public void testChangePinValueViaAppAndHardware() throws Exception {
        ClientPair clientPair = initAppAndHardPair();

        clientPair.hardwareClient.send("hardware vw 4 200");
        verify(clientPair.appClient.responseMock, timeout(500)).channelRead(any(), eq(produce(1, HARDWARE, "1 vw 4 200".replaceAll(" ", "\0"))));

        reset(clientPair.appClient.responseMock);

        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpGet request = new HttpGet(httpsServerUrl + token + "/pin/v4");

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            List<String> values = consumeJsonPinValues(response);
            assertEquals(1, values.size());
            assertEquals("200", values.get(0));
        }

        clientPair.appClient.send("hardware 1 vw 4 201");
        verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(2, HARDWARE, "vw 4 201".replaceAll(" ", "\0"))));

        try (CloseableHttpResponse response = httpclient.execute(request)) {
            assertEquals(200, response.getStatusLine().getStatusCode());
            List<String> values = consumeJsonPinValues(response);
            assertEquals(1, values.size());
            assertEquals("201", values.get(0));
        }
    }

    @Test
    public void testChangePinValueViaHttpAPI() throws Exception {
        ClientPair clientPair = initAppAndHardPair();
        clientPair.appClient.send("getToken 1");
        String token = clientPair.appClient.getBody();

        HttpPut request = new HttpPut(httpsServerUrl + token + "/pin/v4");
        HttpGet getRequest = new HttpGet(httpsServerUrl + token + "/pin/v4");

        for (int i = 0; i < 100; i++) {
            request.setEntity(new StringEntity("[\"" + i + "\"]", ContentType.APPLICATION_JSON));
            try (CloseableHttpResponse response = httpclient.execute(request)) {
                assertEquals(200, response.getStatusLine().getStatusCode());
            }

            clientPair.hardwareClient.send("hardsync " + "vr 4".replaceAll(" ", "\0"));
            verify(clientPair.hardwareClient.responseMock, timeout(500)).channelRead(any(), eq(produce(i + 1, HARDWARE, ("vw 4 " + i).replaceAll(" ", "\0"))));

            try (CloseableHttpResponse response = httpclient.execute(getRequest)) {
                assertEquals(200, response.getStatusLine().getStatusCode());
                List<String> values = consumeJsonPinValues(response);
                assertEquals(1, values.size());
                assertEquals(i, Integer.valueOf(values.get(0)).intValue());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> consumeJsonPinValues(CloseableHttpResponse response) throws IOException {
        return JsonParser.readAny(consumeText(response), List.class);
    }

    @SuppressWarnings("unchecked")
    private String consumeText(CloseableHttpResponse response) throws IOException {
        return EntityUtils.toString(response.getEntity());
    }

}
