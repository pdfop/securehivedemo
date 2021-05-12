

import com.github.devicehive.client.model.DHResponse;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.NotificationFilter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceHive;

import com.google.gson.JsonObject;
import org.joda.time.DateTime; 

import java.io.IOException;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Arrays;

public class Main {

    //DeviceHive server connection
    private static final String URL = "http://20.67.42.189//api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYyMDgwNTQzOTc4NiwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.v48hkgchHdkk2x4B4qEF0hz_hfMRnxaj8qluWKEoKMY";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYzNjUyODQzOTc4NiwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.HqZp_oLBIhgzTjCSuE7zuSCvguj4MSSUjV-aaE_0unE";

    private static DeviceHive client; 
    // Devices managed by this Host App 
    List<String> deviceIds; 
    // proxy connection
    private final String enclaveIP = "10.0.1.1"; 
    private final int port = 6767; 


    public static void main(String[] args) throws IOException
    { 
        Main main = new Main(); 
        main.init(Arrays.asList(args));  
    }

    private void init(List<String> deviceIds) throws IOException
    {
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        deviceIds = deviceIds; 
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
            SecureProcessorProxy proxy = SecureProcessorProxyBuilder.build(device, filter, enclaveIP, port); 

        }
    }

}
