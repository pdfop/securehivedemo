import java.io.*;
import java.util.Scanner;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Arrays; 
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger; 

import com.google.gson.*;
import org.joda.time.DateTime;
import org.joda.time.Duration;  

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
    private static final String URL = "http://20.67.42.189//api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYyMDgwNTQzOTc4NiwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.v48hkgchHdkk2x4B4qEF0hz_hfMRnxaj8qluWKEoKMY";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYzNjUyODQzOTc4NiwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.HqZp_oLBIhgzTjCSuE7zuSCvguj4MSSUjV-aaE_0unE";

    private DeviceHive client; 
    // single device to send notifications and receive commands
    // **declaring a SecureDevice rather than a Device **  
    private SecureDevice secureDevice;
    private String deviceId; 
    // execution time measurement 
    private List<Duration> times = new ArrayList<>(); 
    // data loading 
    private AtomicInteger dataIndex = new AtomicInteger(0);
    private List<List<String>> data = new ArrayList<>(); 
 
    public static void main(String[] args) throws Exception
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
				        // ** calling secureDevice.getDecryptedParameters(command) rather than command.getParameters() ** 
                        JsonObject parameters = secureDevice.getDecryptedParameters(command);
                        times.add(new Duration(DateTime.parse(parameters.get("time").getAsString()), DateTime.now()));   

                    }
                    catch(Exception e)
                    {
                       // e.printStackTrace(System.out);
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
                    // build notification parameters    
                    List<Parameter> params = new ArrayList<Parameter>(); 
                    params.add(new Parameter("license-plate", data.get(current).get(0))); 
                    params.add(new Parameter("speed", data.get(current).get(1)));
                    params.add(new Parameter("time", DateTime.now().toString())); 
                    // encrypt and publish the notification 
		            // **calling senEncryptedNotification rather than sendNotification, note that sendNotification is also available ** 
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
                                String responseTime = String.valueOf(times.get(i).getMillis()) + "\n";  
                                writer.write(responseTime);     
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
                        service.schedule(this, 500L, TimeUnit.MILLISECONDS);
                        //Long.parseLong(data.get(current).get(2))
                    }   

                }    
             return null;
            }
        };
        service.schedule(sendData,0L, TimeUnit.MILLISECONDS);
    }




    
}   


