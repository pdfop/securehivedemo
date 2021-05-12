import java.util.Scanner;
import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.lang.IllegalThreadStateException; 
import org.joda.time.DateTime; 
import com.github.devicehive.client.model.DHResponse;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.DeviceHive;
import com.github.devicehive.client.model.DeviceNotificationsCallback; 
import com.github.devicehive.client.model.DeviceNotification; 
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.CommandFilter; 
import com.github.devicehive.client.service.DeviceCommand;

import javax.crypto.spec.IvParameterSpec; 
import java.security.SecureRandom; 
import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.security.*;
import java.security.cert.Certificate; 
import javax.crypto.*; 
import java.util.concurrent.*;
import static org.junit.Assert.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.util.Base64; 
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays; 
import java.math.BigInteger;

import java.util.concurrent.atomic.AtomicInteger;
import org.joda.time.format.DateTimeFormat; 
import org.joda.time.format.DateTimeFormatter; 
import org.joda.time.Duration;


public class Main
{	
    // Devicehive Setup 
    private static final String URL = "http://20.67.42.189//api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYyMDgwNTQzOTc4NiwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.v48hkgchHdkk2x4B4qEF0hz_hfMRnxaj8qluWKEoKMY";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYzNjUyODQzOTc4NiwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.HqZp_oLBIhgzTjCSuE7zuSCvguj4MSSUjV-aaE_0unE";

    private static final String deviceId = "Taxi"; 
    private Device device; 
    private HomomorphicDevice secureDevice;
    private List<Long> times = new ArrayList<Long>();
    private List<Long> encTimes = new ArrayList<Long>(); 
    private int timerIndex = 0; 
    private int responseCounter = 0;  
    private AtomicInteger dataIndex = new AtomicInteger(0);
    private List<List<String>> data = new ArrayList<>(); 

    private DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-mm-dd H:m:s");
    ScheduledExecutorService service = Executors.newScheduledThreadPool(2);
    
    public static void main(String[] args) throws InterruptedException, IllegalThreadStateException, Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput);  
        main.subscribeCommands();
        main.loadData();
        main.generateAndSendData(); 
        main.sendRequests();         
    }

    private void init_device(String deviceId) throws Exception
    {
        final DeviceHive client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> deviceResponse = client.getDevice(deviceId);
        if (!deviceResponse.isSuccessful())
        {
            System.out.println(deviceResponse);
            return;
        }
        device = deviceResponse.getData();
        secureDevice = new HomomorphicDevice(device); 
    }

    private void generateAndSendData()
    {
        Callable<Void> sendData = new Callable<Void>() 
        {
            public Void call() 
            {
                int current = dataIndex.getAndIncrement();
                try
                {
                    List<Parameter> params = new ArrayList<>(); 
                    List<String> line = data.get(current); 
                    params.add(new Parameter("pickup-time", line.get(0))); 
                    params.add(new Parameter("dropoff-time", line.get(1))); 

                    params.add(new Parameter("pickup-location", line.get(3))); 
                    params.add(new Parameter("dropoff-location", line.get(4))); 

                    params.add(new Parameter("$distance", line.get(2))); 
                    params.add(new Parameter("$cost", line.get(5))); 

                    secureDevice.sendEncryptedNotification("data", params);
                }
                catch (Exception e)
                {
                    System.out.println(e.getMessage());
                    e.printStackTrace(System.out);  
                }
                finally
                {
                    DateTime now = DateTime.parse(data.get(current).get(1), formatter); 
                    DateTime next = DateTime.parse(data.get(dataIndex.get()).get(1), formatter); 
                    Duration delay = new Duration(now,next); 
                    // here we can make the scheduling rate variable e.g. by additionally reading a measurement delay from our data csv 
                    service.schedule(this, delay.getMillis(), TimeUnit.MILLISECONDS);
                    return null;

                }

            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);  
    }
   
    private void loadData() throws Exception
    {
        try (BufferedReader br = new BufferedReader(new FileReader("resources/" + deviceId + ".csv"))) 
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] values = line.split(",");
                data.add(Arrays.asList(values));
            }
        }
    }

    private void subscribeCommands() throws Exception
    {
        CommandFilter filter = new CommandFilter(); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now()); 
        filter.setCommandNames("response"); 
        final DeviceCommandsCallback callback = new DeviceCommandsCallback()
        {
            public void onSuccess(List<DeviceCommand> commands)
            {
                for(DeviceCommand command : commands)
                {   
                    try
                    {
                        JsonObject parameters = secureDevice.getDecryptedParameters(command); 
                    }
                    catch(Exception e)
                    {
                        e.printStackTrace(System.out);
                    }
                }
                responseCounter += commands.size(); 
                if(responseCounter % 5 == 0)
                {
                    times.set(timerIndex, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - times.get(timerIndex))); 
                    timerIndex++;
                }
                if(timerIndex > 360)
                {
                    try
                    {
                        System.out.println("Done"); 
                        FileWriter writer = new FileWriter(deviceId +  ".txt"); 
                        for (int i = 0; i < times.size(); i++)
                        {
                            long time = times.get(i);
                            String dataString = String.valueOf(time) + "\n"; 
                            writer.write(dataString);     
                        }   
                        writer.close(); 
                        System.out.println("File Written"); 
                    }
                    catch(Exception e)
                    {
                        System.out.println(e.getMessage()); 
                    }
                }
  
            }
            public void onFail(FailureData fail)
            {
                System.out.println(fail); 
            }
        }; 
        secureDevice.subscribeCommands(filter, callback); 

    }

    private void sendRequests()
    {
        // schedule the device to send a notification with variable delay 
        Callable<Void> sendRequest = new Callable<Void>() {
            public Void call() {
                int current = dataIndex.getAndIncrement();
                try 
                {
                    List<Parameter> req = new ArrayList<>(); 
                    req.add(new Parameter("test", "test")); 
                    secureDevice.sendEncryptedNotification("request", req); 
                    times.add(System.nanoTime());   
                } 
                catch(Exception e)
                {
                    System.out.println(e.getMessage()); 

                }
                finally
                {
                    if(timerIndex <= 360)
                    {
                        service.schedule(this, 5L, TimeUnit.SECONDS);
                    }                    
                }    
             return null;
            }
        };
        service.schedule(sendRequest,60L, TimeUnit.SECONDS);
    }    
}   
