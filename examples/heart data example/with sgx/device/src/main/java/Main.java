import java.util.Scanner;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.io.*; 
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger; 
import java.util.Arrays; 

import org.joda.time.DateTime; 
import com.google.gson.JsonObject;

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

public class Main
{
    // Devicehive server connection 
    private static final String URL = "http://20.67.42.189/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.zjTBCAtTFqNMfLzW11Xqz44UJ5wPkM5j1AUW_6vzpew";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxOTgyMDAwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.mMF61xlkbHblaSCwikU0m5J9s7hWDAzSAbuKpPkM0GQ";

    private DeviceHive client; 
    // single device to send notifications and receive commands 
    private SecureDevice secureDevice;
    private String deviceId; 
    // execution time measurement 
    private List<Long> times = new ArrayList<Long>();
    //private List <Long> encTimes = new ArrayList<Long>();
    private AtomicInteger timerIndex = new AtomicInteger(0);   
    // data loading 
    private AtomicInteger dataIndex = new AtomicInteger(0); 
    private List<List<String>> data = new ArrayList<>(); 
 
    public static void main(String[] args) throws InterruptedException, IllegalThreadStateException, Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput); 
        main.loadData();
        main.subscribeCommands(); 
        main.generateAndSendData(); 
    
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

    /**
     * Subscribe the Device to the Commands sent by the processor 
     */
    private void subscribeCommands() throws Exception
    {
        CommandFilter filter = new CommandFilter();
        // time filter for the most recent command  
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());
        // set command names according to the name set in the processor  
        filter.setCommandNames("response"); 
        // callback method when receiving a now command. all reactions to the commands by the processor start here 
        final DeviceCommandsCallback callback = new DeviceCommandsCallback()
        {
            public void onSuccess(List<DeviceCommand> commands)
            {   
                for(DeviceCommand command : commands)
                {   
                    try
                    {
                       // measuring time from notification creation until parameter decryption of the response to represent system latency 
                        JsonObject parameters = secureDevice.getDecryptedParameters(command);
                        long end = System.nanoTime();
                        int current = timerIndex.getAndIncrement();   
                        times.set(current, TimeUnit.NANOSECONDS.toMillis(end - times.get(current))); 
                        System.out.println(current); 
                    }
                    
                    catch(Exception e)
                    {
                        System.out.println(e.getMessage()); 
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

    private void generateAndSendData()
    {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        // schedule the device to send a notification with variable delay 
        Callable<Void> sendData = new Callable<Void>() {
            public Void call() {
                int current = dataIndex.getAndIncrement(); 
                try 
                { 
                    // time stamp for the start of the lifecycle of this notification 
                    long start = System.nanoTime(); 
                    // build notification parameters    
                    List<Parameter> params = new ArrayList<Parameter>(); 
                    params.add(new Parameter("qt", data.get(current).get(0))); 
                    params.add(new Parameter("rr", data.get(current).get(2)));
                    times.add(start);
                    // encrypt and publish the notification 
                    secureDevice.sendEncryptedNotification("data", params);
                } 
                catch(Exception e)
                {
                    System.out.println(e.getMessage()); 

                }
                finally
                {
                    // stopping after we processed all available data  
                    if(current >= data.size() -1)
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
                    // reschedule this data generation 
                    else
                    {
                        // here we can make the scheduling rate variable e.g. by additionally reading a measurement delay from our data csv 
                        service.schedule(this, Long.parseLong(data.get(current).get(2)), TimeUnit.MILLISECONDS); 
                    }

                }    
             return null;
            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);
    }




    
}   


