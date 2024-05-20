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
    private MqttClient client;
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
        }
    }

    private void handleSysMessage(String topic, String payload, long timestamp) {
        synchronized (sysMetrics) {
            String key = String.format("%d_%d_%d_%s", currentInstanceCount, currentQoS, currentDelay, topic);
            ArrayList<String[]> metricsList = sysMetrics.computeIfAbsent(key, k -> new ArrayList<>());
            metricsList.add(new String[]{String.valueOf(timestamp), payload});
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
        analyser.writeCSVFiles();
    } catch (Exception e) {
        e.printStackTrace();
    }
}
    

    
    private static void runTests(Analyser analyser, int[] qosLevels, int[] delayLevels, int[] instanceCountLevels) {
        int[] subscriptionQoSLevels = {0, 1, 2};
        try {
            analyser.client.subscribe("$SYS/#", 1);

            for (int subQoS : subscriptionQoSLevels) {
                for (int instanceCount : instanceCountLevels) {
                    for (int qos : qosLevels) {
                        for (int delay : delayLevels) {
                            analyser.sendRequest(String.valueOf(qos), String.valueOf(delay), String.valueOf(instanceCount), subQoS);
                            try {
                                String topic = String.format("counter/#", instanceCount, qos, delay);
                                analyser.client.subscribe(topic, subQoS);
    
                                Thread.sleep(60000);
                                analyser.measurePerformance(qos, delay, instanceCount, subQoS);
                                analyser.client.unsubscribe(topic);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            analyser.client.unsubscribe("$SYS/#");
        } catch (Exception e) {
           e.printStackTrace();
        }
        
    }
    

    private void measurePerformance(int qos, int delay, int instanceCount, int subQoS) {

        currentQoS = qos;
        currentDelay = delay;
        currentInstanceCount = instanceCount;
        // Average rate of messages received per second.
        double rate = (double) messageCount / 60;
        totalRateStatsArrayList.add(new String[] {
            String.valueOf(instanceCount),
            String.valueOf(qos),
            String.valueOf(delay),
            String.valueOf(subQoS),
            String.valueOf(rate)
        });
    
        
        String key = String.format("%d_%d_%d", instanceCount, qos, delay);
        int totalPublishedMessages = MessageCountManager.getInstance().getPublishedCount(key);
        int totalReceivedMessages = MessageCountManager.getInstance().getReceivedCount(key);
        //System.out.println("Total Published Messages: " + totalPublishedMessages);
        //System.out.println("analyser's key: " + key + " totalReceivedMessages: " + totalReceivedMessages);
    
        
        // Check if totalPublishedMessages is zero to avoid division by zero
        
            double messageLossRate = (1 - (double) messageCount / totalPublishedMessages) * 100;
            //System.out.println("Message loss rate: " + messageLossRate);
            //System.out.println("Message loss rate: " + messageLossRate);
            //System.out.println("Message loss rate: " + messageLossRate);
            messageLossStatsArrayList.add(new String[] {
                String.valueOf(instanceCount),
                String.valueOf(qos),
                String.valueOf(delay),
                String.valueOf(subQoS),
                String.valueOf(messageLossRate)
            });

            double outOfOrderRate = (double) outOfOrderCount / messageCount * 100;
            outOfOrderStatsArrayList.add(new String[] {
                String.valueOf(instanceCount),
                String.valueOf(qos),
                String.valueOf(delay),
                String.valueOf(subQoS),
                String.valueOf(outOfOrderRate)
            });

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
                //System.out.println("Publisher: " + entry.getKey() + ", Median Gap: " + medianGap);
            }

            if (publisherCount > 0) {
                long averageMedianGap = totalMedianGap / publisherCount;
                //System.out.println("Average Median Gap: " + averageMedianGap);
                medianMessageGapsArrayList.add(new String[] {
                    String.valueOf(instanceCount),
                    String.valueOf(qos),
                    String.valueOf(delay),
                    String.valueOf(subQoS),
                    String.valueOf(averageMedianGap)
                });
            }

            
        

        //System.out.println(messageCount + " messages received by the analyser before resetting data."); 
    
        // Reset the data for the next configuration.
        resetData();

        //System.out.println(messageCount + "messages received by the analyser after resetting data.");
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
        // Write the average message data to a CSV file.
        try (FileWriter csvWriter = new FileWriter("rateStats.csv")){
            for (String[] rowData : totalRateStatsArrayList) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            //System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }

        // Write the message loss to a CSV file.
        try (FileWriter csvWriter = new FileWriter("messageLossStats.csv")){
            for (String[] rowData : messageLossStatsArrayList) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }

        // Write the out of order stats to a CSV file.
        try (FileWriter csvWriter = new FileWriter("outOfOrderStats.csv")){
            for (String[] rowData : outOfOrderStatsArrayList) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }

        // Write the median message gaps to a CSV file.
        try (FileWriter csvWriter = new FileWriter("medianMessageGaps.csv")) {
            for (String[] rowData : medianMessageGapsArrayList) {
                csvWriter.append(String.join(",", rowData));
                csvWriter.append("\n");
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the CSV file.");
            e.printStackTrace();
        }
        // Write the $SYS/# metrics to a CSV file
        try (FileWriter csvWriter = new FileWriter("sysMetrics.csv")) {
            for (Map.Entry<String, ArrayList<String[]>> entry : sysMetrics.entrySet()) {
                String syskey = entry.getKey();
                for (String[] rowData : entry.getValue()) {
                    csvWriter.append(syskey).append(",");
                    csvWriter.append(String.join(",", rowData));
                    csvWriter.append("\n");
                }
            }
            csvWriter.flush();
        } catch (Exception e) {
            System.out.println("Something went wrong writing the sysMetrics CSV file.");
            e.printStackTrace();
        }
    }
}
