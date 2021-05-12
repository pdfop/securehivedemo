package com.n1analytics.paillier; 
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

import com.n1analytics.paillier.*;

public class Main
{
    //DeviceHive settings
    private static final String URL = "http://20.67.42.189//api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYyMDgwNTQzOTc4NiwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.v48hkgchHdkk2x4B4qEF0hz_hfMRnxaj8qluWKEoKMY";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYzNjUyODQzOTc4NiwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.HqZp_oLBIhgzTjCSuE7zuSCvguj4MSSUjV-aaE_0unE";
    private static DeviceHive client = null;  

    public static void main(String[] args) throws Exception
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
        Processor p = new Processor(deviceId, "128"); 
        p.start(); 
    }

    private class Processor extends Thread 
    {
        private Device device; 
        private String speedLimit; 
        private HomomorphicProcessor processor; 

        public Processor(String deviceId, String speedLimit)
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
                processor = new HomomorphicProcessor(device, speedLimit); 
                subscribe(); 
            }
            catch(Exception e)
            {
                System.out.println(e.getMessage()); 
                e.printStackTrace(System.out); 
            }
        }

        private void subscribe() throws Exception
        {
            NotificationFilter filter = new NotificationFilter(); 
            filter.setNotificationNames("data"); 
            filter.setStartTimestamp(DateTime.now()); 
            filter.setEndTimestamp(DateTime.now());
    
            final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
            {
                
                public void onSuccess(List<DeviceNotification> notifications)
                {
                    for(DeviceNotification notif : notifications)
                    {
                        try 
                        { 
                            processor.process(notif); 
                        } 
                        catch (Exception e)
                        {
                            System.out.println(e.getMessage());
                            e.printStackTrace(System.out);
                        }
                        
                    } 
                }
                 
                public void onFail(FailureData failureData)
                {
                     System.out.println(failureData); 
                }
            };
            device.subscribeNotifications(filter,callback);
        }
    }
}