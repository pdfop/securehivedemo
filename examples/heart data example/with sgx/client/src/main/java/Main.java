import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceNotification;
import com.google.gson.JsonObject; 
import java.util.concurrent.*; 

import org.joda.time.Duration; 
import org.joda.time.DateTime;  

public class Main
{
    private static final int port = 6767;  
    private static final int threshhold = 500; 
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
        public void process(DeviceNotificationWrapper notification) throws Exception
        {  
            try
            {

            
                List<Parameter> response = new ArrayList<Parameter>(); 
                JsonObject parameters = getDecryptedParameters(notification); 

                Parameter hr = new Parameter("heartrate", ""); 
                Parameter lqts = new Parameter("lqts", "");   
                int qt = parameters.get("qt").getAsInt(); 
                int rr = parameters.get("rr").getAsInt();
                double lqtsValue = qt/Math.sqrt(rr);
                if(lqtsValue > threshhold)
                {
                    lqts.setValue(Boolean.toString(true)); 
                }
                else
                {
                    lqts.setValue(Boolean.toString(false)); 
                }
                hr.setValue(String.valueOf(60/((double)rr/1000))); 
                response.add(lqts);
                response.add(hr); 
                sendCommand(encryptedCommand("response", response));      
            }
            
            catch(Exception e)
            {
                System.out.println(e.getMessage());  
            }   
        }
    }
}
