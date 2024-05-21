package edu.anu.cecc.shafinkamal;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.io.FileWriter;
import java.util.concurrent.CountDownLatch;

public class PublisherInstance implements Runnable {

    private String broker;
    private String clientId;
    private int subQos;
    private int pubQos;
    private int instanceId;
    private MqttClient client;
    static int requestedQoS;
    static int requestedDelay;
    static int requestedInstanceCount;
    private boolean newConfigurationReceived = false;

    public PublisherInstance(String broker, String clientId, int subQos, int pubQos, int instanceId) {
        this.broker = broker;
        this.clientId = clientId;
        this.subQos = subQos;
        this.pubQos = pubQos;
        this.instanceId = instanceId;
    }

    @Override
    public void run() {
        try {
            client = new MqttClient(broker, clientId);
            MqttConnectionOptions options = new MqttConnectionOptions();

            client.setCallback(new MqttCallback() {
                public void connectComplete(boolean reconnect, String serverURI) {
                    System.out.println(clientId + " connected to: " + serverURI);
                }

                public void disconnected(MqttDisconnectResponse disconnectResponse) {
                    System.out.println(clientId + " disconnected: " + disconnectResponse.getReasonString());
                }

                public void deliveryComplete(IMqttToken token) {
                    //System.out.println(clientId + " deliveryComplete: " + token.isComplete());
                }

                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    if (topic.equals("request/qos")) {
                        requestedQoS = Integer.parseInt(new String(message.getPayload()));
                    } else if (topic.equals("request/delay")) {
                        requestedDelay = Integer.parseInt(new String(message.getPayload()));
                    } else if (topic.equals("request/instancecount")) {
                        requestedInstanceCount = Integer.parseInt(new String(message.getPayload()));
                    }
                    newConfigurationReceived = true;
                }

                public void mqttErrorOccurred(MqttException exception) {
                    System.out.println(clientId + " mqttErrorOccurred: " + exception.getMessage());
                }

                public void authPacketArrived(int reasonCode, MqttProperties properties) {
                    System.out.println(clientId + " authPacketArrived");
                }
            });

            client.connect(options);

            client.subscribe("request/qos", subQos);
            client.subscribe("request/delay", subQos);
            client.subscribe("request/instancecount", subQos);

            while (true) {
                if (newConfigurationReceived && instanceId <= requestedInstanceCount) {
                    newConfigurationReceived = false;
                    publishMessages();
                } else {
                    Thread.sleep(59000);
                }
            }

        } catch (MqttException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void publishMessages() throws MqttException, InterruptedException {
        int counter = 0;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 59000) {
            String topic = String.format("counter/%d/%d/%d", instanceId, requestedQoS, requestedDelay);
            MqttMessage message = new MqttMessage(String.valueOf(counter).getBytes());
            message.setQos(requestedQoS);
            client.publish(topic, message);
            counter++;
            Thread.sleep(requestedDelay);
        }
        String key = String.format("%d_%d_%d", requestedInstanceCount, requestedQoS, requestedDelay);
        MessageCountManager.getInstance().incrementPublishedCount(key, counter);
        //System.out.println("publisher's key: " + key + " counter: " + counter);
        //System.out.println(instanceId + "published " + counter + " messages.");
    }
}
