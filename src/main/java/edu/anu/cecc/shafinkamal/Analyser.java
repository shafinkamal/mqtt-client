//package edu.anu.cecc.shafinkamal;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.MqttToken;
import org.eclipse.paho.mqttv5.client.IMqttDeliveryToken;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import java.util.ArrayList;
import java.io.FileWriter;
import java.io.BufferedReader; // Add this line to import the BufferedReader class
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.CountDownLatch; // Add this line to import the CountDownLatch class
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

public class Analyser implements MqttCallback, Runnable {

    private String broker;
    private String clientId;
    private MqttAsyncClient client;
    private int messageCount = 0;
    private int outOfOrderCount = 0;
    private long lastReceivedTime = 0;
    private long totalGap = 0;
    private int validGaps = 0;
    private int lastCounterValue = -1;
    private int testNumber = 0;

    private ArrayList<String[]> totalRateStatsArrayList = new ArrayList<>();    
    private ArrayList<String[]> messageLossStatsArrayList = new ArrayList<>();
    private ArrayList<String[]> outOfOrderStatsArrayList = new ArrayList<>();
    private Map<String, Integer> lastCounterValues = new HashMap<>(); // To track last counter values for each publisher
    private Map<String, Long> lastTimestamps = new HashMap<>(); // To track last timestamps for each publisher
    private Map<String, ArrayList<Long>> gaps = new HashMap<>(); // To track gaps for each publisher
    private ArrayList<String[]> medianMessageGapsArrayList = new ArrayList<>();
    private Map<String, ArrayList<String[]>> sysMetrics = new HashMap<>();
    private int currentQoS;
    private int currentDelay;
    private int currentInstanceCount;
    private int sysMetricSubQos;
    private ArrayList<String[]> aggregatedMetrics = new ArrayList<>();





    public Analyser(String broker, String clientId) {
        this.broker = broker;
        this.clientId = clientId;
        try {
            client = new MqttAsyncClient(broker, clientId, new MemoryPersistence());
            client.setCallback(this);
            IMqttToken token = client.connect();
            token.waitForCompletion();
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
        //System.out.println("mqttErrorOccurred: " + exception.getMessage());
    }

    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        //System.out.println("authPacketArrived");
    }

    /*
     * This is for when a message is heard by the analyser on the
     * 'request/#' topic.
     * 
     * 
     */
    public void messageArrived(String topic, MqttMessage message) {
        long currentTimeMillis = System.currentTimeMillis();
        String payload = new String(message.getPayload());
        //System.out.println("Received message: " + payload + "on topic: " + topic);

        if (topic.startsWith("counter/")) {
            int counterValue = Integer.parseInt(payload);

            // updated how many messages is being seen in this topic.
            messageCount++;
            MessageCountManager.getInstance().incrementReceivedCount(topic);            

            String[] topicParts = topic.split("/");
            String publisherID = topicParts[1] + "_" + topicParts[2] + "_" + topicParts[3];

            // Calculate the gap between the last message and the current message from the same publisher.
            synchronized (gaps) {
                if (lastTimestamps.containsKey(publisherID)) {
                    long gap = currentTimeMillis - lastTimestamps.get(publisherID);
                    if (counterValue == lastCounterValues.get(publisherID) + 1) {
                        gaps.computeIfAbsent(publisherID, k -> new ArrayList<>()).add(gap);
                    }
                }
                lastTimestamps.put(publisherID, currentTimeMillis);
            }

            if (lastCounterValues.containsKey(publisherID)) {
                if (counterValue < lastCounterValues.get(publisherID)) {
                    outOfOrderCount++;
                }
            }
            lastCounterValues.put(publisherID, counterValue);

            // Update the last counter value.
            lastCounterValue = counterValue;



        } else if (topic.startsWith("$SYS/")) {
            handleSysMessage(topic, payload, currentTimeMillis);
            //System.out.println(String.format("Received sys metric: %s %s", topic, payload));
        }
    }

