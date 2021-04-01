
import com.github.devicehive.client.model.CommandFilter;
import com.github.devicehive.client.model.DHResponse;
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.NotificationFilter;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.service.DeviceHive;

import com.google.gson.JsonObject;
import org.joda.time.DateTime; 

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Arrays; 
import java.util.Map; 
import java.util.Hashtable; 

public class Main {

    //DeviceHive server connection
    private static final String URL = "http://20.67.42.189/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.zjTBCAtTFqNMfLzW11Xqz44UJ5wPkM5j1AUW_6vzpew";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.mMF61xlkbHblaSCwikU0m5J9s7hWDAzSAbuKpPkM0GQ";
   
    private static DeviceHive client; 
    // proxy connection
    // ** additional parameters for the proxy connection ** 
    private final String enclaveIP = "10.0.1.1"; 
    private static final int port = 6767; 


    public static void main(String[] args) throws IOException
    { 
        Main main = new Main(); 
        main.init(Arrays.asList(args));  
    }

    private void init(List<String> deviceIds) throws IOException
    {
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken); 
        for(String deviceId : deviceIds)
        {
            DHResponse<Device> response = client.getDevice(deviceId); 
            if(!response.isSuccessful())
            {
                return; 
            }
            Device device = response.getData();

            NotificationFilter filter = new NotificationFilter(); 
            filter.setNotificationNames("data"); 
            filter.setStartTimestamp(DateTime.now()); 
            filter.setEndTimestamp(DateTime.now());
	        // ** creation of a proxy ** 
            SecureProcessorProxy proxy = SecureProcessorProxyBuilder.build(device, filter, enclaveIP, port); 

        }
    }

}
