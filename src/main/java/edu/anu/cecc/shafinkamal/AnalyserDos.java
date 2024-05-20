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
import java.util.ArrayList;
import java.io.FileWriter;

public class AnalyserDos implements Runnable, MqttCallback {

    private String broker;
    private String clientId;
    private MqttClient client;
    private int messageCount = 0;
    private int outOfOrderCount = 0;
    private int lastCounterValue = -1;
    private int testNumber = 0;

    private ArrayList<String[]> totalRateStatsArrayList = new ArrayList<>();

    public AnalyserDos(String broker, String clientId) {
        this.broker = broker;
        this.clientId = clientId;
        try {
            client = new MqttClient(broker, clientId, new MemoryPersistence());
            client.setCallback(this);
            client.connect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void connectComplete(boolean reconnect, String serverURI) {
        //System.out.println("Connected to: " + serverURI);
    }

    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        //System.out.println("disconnected: " + disconnectResponse.getReasonString());
    }

    public void deliveryComplete(IMqttToken token) {
        //System.out.println("deliveryComplete: " + token.isComplete());
    }

    public void mqttErrorOccurred(MqttException exception) {
        System.out.println("mqttErrorOccurred: " + exception.getMessage());
    }

    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        //System.out.println("authPacketArrived");
    }

    /*
     * This is for when a message is heard by the analyser on the
     * 'request/#' topic.
     */
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload());
        //System.out.println("Received message: " + payload + "on topic: " + topic);

        if (topic.startsWith("counter/")) {
            int counterValue = Integer.parseInt(payload);

            // updated how many messages is being seen in this topic.
            messageCount++;

            // Check if the counter value is out of order.
            if (counterValue != -1 && counterValue < lastCounterValue) {
                outOfOrderCount++;
            }

            // Update the last counter value.
            lastCounterValue = counterValue;
        }
    }

    public void run() {
        try {
            AnalyserDos analyserDos = new AnalyserDos(broker, clientId);

            int[] qosLevels = {0, 1, 2};
            int[] delayLevels = {0, 1, 2, 4};
            int[] instanceCountLevels = {1, 2, 3, 4, 5};

            runTests(analyserDos, qosLevels, delayLevels, instanceCountLevels);
            writeCSVFiles();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runTests(AnalyserDos analyserDos, int[] qosLevels, int[] delayLevels, int[] instanceCountLevels) {
        int[] subscriptionQoSLevels = {0, 1, 2};
        for (int subQoS : subscriptionQoSLevels) {
            for (int instanceCount : instanceCountLevels) {
                for (int qos : qosLevels) {
                    for (int delay : delayLevels) {
                        analyserDos.sendRequest(String.valueOf(qos), String.valueOf(delay), String.valueOf(instanceCount), subQoS);
                        try {
                            String topic = String.format("counter/#", instanceCount, qos, delay);
                            analyserDos.client.subscribe(topic, subQoS);
                            Thread.sleep(2000); // Ensure messages are being processed
                            analyserDos.measurePerformance(instanceCount, qos, delay, subQoS);
                            analyserDos.client.unsubscribe(topic);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void measurePerformance(int instanceCount, int qos, int delay, int subQoS) {
        try {
            //Thread.sleep(2000); // Ensure this sleep is at the right place.
            System.out.println("Messages received: " + messageCount);
            double rate = (double) messageCount / 2;
            totalRateStatsArrayList.add(new String[] {
                String.valueOf(instanceCount),
                String.valueOf(qos),
                String.valueOf(delay),
                String.valueOf(subQoS),
                String.valueOf(rate)
            });
            for (String[] stats : totalRateStatsArrayList) {
                System.out.println("Instance Count: " + stats[0]);
                System.out.println("QoS: " + stats[1]);
                System.out.println("Delay: " + stats[2]);
                System.out.println("Subscription QoS: " + stats[3]);
                System.out.println("Rate: " + stats[4]);
                System.out.println("-----------------------");
            }
            resetData();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void resetData() {
        messageCount = 0;
        outOfOrderCount = 0;
        lastCounterValue = -1;
    }

    public void writeCSVFiles() {
        // Write the average message data to a CSV file.
        try (FileWriter csvWriter = new FileWriter("rateStats.csv")) {
            for (String[] rowData : totalRateStatsArrayList) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }
    }

    private void sendRequest(String qos, String delay, String instanceCount, int subQoS) {
        try {
            // Generate the message to be published.
            MqttMessage qosMessage = new MqttMessage(qos.getBytes());
            MqttMessage delayMessage = new MqttMessage(delay.getBytes());
            MqttMessage instanceCountMessage = new MqttMessage(instanceCount.getBytes());

            client.publish("request/qos", qosMessage);
            client.publish("request/delay", delayMessage);
            client.publish("request/instancecount", instanceCountMessage);

            testNumber++;
            System.out.println("Test number: " + testNumber + " at subQoS: " + subQoS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
