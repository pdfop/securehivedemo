import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
import java.util.concurrent.*; 
import java.nio.channels.*; 
import org.joda.time.Duration; 
import org.joda.time.DateTime;  
// ** this entire class is only necessary with this system. in regular DeviceHive the equivalent would be a DeviceNotificationsCallback passed to the subscribeNotifications() function ** 
public class Main
{
    private static final int port = 6767;   
    public static void main(String[] args) throws Exception
    {
        Main main = new Main(); 
        main.init(); 

    }

    private void init() throws Exception
    {
        MyThreadedProcessor p = new MyThreadedProcessor(60); 
        p.init(port); 

    }

    // **overriding the process method of the SecureProcessor class replaces the onSuccess function in a regular DeviceNotificationsCallback** 
    private class MyThreadedProcessor extends ThreadedSecureProcessor
    { 
        private int speedlimit; 

        public MyThreadedProcessor(int speedlimit)
        {
            super();
            this.speedlimit = speedlimit; 
        }

        @Override
        protected void startThread(SocketChannel in, SocketChannel out)
        {
            TrafficProcessor p = new TrafficProcessor(in,out); 
            p.start(); 
        }

        protected class TrafficProcessor extends ThreadedSecureProcessor.Processor
        {
            public TrafficProcessor(SocketChannel in, SocketChannel out)
            {
                super(in,out); 
            }
            @Override
            public void process(DeviceNotificationWrapper notification) throws Exception
            {
                JsonObject parameters = getDecryptedParameters(notification);
                int speedDiff = parameters.get("speed").getAsInt() - speedlimit;
                boolean speeding = speedDiff > 0; 
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
}
