import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
import java.util.concurrent.*; 

import org.joda.time.Duration; 
import org.joda.time.DateTime;  
// ** this entire class is only necessary with this system. in regular DeviceHive the equivalent would be a DeviceNotificationsCallback passed to the subscribeNotifications() function ** 
public class Main
{
    private static final int[] ports = {6767,6768,6769};   
    private static final int[] speedlimits = {60,100,120};
    private static final String[] deviceIds = {"TrafficSensor1", "TrafficSensor2", "TrafficSensor3"};  
    public static void main(String[] args) throws Exception
    {
        Main main = new Main(); 
        main.init(); 

    }

    private void init() throws Exception
    {
        ExecutorService e = Executors.newFixedThreadPool(deviceIds.length); 
        for(int i = 0; i < deviceIds.length; i++)
        {
            Processor p = new Processor(i); 
            int port = ports[i];
            Runnable t = new Runnable()
            {
                @Override
                public void run()
                { 
                    try
                    {
                        p.init(port); 
                    } 
                    catch (Exception e) 
                    {
                        //TODO: handle exception
                    }
                    return;
                }
            };
            e.execute(t);
        }

    }

    private class Processor extends SecureProcessor
    { 
        private int index; 
        public Processor(int index)
        {
            super();
            this.index = index; 
        }
        @Override
        public void process(DeviceNotificationWrapper notification) throws Exception
        {
            JsonObject parameters = getDecryptedParameters(notification);
            int speedDiff = parameters.get("speed").getAsInt() - speedlimits[index];
            boolean speeding =  speedDiff > 0; 
            if(speeding)
            {
                List<Parameter> response = new ArrayList<>(); 
                response.add(new Parameter("license-plate", parameters.get("license-plate").getAsString())); 
                response.add(new Parameter("speeding", Boolean.toString(speeding)));
                response.add(new Parameter("time", parameters.get("time").getAsString())); 
                response.add(new Parameter("speed-difference", String.valueOf(speedDiff)));  
                sendCommand(encryptedCommand("response", response));
            }
        }
    }
}
