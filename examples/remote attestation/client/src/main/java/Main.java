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
                JsonObject parameters = getDecryptedParameters(notification); 
                System.out.println("Received notification after attestation and key exchange");                
        }
    }
}