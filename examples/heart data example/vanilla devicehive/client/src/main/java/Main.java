
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

    private static final String deviceId = "HeartSensor"; 
    private static final String URL = "http://desktop-nedtc0v.fritz.box/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.07-pTHlWIvEEi5lWCU6-dVe2is6eB1fwrTTFv1ssMoM";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.16ocpHfZA11AE-3xXXxK4Y5Ld9pmXql7XFD8CgT5dAE";
    private Device device = null; 
    private DeviceHive client = null; 
    private int threshhold = 500; 
    public static void main(String[] args) 
    { 
        Main main = new Main(); 
        main.init(); 
        main.subscribe(); 
    }

    private void init()
    {
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> response = client.getDevice(deviceId); 
        if(!response.isSuccessful())
        {
            System.out.println("Device Failed"); 
            return; 
        }
        device = response.getData(); 
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
        // create response parameters 
        List<Parameter> response = new ArrayList<Parameter>();
        Parameter hr = new Parameter("heartrate", ""); 
        Parameter lqts = new Parameter("lqts", "");

        // get notification parameters 
        JsonObject parameters = notification.getParameters(); 
        int qt = parameters.get("qt").getAsInt(); 
        int rr = parameters.get("rr").getAsInt();

        // calculate response values 
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
        
        // build and send response
        response.add(lqts);
        response.add(hr); 
        device.sendCommand("response", response); 
    }

}