    private void handleSysMessage(String topic, String payload, long timestamp) {
        synchronized (sysMetrics) {
            String key = String.format("%d_%d_%d_%s", currentInstanceCount, currentQoS, currentDelay, topic);
            //System.out.println("[HANDLING] sysMetrics key: " + key);
            ArrayList<String[]> metricsList = sysMetrics.computeIfAbsent(key, k -> new ArrayList<>());
            metricsList.add(new String[]{String.valueOf(timestamp), payload});
            //System.out.println(String.format("handled sys metric: %s %s", topic, payload));
        }
    }

    private void collectSysMetrics() {
        try {
            // Subscribe to $SYS/# topic to collect the metrics
            client.subscribe(new String[]{
                "$SYS/broker/load/messages/received/1min",
                "$SYS/broker/load/messages/sent/1min",
                "$SYS/broker/clients/connected",
                "$SYS/broker/load/publish/dropped/1min"
            }, new int[]{1, 1, 1, 1});

            // Wait for a short period to collect the metrics
            Thread.sleep(5000); // Adjust the sleep time as needed

            // Unsubscribe from the $SYS/# topic after collecting the metrics
            client.unsubscribe(new String[]{
                "$SYS/broker/load/messages/received/1min",
                "$SYS/broker/load/messages/sent/1min",
                "$SYS/broker/clients/connected",
                "$SYS/broker/load/publish/dropped/1min"
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void resetData() {
        messageCount = 0;
        outOfOrderCount = 0;
        lastReceivedTime = 0;
        totalGap = 0;
        validGaps = 0;
        lastCounterValue = -1;
    }


    
@Override
public void run() {

    try {
        Analyser analyser = new Analyser(broker, clientId);

        // At this point, an analyser has been connected to a specified MQTT broker. 
        int[] qosLevels = {0, 1, 2};
        int[] delayLevels = {0, 1, 2, 4};
        int[] instanceCountLevels = {1, 2, 3, 4, 5};
    
        runTests(analyser, qosLevels, delayLevels, instanceCountLevels);
        System.out.println("Tests run successfully.");
        analyser.writeCSVFiles();
        System.out.println("CSV files written successfully.");
    } catch (Exception e) {
        e.printStackTrace();
    }
}
    

    
    private static void runTests(Analyser analyser, int[] qosLevels, int[] delayLevels, int[] instanceCountLevels) {
        int[] subscriptionQoSLevels = {0, 1, 2};
        for (int subQoS : subscriptionQoSLevels) {
            MessageCountManager.getInstance().resetCounts();
            analyser.lastTimestamps.clear();
            analyser.gaps.clear();
            System.out.println("Subscribing at QoS " + subQoS);
            for (int instanceCount : instanceCountLevels) {
                for (int qos : qosLevels) {
                    for (int delay : delayLevels) {
                        if (!analyser.client.isConnected()) {
                            try {
                                IMqttToken token = analyser.client.connect();
                                token.waitForCompletion();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        analyser.sendRequest(String.valueOf(qos), String.valueOf(delay), String.valueOf(instanceCount), subQoS);
                        try {
                            String topic = String.format("counter/#", instanceCount, qos, delay);
                            analyser.client.subscribe(topic, subQoS);
                            Thread.sleep(5000);
                            //System.out.println("[ANALYSER] For " + String.format("counter/%d/%d/%d", instanceCount, qos, delay) + ": message count = " + MessageCountManager.getInstance().getReceivedCount(String.format("counter/%d/%d/%d", instanceCount, qos, delay)));
                            //System.out.println("[ANALYSER] Message count for topic " + String.format("counter/%d/%d/%d ", instanceCount, qos, delay) + analyser.messageCount);
                            analyser.measurePerformance(qos, delay, instanceCount, subQoS);
                            analyser.sysMetricSubQos = subQoS;
                            analyser.collectSysMetrics();
                            analyser.client.unsubscribe(topic);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    

    private void measurePerformance(int qos, int delay, int instanceCount, int subQoS) {

        currentQoS = qos;
        currentDelay = delay;
        currentInstanceCount = instanceCount;

        // Average rate of messages received per second.
        double rate = (double) messageCount / 5;
        String key = String.format("%d_%d_%d", instanceCount, qos, delay);
        int totalPublishedMessages = MessageCountManager.getInstance().getPublishedCount(key);
        int totalReceivedMessages = MessageCountManager.getInstance().getReceivedCount(key);

        // Check if totalPublishedMessages is zero to avoid division by zero
        double messageLossRate = (1 - (double) messageCount / totalPublishedMessages) * 100;
        double outOfOrderRate = (double) outOfOrderCount / messageCount * 100;

        // Make a copy of the gaps map to avoid ConcurrentModificationException
        Map<String, ArrayList<Long>> gapsCopy;
        synchronized (gaps) {
            gapsCopy = new HashMap<>(gaps);
        }

        long totalMedianGap = 0;
        int publisherCount = 0;

        for (Map.Entry<String, ArrayList<Long>> entry : gapsCopy.entrySet()) {
            ArrayList<Long> gapList = new ArrayList<>(entry.getValue());
            Collections.sort(gapList);
            long medianGap = gapList.get(gapList.size() / 2);
            totalMedianGap += medianGap;
            publisherCount++;
        }

        long averageMedianGap = publisherCount > 0 ? totalMedianGap / publisherCount : 0;

        // Collect the system metrics
        String[] sysMetricsValues = new String[4];
        String[] sysMetricTopics = {
            "$SYS/broker/load/messages/received/1min",
            "$SYS/broker/load/messages/sent/1min",
            "$SYS/broker/clients/connected",
            "$SYS/broker/load/publish/dropped/1min"
        };
        for (int i = 0; i < sysMetricTopics.length; i++) {
            String sysMetricKey = String.format("%d_%d_%d_%s", instanceCount, qos, delay, sysMetricTopics[i]);
            //System.out.println("[MEASURE] sysMetrics key: " + sysMetricKey);
            ArrayList<String[]> metricsList = sysMetrics.get(sysMetricKey);
            //System.out.println("sysMetrics: " + metricsList);
            if (metricsList != null && !metricsList.isEmpty()) {
                sysMetricsValues[i] = metricsList.get(metricsList.size() - 1)[1]; // Get the latest value
            } else {
                sysMetricsValues[i] = "0";
            }
        }

        // Add the aggregated data to the list
        aggregatedMetrics.add(new String[]{
            String.valueOf(instanceCount),
            String.valueOf(qos),
            String.valueOf(delay),
            String.valueOf(subQoS),
            String.valueOf(rate),
            String.valueOf(messageLossRate),
            String.valueOf(outOfOrderRate),
            String.valueOf(averageMedianGap),
            sysMetricsValues[0],
            sysMetricsValues[1],
            sysMetricsValues[2],
            sysMetricsValues[3]
        });

        // Reset the data for the next configuration
        resetData();
    }


    
    
    
    

    /*
     * This method is what allows the analyser to publish to new
     * qos, delay and instance count values to the request/[x] topics.
     */
    private void sendRequest(String qos, String delay, String instanceCount, int subQoS) {
        try {
            // Generate the message to be published.
            MqttMessage qosMessage              = new MqttMessage(qos.getBytes());
            MqttMessage delayMessage            = new MqttMessage(delay.getBytes());
            MqttMessage instanceCountMessage    = new MqttMessage(instanceCount.getBytes());

            client.publish("request/qos", qosMessage);
            client.publish("request/delay", delayMessage);
            client.publish("request/instancecount", instanceCountMessage);

            testNumber++;
            //System.out.println("Test number: " + testNumber + " at subQoS: " + subQoS);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void writeCSVFiles() {
        // Write all metrics to a single CSV file
        try (FileWriter csvWriter = new FileWriter("allMetrics.csv")) {
            // Write the header
            csvWriter.append("InstanceCount, QoS, Delay, Subscription QoS, Rate of messages, Message Loss, Out of order messages, median inter message gaps, $SYS/broker/load/messages/received/1min, $SYS/broker/load/messages/sent/1min, $SYS/broker/clients/connected, $SYS/broker/load/publish/dropped/1min\n");

            // Write the data
            for (String[] rowData : aggregatedMetrics) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }
    }
}
