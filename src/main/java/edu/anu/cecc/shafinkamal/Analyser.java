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
    private int messageCount = 0;
    private int outOfOrderCount = 0;
    private long lastReceivedTime = 0;
    private long totalGap = 0;
    private int validGaps = 0;
    private int lastCounterValue = -1;

    public Analyser(String broker, String clientId) {
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
        System.out.println("Connected to: " + serverURI);
    }

    public void disconnected(MqttDisconnectResponse disconnectResponse) {
        System.out.println("disconnected: " + disconnectResponse.getReasonString());
    }

    public void deliveryComplete(IMqttToken token) {
        //System.out.println("deliveryComplete: " + token.isComplete());
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
        System.out.println("Received message: " + payload + "on topic: " + topic);

        if (topic.startsWith("counter/")) {
            int counterValue = Integer.parseInt(payload);

            messageCount++;

            if (lastCounterValue != -1 && counterValue < lastCounterValue) {
                outOfOrderCount++;
            }
            lastCounterValue = counterValue;

            long currentTime = System.currentTimeMillis();
            if (lastReceivedTime != 0) {
                long gap = currentTime - lastReceivedTime;
                totalGap += gap;
                validGaps++;
            }
            lastReceivedTime = currentTime;
        }
    }

    public void analyseData(int qos, int delay, int instanceCount) {
        //System.out.printf("QoS: %d, Delay: %d ms, Instances: %d%n", qos, delay, instanceCount);
        //System.out.printf("Total Messages: %d%n", messageCount);
        //System.out.printf("Out-of-Order Messages: %d%n", outOfOrderCount);
        //System.out.printf("Average Gap: %.2f ms%n", validGaps > 0 ? (double) totalGap / validGaps : 0);
        //System.out.println();
    }
    
    public void resetData() {
        messageCount = 0;
        outOfOrderCount = 0;
        lastReceivedTime = 0;
        totalGap = 0;
        validGaps = 0;
        lastCounterValue = -1;
    }


    

    private static void runAnalyser(String broker, String clientId) {
        Analyser analyser = new Analyser(broker, clientId);

        // At this point, an analyser has been connected to a specified
        // MQTT broker. 

        int[] qosLevels             = {0,1,2};
        int[] delayLevels           = {0,1,2,4};
        int[] instanceCountLevels   = {1,2,3,4,5};

        runTests(analyser, qosLevels, delayLevels, instanceCountLevels, 0);

        
        
        
    }

    private static void runTests(Analyser analyser, int[] qosLevels, int[] delayLevels, int[] instanceCountLevels,
            int recursiveIndex) {

                int[] subscriptionQoSLevels = {0,1,2};


                // Base Case
                if (recursiveIndex >= (qosLevels.length * delayLevels.length * instanceCountLevels.length)) {
                    return;
                }

                for (int instanceCount : instanceCountLevels) {
                    for (int qos : qosLevels) {
                        for (int delay : delayLevels) {
                            analyser.sendRequest(String.valueOf(qos), String.valueOf(delay), String.valueOf(instanceCount));

                            for (int subQos : subscriptionQoSLevels) {
                                try {
                                    analyser.client.subscribe("counter/#", subQos);
                                    Thread.sleep(5000); // 5 seconds
                                    analyser.analyseData(qos, delay, instanceCount);
                                    analyser.resetData();
                                    analyser.client.unsubscribe("counter/#");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

                // Once the request has been published, the analyser should wait 60 seconds
                // as it should now listen to the counter topic and take measure
            

                // Recursive case
                runTests(analyser, qosLevels, delayLevels, instanceCountLevels, recursiveIndex+1);

    }

    /*
     * This method is what allows the analyser to publish to new
     * qos, delay and instance count values to the request/[x] topics.
     */
    private void sendRequest(String qos, String delay, String instanceCount) {
        try {
            // Generate the message to be published.
            MqttMessage qosMessage              = new MqttMessage(qos.getBytes());
            MqttMessage delayMessage            = new MqttMessage(delay.getBytes());
            MqttMessage instanceCountMessage    = new MqttMessage(instanceCount.getBytes());

            client.publish("request/qos", qosMessage);
            client.publish("request/delay", delayMessage);
            client.publish("request/instancecount", instanceCountMessage);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String broker = "tcp://localhost:1883";
        String clientId = "AnalyserClient";

        runAnalyser(broker, clientId);
    }


}
