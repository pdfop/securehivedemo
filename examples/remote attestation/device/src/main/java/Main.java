import org.joda.time.DateTime; 
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

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
    private static final String URL = "http://desktop-nedtc0v.fritz.box/api/rest"; 
    private static final String accessToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MSwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.07-pTHlWIvEEi5lWCU6-dVe2is6eB1fwrTTFv1ssMoM";
    private static final String refreshToken = "eyJhbGciOiJIUzI1NiJ9.eyJwYXlsb2FkIjp7ImEiOlswXSwiZSI6MTYxNzE0MTYwMDAwMCwidCI6MCwidSI6MSwibiI6WyIqIl0sImR0IjpbIioiXX19.16ocpHfZA11AE-3xXXxK4Y5Ld9pmXql7XFD8CgT5dAE";
    private DeviceHive client; 
    // single device to send notifications and receive commands 
    private SecureDevice secureDevice;
    private String deviceId; 
 
    public static void main(String[] args) throws InterruptedException, IllegalThreadStateException, Exception
    {
        String deviceIdInput= args[0];
        Main main = new Main(); 
        main.init_device(deviceIdInput); 
        main.sendData(); 
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

    private void sendData() throws Exception
    {
        List<Parameter> params = new ArrayList<>(); 
        params.add(new Parameter("Test", "Test")); 
        secureDevice.sendEncryptedNotification("data", params); 
    }
}

