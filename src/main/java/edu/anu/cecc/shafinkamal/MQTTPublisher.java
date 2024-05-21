//package edu.anu.cecc.shafinkamal;

import java.util.concurrent.CountDownLatch;

public class MQTTPublisher {

    public static void main(String[] args) {
        String broker = "tcp://localhost:1883";
        int subQos = 1;
        int pubQos = 1;

        int instanceCount = 5; // Number of publisher instances

        for (int i = 1; i <= instanceCount; i++) {
            String clientId = "pub-" + i;
            new Thread(new PublisherInstance(broker, clientId, subQos, pubQos, i)).start();
        }

        try {
            Thread.sleep(10000);
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Starting the analyser");
        new Thread(new Analyser(broker, "analyser")).start();
    }
}
