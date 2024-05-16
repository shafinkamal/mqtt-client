package edu.anu.cecc.shafinkamal;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.IMqttDeliveryToken;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class Analyser implements MqttCallback {

    private String broker;
    private String clientId;
    private MqttClient client;

    public Analyser(String broker, String clientId) {
        this.broker = broker;
        this.clientId = clientId;
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            client.setCallback(this);
            client.connect();
            client.subscribe("counter/#", 1);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void connectComplete(boolean reconnect, String serverURI) {
        System.out.println("Connected to: " + serverURI);
    }

    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        System.out.println("disconnected: " + disconnectResponse.getReasonString());
    }

    public void deliveryComplete(IMqttToken token) {
        System.out.println("deliveryComplete: " + token.isComplete());
    }

    public void mqttErrorOccurred(MqttException exception) {
        System.out.println("mqttErrorOccurred: " + exception.getMessage());
    }

    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        System.out.println("authPacketArrived");
    }

    /*
     * This is for when a message is heard by the analyser on the
     * 'request/#' topic.
     * 
     * 
     */
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        System.out.println(payload);
    }

    public void sendRequest(String qos, String delay, String instanceCount) {
        try {
            MqttMessage qosToMessage = new MqttMessage(qos.getBytes());
            MqttMessage delayToMessage = new MqttMessage(qos.getBytes());
            MqttMessage instanceCountToMessage = new MqttMessage(qos.getBytes());
            client.publish("request/qos", qosToMessage);
            client.publish("request/delay", delayToMessage);
            client.publish("request/instancecount", instanceCountToMessage);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String broker = "tcp://localhost:1883";
        String clientId = "AnalyserClient";
        Analyser analyzer = new Analyser(broker, clientId);

        // Publish requests to the topics
        analyzer.sendRequest("0", "0", "5"); // Example request: qos=0, delay=0ms, instanceCount=5

        // Keep the program running to listen for messages
        try {
            Thread.sleep(60000); // Listen for messages for 60 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            analyzer.client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }




}
