package edu.anu.cecc.shafinkamal;
import java.util.HashMap;
import java.util.Map;

public class MessageCountManager {
    private static MessageCountManager instance;
    private Map<String, Integer> publishedCountMap = new HashMap<>();
    private Map<String, Integer> receivedCountMap = new HashMap<>();

    private MessageCountManager() {}

    public static synchronized MessageCountManager getInstance() {
        if (instance == null) {
            instance = new MessageCountManager();
        }
        return instance;
    }

    public synchronized void incrementPublishedCount(String key, int count) {
        publishedCountMap.put(key, publishedCountMap.getOrDefault(key, 0) + count);
    }

    public synchronized void incrementReceivedCount(String key) {
        receivedCountMap.put(key, receivedCountMap.getOrDefault(key, 0) + 1);
    }

    public synchronized int getPublishedCount(String key) {
        return publishedCountMap.getOrDefault(key, 0);
    }

    public synchronized int getReceivedCount(String key) {
        return receivedCountMap.getOrDefault(key, 0);
    }

    public synchronized void resetCounts() {
        publishedCountMap.clear();
        receivedCountMap.clear();
    }
}
