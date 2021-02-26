# DeviceHive  java client Intel SGX extension 
Using:   
[DeviceHive](https://devicehive.com)  
[DeviceHive java client](https://github.com/devicehive/devicehive-java)  
[sgx-lkl](https://github.com/lsds/sgx-lkl) 

# Concepts 
A SecureDevice runs on trusted hardware that generates data in some way e.g. through a connected sensor.

A DeviceHive client app running on untrusted cloud hardware establishes a connection to this Device and subscribes to its DeviceNotifications. This client app contains an instance of SecureProcessorProxy that connects it to the SecureProcessor running inside an enclave on the same hardware using sgx-lkl. 

A SecureProcessor receives the DeviceNotifications of a connected Device through the proxy and implements the logic to process and analyze the notification data. It responds to the Device and publishes messages through DeviceCommands. The Device may subscribe and react to those Commands.  

The SecureDevice has a key store that contains a x.509 certificate of the SecureProcessor and additional secrets used in the remote attestation process. The SecureProcessor had a key store that contains x.509 certificates for each SecureDevice that may want to connect to it. Each key store also contains the private key for its owner's certificate. The certificates use 2048 bit RSA. 

When a SecureDevice connects to the Server it will start sending a key exchange request notification. Upon receiving the request the processor will respond with a key exchange command. This command contains a time stamp and its signature, signed using the SecureProcessor's private key. The SecureDevice verifies the signature using the SecureProcessor's public it. If this is successful the SecureDevice generates a 256 bit AES key. It encrypts this key using the SecureProcessor's public key and sends a key exchange notification the with key and the original time stamp signed using its own private key. 
The SecureProcessor again verifies this signature and if successful will decrypt the contained AES key and accept it as the   SecureDevice's message key. From this point onwards the SecureDevice and SecureProcessor will encrypt and decrypt DeviceNotifications and DeviceCommands using this key.  

When creating an encrypted notification or command the system will include the IV used for encryption and a time stamp to be used as a nonce. When the other party receives the message it will use the IV to allow for decryption and verify that the included time stamp matches the time stamp of the notification/command itself. This prevents a set of encrypted parameters from being replayed or delayed by a malicious host app.  

The entire system and its classes are transparent to the DeviceHive server and work with  an unmodified Server. 

# Dataflow  
The diagram below displays the way messages take through the system. They are encrypted during the entire transmission and only decrypted while in the enclave.  
![diagram](https://puu.sh/HkleA/72a285c15b.png)  
# Classes 

## SecureDevice 
A SecureDevice is an extended version of the Device class in the DeviceHive java client. As it is not part of the DeviceHive library it is however not a child class and does not directly implement the DeviceInterface.  
As the server is assumed to be unmodified the underlying connection has to be made with a normal Device.  
Once created a SecureDevice will automatically perform the authentication and key exchange procedure as well as remote attestation of the enclave containing the SecureProcessor, if configured.  
A SecureDevice retains all of the functionality of a regular Device and is capable of sending normal as well as encrypted notifications.  

### Usage Example
This is a minimal example of a typical Device application. It establishes a connection to the DeviceHive server, creates a Device and sends constant notifications with a constant delay.

```
public class Main
{
    // Devicehive Setup 
    static final String URL = ""; 
    private static final String accessToken = "";
    private static final String refreshToken = "";

    public static void main(String[] args)
    {
        // initialize client 
        final DeviceHive client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);

        // create or get device from server 
        DHResponse<Device> deviceResponse = client.getDevice("deviceName");

        // Device instance 
        Device device = deviceResponse.getData();

        // loop data generation 
        while(true)
        {
        	List<Parameter> parameters = new ArrayList<Parameter>(); 
            parameters.add(new Parameter("name", "value")); 
            device.sendNotification("data",parameters); 
        }     
    }
}

```
This is how it changes with the usage of a SecureDevice sending an encrypted notification. 

```
public class Main
{
    // Devicehive Setup 
    static final String URL = ""; 
    private static final String accessToken = "";
    private static final String refreshToken = "";

    public static void main(String[] args)
    {
        // initialize client 
        final DeviceHive client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);

        // create or get device from server 
        DHResponse<Device> deviceResponse = client.getDevice("deviceName");

        // Device instance 
        // **changed**
        SecureDevice device = SecureDeviceBuilder.build(deviceResponse.getData()); 

        // loop data generation 
        while(true)
        {
        	List<Parameter> parameters = new ArrayList<Parameter>(); 
            parameters.add(new Parameter("name", "value")); 
            // **changed**
            device.sendEncryptedNotification("data",parameters); 
        }     
    }
}

```
## SecureProcessorProxy
The SecureProcessorProxy is instantiated by the host app on the hardware the SecureProcessor is running on. It acts as the bridge between the DeviceHive server and the processor running inside the enclave. The proxy manages the subscription to DeviceNotifications. Upon creation it will first connect to the enclave, then set the necessary subscriptions for the key exchange. After the exchange has been completed it will switch to the user-supplied NotificationFilter and begin exchanging messages between the DeviceHive server and the processor in the enclave. This happens transparently to the user developing the rest of the host app, which may be used to process non-privacy-critical notifications etc.  

### Usage Example  
The SecureProcessorProxy have to be viewed in conjunction, as in a regular DeviceHive client that wishes to process notification data both the message exchange and processing would happen in the same function. 

```
public class Main {

    //DeviceHive settings
    private static final String URL = ""; 
    private static final String accessToken = "";
    private static final String refreshToken = "";
    private static DeviceHive client;
    
    public static void main(String[] args) 
    {  
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
		// getting Device
        DHResponse<Device> response = client.getDevice("deviceName"); 
        if(!response.isSuccessful())
        {
            System.out.println("Device Failed"); 
            return; 
        }
        Device device = response.getData();
        // setting up a NotificationFilter
        NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("data"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());

        final DeviceNotificationsCallback callback = new DeviceNotificationsCallback()
        {
            
            public void onSuccess(List<DeviceNotification> notifications)
            {
        		int threshhold = 500;
                List<Parameter> response = new ArrayList<Parameter>(); 
                JsonObject parameters = notification.getParameters(); 
                int value = parameters.get("name").getAsInt(); 
                response.add(new Parameter("over-threshhold", Boolean.toString( value > threshhold))); 
                device.sendCommand("response", response); 
            }
             
            public void onFail(FailureData failureData)
            {
                 // handle failures  
            }
        };

        device.subscribeNotifications(filter,callback);          
    }
}
```

When using a  SecureProcessorProxy the same example looks like this: 
```
public class Main {

    //DeviceHive settings
    private static final String URL = ""; 
    private static final String accessToken = "";
    private static final String refreshToken = "";
    private static DeviceHive client;
    // ** additional parameters for enclave connection, could be saved in configuration file as well **
    private final String enclaveIP = "10.0.1.1"; 
    private final int port = 6767; 
    
    public static void main(String[] args) 
    {  
        //Initiating DeviceHive
        client = DeviceHive.getInstance().init(URL, refreshToken, accessToken);
		// getting Device
        DHResponse<Device> response = client.getDevice("deviceName"); 
        if(!response.isSuccessful())
        {
            System.out.println("Device Failed"); 
            return; 
        }
        // setting up a NotificationFilter
        Device device = response.getData();
		NotificationFilter filter = new NotificationFilter(); 
        filter.setNotificationNames("data"); 
        filter.setStartTimestamp(DateTime.now()); 
        filter.setEndTimestamp(DateTime.now());
        /*
        *  creating a SecureProcessorProxy
        *  parameters could also be loaded from a configuration file
        *  upon creation connection to the enclave, authentication and key exchange happen without user interaction
        *  processing logic (compare to the onSuccess function in the callback above) will be implemented
        *   in the SecureProcessor
        */ 
        SecureProcessorProxy proxy = SecureProcessorProxyBuilder.build(device, filter, enclaveIP, port); 
    }
}
```
## SecureProcessor 
A SecureProcessor replaces the onSuccess function in a DeviceNotificationsCallback with its own process function. It runs inside an enclave using sgx-lkl and exchanges messages through a SecureProcessorProxy. It establishes an authenticated encrypted connection to any number of SecureDevices. It receives notifications matching a user-supplied NotificationFilter, decrypts their parameters and verifies their freshness, processes this data in a user-supplied way and sends an encrypted response to the proxy, which sends it to the DeviceHive server. 
### Usage Example 
```
public class Main
{
    private static final int port = 6767;   
    
    public static void main(String[] args) throws Exception
    {
        MySecureProcessor processor = new MySecureProcessor(); 
        processor.init(port);
    }

    private class MySecureProcessor extends SecureProcessor
    {   
        @Override 
        public void process(DeviceNotification notification) throws Exception
        { 
        		int threshhold = 500;
        		// assuming the enclave handles multiple devices we need to match notification to device
                String deviceId = notification.getDeviceId(); 
                List<Parameter> response = new ArrayList<Parameter>(); 
                JsonObject parameters = getDecryptedParameters(notification, deviceId);
                int value = parameters.get("name").getAsInt(); 
                response.add(new Parameter("over-threshhold", Boolean.toString( value > threshhold))); 
                this.getServers().get(deviceId).sendCommand(encryptedCommand("response", response, deviceId));
        }
    }
}
```

## Builder classes
Builder classes are included in the project to allow for easily overloaded constructor of other classes.  
The builder classes implement overloaded build methods that accept a number of parameters and fill any missing ones with default values.  
They also read configuration files in default and user-supplied paths. 
Their intent is to remove configuration code from the respective classes to keep their code about their functionality. 
Any builder is used like ClassName.build(parameters)
