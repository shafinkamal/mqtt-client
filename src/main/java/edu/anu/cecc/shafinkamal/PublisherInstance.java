package edu.anu.cecc.shafinkamal;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

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


/*
 * Publisher instance constructor
 */
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
                    //System.out.println("MESSAGE ARRIVED!");

                    if (topic.equals("request/qos")) {
                        requestedQoS = Integer.parseInt(new String(message.getPayload()));
                        System.out.println("requestedQoS: " + requestedQoS);
                    } else if (topic.equals("request/delay")) {
                        requestedDelay = Integer.parseInt(new String(message.getPayload()));
                        System.out.println("requestedDelay: " + requestedDelay);
                    } else if (topic.equals("request/instancecount")) {
                        requestedInstanceCount = Integer.parseInt(new String(message.getPayload()));
                        System.out.println("requestedInstanceCount: " + requestedInstanceCount);
                    }
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
                //System.out.println("while (true)");
                //System.out.println("instanceID: " + instanceId);
                //System.out.println("requestedInstanceCount: " + requestedInstanceCount);
                if (instanceId <= requestedInstanceCount) {
                    //System.out.println("if (instanceId <= requestedInstanceCount)");
                    publishMessages();
                } else {
                    // Stay quiet if this instance is not supposed to be active
                    Thread.sleep(5000);
                }
            }

        } catch (MqttException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void publishMessages() throws MqttException, InterruptedException {
        int counter = 0;
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < 5000) { // Publish for 60 seconds
            String topic = String.format("counter/%d/%d/%d", instanceId, requestedQoS, requestedDelay);
            //System.out.println(topic);
            MqttMessage message = new MqttMessage(String.valueOf(counter).getBytes());
            message.setQos(requestedQoS);
            client.publish(topic, message);
            //System.out.println("Message published: " + counter + " on topic: " + topic);
            counter++;
            Thread.sleep(requestedDelay);
            //System.out.println("did the publisher wake up?");
        }
    }



}
