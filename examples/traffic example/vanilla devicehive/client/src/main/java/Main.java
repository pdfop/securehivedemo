
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
import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.io.IOException;


public class Main {

    //DeviceHive settings
    private static final String URL = "http://20.67.42.189/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.zjTBCAtTFqNMfLzW11Xqz44UJ5wPkM5j1AUW_6vzpew";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.mMF61xlkbHblaSCwikU0m5J9s7hWDAzSAbuKpPkM0GQ";
    
    private Device device = null; 
    private static DeviceHive client; 

    public static void main(String[] args) 
    { 
        Main main = new Main(); 
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);

        for(String deviceId : args)
        {
            main.init(deviceId); 
        }
    }

    private void init(String deviceId)
    {
        Processor p = new Processor(deviceId, 130); 
        p.start(); 
    }

    private class Processor extends Thread 
    {
        private Device device; 
        private int speedLimit; 

        public Processor(String deviceId, int speedLimit)
        {
            DHResponse<Device> response = client.getDevice(deviceId); 
            if(!response.isSuccessful())
            {
                System.out.println("Device Failed"); 
                return; 
            }
            device = response.getData(); 
            this.speedLimit = speedLimit; 
        }

        @Override 
        public void run()
        {
            try
            {
                subscribe(); 
            }
            catch(Exception e)
            {
                System.out.println(e.getMessage()); 
                e.printStackTrace(System.out); 
            }
        }

        private void subscribe()
        {
            NotificationFilter filter = new NotificationFilter(); 
            filter.setNotificationNames("data"); 
            filter.setStartTimestamp(DateTime.now()); 
            filter.setEndTimestamp(DateTime.now());
    
            final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
            {
                
                public void onSuccess(List<DeviceNotification> notifications)
                {
                    for(DeviceNotification notification : notifications)
                    {
                        process(notification); 
                    }
                }
                 
                public void onFail(FailureData failureData)
                {
                     System.out.println(failureData); 
                }
            };
    
            device.subscribeNotifications(filter,callback);
    
    
        }

        private void process(DeviceNotification notification)
        {
            JsonObject parameters = notification.getParameters(); 
            int speedDiff = parameters.get("speed").getAsInt() - speedLimit;
            boolean speeding = speedDiff > 0; 
            if(speeding)
            {
                List<Parameter> response = new ArrayList<>(); 
                response.add(new Parameter("license-plate", parameters.get("license-plate").getAsString())); 
                response.add(new Parameter("speeding", Boolean.toString(speeding)));
                response.add(new Parameter("time", parameters.get("time").getAsString())); 
                response.add(new Parameter("over-limit", String.valueOf(speedDiff))); 
                device.sendCommand("response", response); 
            }
        }



    }
}