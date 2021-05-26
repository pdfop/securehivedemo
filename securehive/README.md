# SecureHive: A DeviceHive Security Extension using Intel SGX
Using:   
[DeviceHive](https://devicehive.com)  
[DeviceHive Java client](https://github.com/devicehive/devicehive-java)  
[SGX-LKL](https://github.com/lsds/sgx-lkl)   
This readme will provide an overview of the SecureHive extension. For details please refer to the written version of the thesis. 
# Concepts 
A SecureDevice runs on trusted hardware that generates data in some way e.g. through a connected sensor.

A DeviceHive client app running on untrusted cloud hardware establishes a connection to this Device and subscribes to its DeviceNotifications. This client app contains an instance of SecureProcessorProxy that connects it to the SecureProcessor running inside an enclave on the same hardware using SGX-LKL. 

A SecureProcessor receives the DeviceNotifications of a connected Device through the proxy and implements the logic to process and analyze the notification data. It responds to the Device and publishes messages through DeviceCommands. The Device may subscribe and react to those commands.  

The SecureDevice has a key store that contains a x.509 certificate and associated RSA public key of the SecureProcessor and additional secrets used in the remote attestation process. The SecureProcessor has a key store that contains x.509 certificates and associated public keys for each SecureDevice that may want to connect to it. Each key store also contains the private key for its owner's certificate. The certificates use 2048 bit RSA. 

When creating an encrypted notification or command the system will include the IV used for encryption, a randomly generated nonce and a message authentication code.
The recipient will verify the freshness of the nonce and the integrity of the message before decrypting its parameters.  

The entire system and its classes are transparent to the DeviceHive server and work with an unmodified Server. 

# Authentication
When a SecureDevice connects to the server it will send a key exchange request notification. Upon receiving the request the processor will respond with a key exchange command. This command contains a time stamp and its signature, signed using the SecureProcessor's private key. The SecureDevice verifies the signature using the SecureProcessor's public key. If this is successful the SecureDevice generates a 256 bit AES key and a key for SHA512 HMAC. It encrypts these keys using the SecureProcessor's public key. It combines these encrypted keys with the timestamp and signs this message using its own private key. The device sends the keys and signature to the SecureProcesor, which verifies the signature and timestamp. If this is successful it decrypts the keys using its own private key and accepts them as the current session keys. From this point onwards the SecureDevice and SecureProcessor will encrypt and decrypt DeviceNotifications and DeviceCommands using this key.  

This protocol can be seen here:  
![diagram](https://puu.sh/HJPed/5403c8d074.png) 

Warning: This protocol was designed and implemented by me and not verified for correctness by an expert. Use at your own risk.  


# Dataflow  
The diagram below displays the way messages take through the system. They are encrypted during the entire transmission and only decrypted while in the enclave.  
![diagram](https://puu.sh/HJPfl/72f9342f80.png)  

# Usage
SecureHive requires:  
  * A DeviceHive server e.g. set up using docker https://docs.devicehive.com/docs/deployment-with-docker
  * The original DeviceHive Java client library version 3.1.2. https://github.com/devicehive/devicehive-java linked to your project
  * SecureHive linked to your project 
    * How to build: 
      * clone this repository 
      * run ``` gradle build``` in this directory
      * link the resulting .jar file in build/libs to your project              

      
Additionally on the processing side:  
  * Intel SGX capability 
  * SGX Driver and PSW https://github.com/intel/linux-sgx
  * SGX-LKL https://github.com/lsds/sgx-lkl/tree/legacy
  * a network interface for the enclave https://github.com/lsds/sgx-lkl/tree/legacy#networking-support
  * disk image for SGX-LKL https://github.com/lsds/sgx-lkl/tree/legacy#creating-sgx-lkl-disk-images-with-sgx-lkl-disk
  * build process for enclave - refer to make files in examples/*/with sgx/client for examples 

Finally each SecureHive application expects a key store:
  * default path resources/storename.pkcs12
    * can be changed in configuration files or passed to build methods 
  *  pkcs12 format 
  * content for a Device application: 
    * certificate and secret key for the Device, named as device ID
    * certificate and public key for the SecureProcessor, named enclaveCert 
    * optionally for remote attestation: 
      * Intel Attestation Service certificate 
      * MRSIGNER as password entry 
      * MRENCLAVE as password entry 
      * SGX Service Provider ID as password entry
  * content for a Device application: 
    * certificate and secret key for the SecureProcssor, named enclaveCert
    * certificate and public key for each Device, named by device ID 
# Classes 

## SecureDevice 
A SecureDevice is an extended version of the Device class. As the DeviceHive server remains unmodified the underlying connection has to be established using a regular Device that is then used to create a SecureDevice. After creation a SecureDevice will optionally perform remote attestation of the enclave the SecureProcessor is running in. It will then automatically perform the authentication and key exchange protocol. The user can then send notifications that are transparently encrypted using AES and have a nonce and SHA512 HMAC added to them. Incoming encrypted DeviceCommands can easily be decrypted and their nonce and MAC will be verified automatically. A SecureDevice retains all of the functionality of a regular Device and is capable of sending normal as well as encrypted notifications.  

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
The SecureProcessorProxy is instantiated by the host app on the hardware the SecureProcessor is running on. It acts as the bridge between the DeviceHive server and the  SecureProcessor running inside the enclave. The proxy manages the subscription to DeviceNotifications. Upon creation it will first connect to the enclave, then set the necessary subscriptions for the key exchange. After the exchange has been completed it will switch to the user-supplied NotificationFilter and begin exchanging messages between the DeviceHive server and the processor in the enclave. This happens transparently to the user developing the rest of the host app, which may be used to process non-privacy-critical notifications etc.  

### Usage Example  
The SecureProcessorProxy has to be viewed in conjunction with its SecureProcessor, as in a regular DeviceHive client that wishes to process notification data both the message exchange and processing would happen in the same function. 

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
    private final String enclaveIP = ""; 
    private final int port = ; 
    
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
        *  in the SecureProcessor
        */ 
        SecureProcessorProxy proxy = SecureProcessorProxyBuilder.build(device, filter, enclaveIP, port); 
    }
}
```
## SecureProcessor 
A SecureProcessor replaces the onSuccess function in a DeviceNotificationsCallback with its own process function. It runs inside an enclave using SGX-LKL and exchanges messages through a SecureProcessorProxy. It establishes an authenticated encrypted connection to a SecureDevice whose certificate has previously been added to its key store. It receives notifications matching a user-supplied NotificationFilter. It transparently decrypts their parameters and verifies their freshness and integrity before passing it to the process function. The process function is implemented by the user and performs arbitrary computation on the data. The user can send DeviceCommands that will be transparently encrypted and have a nonce and MAC added to them before leaving the enclave.  
### Usage Example 
```
public class Main
{
    private static final int port = ;   
    
    public static void main(String[] args) throws Exception
    {
        MySecureProcessor processor = new MySecureProcessor(); 
        processor.init(port);
    }

    private class MySecureProcessor extends SecureProcessor
    {   
        @Override 
        public void process(DeviceNotificationWrapper notification) throws Exception
        { 
        		int threshhold = 500;
                List<Parameter> response = new ArrayList<Parameter>(); 
                // ** parameters are transparently decrypted before the notification is passed to this function
                JsonObject parameters = notification.getParameters(); 
                int value = parameters.get("name").getAsInt(); 
                response.add(new Parameter("over-threshhold", Boolean.toString( value > threshhold))); 
                sendCommand(encryptedCommand("response", response));
        }
    }
}
```

## Builder classes
Builder classes i.e. SecureDeviceBuilder and SecureProcessorProxyBuilder are intended to move configuration code out of their respective classes to keep them about their functionality. Builders implement an overloaded build method that accepts a flexible number of parameters. Builder can also read configuration files that may be placed in a default or custom path. Any missing parameters are filled in with default values. For details about the parameters and usage refer to the comments in the respective class source code. 

## Wrapper classes 
The DeviceNotificationWrapper and DeviceCommandWrapper classes represent DeviceNotifications and DeviceCommands on the enclave side. They add necesarry functionality to these classes without modifying the original library.    
The original classes have private constructors and cannot be directly instanciated in the enclave. Furthermore they cannot be serialized which is necessary to transport them between the host app jvm and the jvm in the enclave.  
