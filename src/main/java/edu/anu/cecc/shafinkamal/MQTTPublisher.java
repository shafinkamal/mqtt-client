package edu.anu.cecc.shafinkamal;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class MQTTPublisher {
    
        public static void main(String[] args) {
            String broker = "tcp://localhost:1883";
            int subQos = 1;
            int pubQos = 1;
    
            for (int i = 1; i <= 5; i++) {
                String clientId = "pub-" + i;
                new Thread(new PublisherInstance(broker, clientId, subQos, pubQos, i)).start();
            }
        
    }
}
