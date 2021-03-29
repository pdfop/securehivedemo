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
import java.io.*; 
import java.util.concurrent.*;
import java.util.Arrays; 
import java.util.concurrent.atomic.AtomicInteger; 
import org.joda.time.format.DateTimeFormat; 
import org.joda.time.format.DateTimeFormatter; 
import org.joda.time.Duration;
import com.google.gson.*;

public class Main
{
    // Devicehive server connection 
    private static final String URL = "http://desktop-nedtc0v.fritz.box/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.07-pTHlWIvEEi5lWCU6-dVe2is6eB1fwrTTFv1ssMoM";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.16ocpHfZA11AE-3xXXxK4Y5Ld9pmXql7XFD8CgT5dAE";
    private DeviceHive client; 
    // single device to send notifications and receive commands
    // **declaring a SecureDevice rather than a Device **  
    private SecureDevice secureDevice;
    private String deviceId;   
    // data loading 
    private AtomicInteger dataIndex = new AtomicInteger(0);
    private List<List<String>> data = new ArrayList<>(); 

    private  List<Long> times = new ArrayList<Long>();

    private  int timerIndex = 0; 
    private  int responseCounter = 0;  
    private DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-mm-dd H:m:s");
 
    public static void main(String[] args) throws InterruptedException, IllegalThreadStateException, Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput); 
        main.loadData(); 
        main.generateAndSendData();
        main.subscribeCommands();
        main.sendRequests();  
    
    }

    /**
     * Initialize Connection to the DeviceHive Server, get a Device from the Server and get a secureDevice 
     */
    private void init_device(String deviceId) throws Exception
    {
        this.deviceId = deviceId;
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
        DHResponse<Device> deviceResponse = client.getDevice(deviceId);
        if (!deviceResponse.isSuccessful())
        {
            System.out.println(deviceResponse);
            return;
        }
	    // ** using the device from the server's response to create a SecureDevice ** 
        secureDevice = SecureDeviceBuilder.build(deviceResponse.getData());  
    }

    /**
     * Load simulated data from a given csv file
     */
    private void loadData()
    {
        try
        {
            BufferedReader br = new BufferedReader(new FileReader("resources/" + deviceId + ".csv"));
            String line;
            while ((line = br.readLine()) != null)
            {
                String[] values = line.split(",");
                data.add(Arrays.asList(values));
            }
        }   
        catch(IOException e)
        {
            System.out.println("Failed to read Data. " + e.getMessage()); 
        }
    }

    private void generateAndSendData()
    {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // schedule the device to send a notification with variable delay 
        Callable<Void> sendData = new Callable<Void>() {
            public Void call() {
                int current = dataIndex.getAndIncrement();
                try 
                { 
                    // build notification parameters    
                    List<Parameter> params = new ArrayList<Parameter>(); 
		            List<String> line = data.get(current); 
                    params.add(new Parameter("pickup-time", line.get(0))); 
                    params.add(new Parameter("dropoff-time", line.get(1)));
                    params.add(new Parameter("distance", line.get(2))); 
                    params.add(new Parameter("pickup-location", line.get(3)));
                    params.add(new Parameter("dropoff-location", line.get(4)));
                    params.add(new Parameter("cost", line.get(5)));
                    // encrypt and publish the notification 
		            // **calling sendEncryptedNotification rather than sendNotification, note that sendNotification is also available ** 
                    secureDevice.sendEncryptedNotification("data", params);  

                } 
                catch(Exception e)
                {
                    System.out.println(e.getMessage()); 

                }
                finally
                {
                    // with this scheduling the device still send a new Notification everyt time a real Taxi trip would have been completed. 
                    // CAREFUL: Potenial nullpointer for the last trip, however this version stops a long time before that anyway. 
                    DateTime now = DateTime.parse(data.get(current).get(1), formatter); 
                    DateTime next = DateTime.parse(data.get(dataIndex.get()).get(1), formatter); 
                    Duration delay = new Duration(now,next); 
                    // here we can make the scheduling rate variable e.g. by additionally reading a measurement delay from our data csv 
                    service.schedule(this, delay.getMillis(), TimeUnit.MILLISECONDS);                    
                }    
             return null;
            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);
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
                            responseCounter++; 
                            if(responseCounter % 5 == 0)
                            {
                                times.set(timerIndex, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - times.get(timerIndex))); 
                                timerIndex++;
		    System.out.println(times);
                                if(timerIndex > 270)
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
                        }
                        catch(Exception e)
                        {
                            e.printStackTrace(System.out);
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
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
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
                    service.schedule(this, 10L, TimeUnit.SECONDS);                    
                }    
             return null;
            }
        };
        service.schedule(sendRequest,0L, TimeUnit.MILLISECONDS);
    }
  
}   


