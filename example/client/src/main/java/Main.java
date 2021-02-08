import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
import java.util.concurrent.*; 

import com.github.devicehive.client.service.SecureProcessor; 

import org.joda.time.Duration; 
import org.joda.time.DateTime;  

public class Main
{
    private static final int port = 6767;  
    private static final int[] speedlimits = {60,100,120}; 
    
    public static void main(String[] args) throws Exception
    {
        Main main = new Main(); 
        main.init(port); 

    }

    private void init(int port) throws Exception
    {
        MySecureProcessor processor = new MySecureProcessor(); 
        processor.init(port);
    }

    private class MySecureProcessor extends SecureProcessor
    {   
        @Override 
        public void process(DeviceNotification notification) throws Exception
        { 
                String deviceId = notification.getDeviceId(); 
                List<Parameter> response = new ArrayList<Parameter>(); 
                JsonObject parameters = getDecryptedParameters(notification, deviceId); 
                response.add(new Parameter("license-plate",parameters.get("license-plate").getAsString())); 
                int speed = parameters.get("speed").getAsInt(); 
                if(speed > speedlimits[0])
                {
                    response.add(new Parameter("speeding", "yes")); 
                    response.add(new Parameter("speed difference", String.valueOf(speed - speedlimits[0]))); 

                }
                else
                {
                    response.add(new Parameter("speeding", "no")); 
                    response.add(new Parameter("speed difference", String.valueOf(speed - speedlimits[0]))); 
                }
                this.getServers().get(deviceId).sendCommand(encryptedCommand("response", response, deviceId));
        }
    }
}
