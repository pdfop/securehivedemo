import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.Gson.*;
import org.joda.time.DateTime;

import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.Parameter;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Hashtable;
import java.util.Map;
import java.util.Base64;
import java.util.Random;  
import java.util.Scanner;

import java.security.cert.Certificate; 
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.security.Signature;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException; 
import java.security.NoSuchAlgorithmException; 
import java.security.KeyStore; 
import java.security.KeyStoreException; 
import java.security.SecureRandom; 

import javax.crypto.spec.SecretKeySpec; 
import javax.crypto.spec.IvParameterSpec; 
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.Mac;

import java.io.FileInputStream; 
import java.io.BufferedReader; 
import java.io.FileReader;
import java.io.BufferedInputStream; 
import java.io.BufferedOutputStream; 
import java.io.ObjectInputStream; 
import java.io.ObjectOutputStream; 
import java.io.IOException; 

import java.net.Socket; 
import java.net.ServerSocket; 

import java.nio.channels.*; 
import java.nio.charset.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import com.google.gson.Gson.*;
import com.google.gson.*; 
import java.net.InetSocketAddress;
import static org.junit.Assert.*; 

import com.google.common.hash.BloomFilter; 
import com.google.common.hash.Funnels;

import java.nio.charset.StandardCharsets;


public class SecureProcessor
{
    // proxy connection
    private Gson gson; 
    private ServerSocketChannel serverSocket;
    private SocketChannel in;
    private SocketChannel out; 
    // storing cerfiticates 
    private KeyStore store; 
    private char[] pwdArray = null; 
    private final String storePath = "resources/SecureHiveClient.pkcs12";  
    // mutual authentication 
    private PrivateKey enclaveSK; 
    private PublicKey devicePK; 
    // message encryption
    private SecretKey messageKey;
    private SecretKey macKey;  

    private ByteBuffer buffer = ByteBuffer.allocate(4096);
    private BloomFilter<String> nonces; 

    // encoding 
    private static Base64.Decoder decoder = Base64.getDecoder(); 
    private static Base64.Encoder encoder = Base64.getEncoder(); 

    // overloaded method to pass default parameters 
    public void init(int port, String storePass) throws Exception
    {
        init(port, storePass, 2500, 0.01d); 
    }

    public void init(int port, String storePass, int nonceCapacity, double nonceError) throws Exception
    { 
        pwdArray = storePass.toCharArray();
        initSecurity();
        nonces = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), nonceCapacity, nonceError); 
        GsonBuilder builder = new GsonBuilder(); 
        JsonSerializer<DateTime> dateTimeSerializer = new JsonSerializer<DateTime>()
        {
            @Override 
            public JsonElement serialize(DateTime src, java.lang.reflect.Type typeOfSrc, JsonSerializationContext context)
            {
                return new JsonPrimitive(src.toString());
            } 
        }; 
        builder.registerTypeAdapter(DateTime.class, dateTimeSerializer); 

        JsonDeserializer<DateTime> dateTimeDeserializer = new JsonDeserializer<DateTime>()
        {
            @Override 
            public DateTime deserialize(JsonElement json, java.lang.reflect.Type typeOfT, JsonDeserializationContext context) throws JsonParseException 
            {
                return DateTime.parse(json.getAsString());  
            } 
        }; 
        builder.registerTypeAdapter(DateTime.class, dateTimeDeserializer);

        builder.setLenient(); 
        gson = builder.create(); 

        serverSocket = ServerSocketChannel.open(); 
        serverSocket.socket().bind(new InetSocketAddress(port)); 
	    System.out.println("Processor initialized. Waiting for connection...");
        out = serverSocket.accept();
        out.configureBlocking(false); 
        in = serverSocket.accept();
        in.configureBlocking(true); 
        System.out.println("Connected");
        keyExchange(); 
        receiveNotifications(); 
    }

    /**
     * This method must be overriden by any specific enclave processor. 
     * The logic depending on the DeviceNotification is to be implemented here. 
     * This is the equivalent of the onSuccess method of the DeviceNotificationsCallback for regular DeviceHive subscriptions 
     */
    public void process(DeviceNotificationWrapper notification) throws Exception
    {
        throw new RuntimeException("Method process must be Overriden for any SecureProcessor Instance");
    }

    /**
     * Create a DeviceCommandWrapper with encrypted parameter names and values 
     * @see DeviceCommandWrapper for an explanation of why this returns a Wrapper 
     * Adds additional parameters "iv" and "nonce" to the command to allow for decryption and verification of the freshness of the command.
     */
    public DeviceCommandWrapper encryptedCommand(String commandName, List<Parameter> parameters) throws Exception
    {
        // initialize encryption cipher 
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec iv = generateIV();
        encrypt.init(Cipher.ENCRYPT_MODE, messageKey, iv); 
        // initialize message authentication
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(macKey);
        StringBuilder sb = new StringBuilder(200); 
        for(Parameter param : parameters)
        {
            // encrypt parameter key and value
            param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
            param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));
            sb.append(param.getKey()); 
            sb.append(param.getValue()); 
        }
        // set message for MAC
        mac.update(sb.toString().getBytes()); 
        // add IV, Nonce and MAC
        parameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 
        parameters.add(new Parameter("nonce", encoder.encodeToString(encrypt.doFinal(generateNonce()))));   
        parameters.add(new Parameter("mac", encoder.encodeToString(mac.doFinal())));
        return new DeviceCommandWrapper(commandName, parameters); 
    }

    /**
     * Load the keyStore and the enclave's secret key
     */
    private void initSecurity() throws Exception
    {
        // load KeyStore from file system 
        FileInputStream fis = null; 
        try 
        {
            store = KeyStore.getInstance("pkcs12");
            fis = new FileInputStream(storePath); 
            store.load(fis, pwdArray);
            fis.close(); 
        }
        catch(IOException  | KeyStoreException | CertificateException | NoSuchAlgorithmException e)
        {
            store = null; 
            System.out.println("KeyStore could not be loaded. \n" + e.getMessage()); 
        }

        try
        { 
            KeyStore.PasswordProtection storePass = new KeyStore.PasswordProtection(pwdArray);
            enclaveSK = (PrivateKey) store.getKey("enclaveCert", pwdArray);
            assertNotNull(enclaveSK);  
        }
        catch(KeyStoreException | AssertionError | NoSuchAlgorithmException | UnrecoverableKeyException e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents."); 
        }     
    }

    /**
     * Generate an Initialization Vector for the AES encryption
     */
    private IvParameterSpec generateIV() 
    {
        byte[] iv = new byte[16];
        new SecureRandom().nextBytes(iv);
        return new IvParameterSpec(iv);
    }
    /**
     * Generate a 64bit nonce to ensure message freshness
     */
    private byte[] generateNonce()
    {
        byte[] array = new byte[8];
        new Random().nextBytes(array);
        return array;     
    }

    /**
     * Decrypt the names and values of the parameters of a DeviceNotification. 
     * Expects the Parameters to include a key "iv" to be used to initialize the decryption cipher
     * @returns a JsonObject of the decrypted parameters of a DeviceNotification, equivalent to DeviceNotification.getParameters() 
     */
    public void decrypt(DeviceNotificationWrapper notification) throws Exception
    {
        JsonObject parameters = notification.getParameters(); 
        if(parameters == null)
        {
            return; 
        } 
        JsonObject decryptedParameters = new JsonObject(); 
        // initialize decryption cipher 
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
        decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);
        // initialize message authentication
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(macKey);
        StringBuilder sb = new StringBuilder(200); 
        String clientMAC = parameters.get("mac").getAsString(); 
        // extract and verify nonce 
        String nonce = new String(decrypt.doFinal(decoder.decode(parameters.get("nonce").getAsString())));
        boolean contained = nonces.mightContain(nonce); 
        if(contained)
        {
            decryptedParameters.addProperty("nonce-seen", Boolean.toString(true));
        } 
        else
        {
            decryptedParameters.addProperty("nonce-seen", Boolean.toString(false)); 
            nonces.put(nonce); 
        }

        // remove meta parameters before decrypting
        parameters.remove("iv");
        parameters.remove("nonce"); 
        parameters.remove("mac");
        for(java.util.Map.Entry<String, JsonElement> entry : parameters.entrySet())
        {
            sb.append(entry.getKey()); 
            sb.append(entry.getValue().getAsString()); 
            decryptedParameters.addProperty(new String(decrypt.doFinal(decoder.decode(entry.getKey()))), new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString()))));         
        }
        mac.update(sb.toString().getBytes()); 
        String serverMAC = encoder.encodeToString(mac.doFinal());
        if(clientMAC.equals(serverMAC))
        {
            notification.setParameters(decryptedParameters); 
        }
        else
        {   
            System.out.println("Message Authentication Failed"); 
            notification.setParameters(new JsonObject()); 
        }
    }

     /**
         * Read a DeviceNotification from the in stream and process it 
         * This method loops until the Stream is closed or null is written to it 
         * The reading process is blocking until a DeviceNotification has been read. 
         */
        private void receiveNotifications() throws Exception
        {
            DeviceNotificationWrapper notification; 
            int read = 0;  
            while((read = in.read(buffer)) > 0)
            {
                int current = 0; 
                if(read > 4)
                {  
                    buffer.position(0); 
                    while(current < read)
                    {
                        int size = buffer.getInt(); 
                        current = current + 4; 
                        byte[] dest = new byte[size]; 
                        buffer.get(dest,0,size); 
                        String s = new String(dest, StandardCharsets.UTF_8); 
                        current = current + size; 
                        buffer.position(current); 
                        notification = gson.fromJson(s.trim(), DeviceNotificationWrapper.class);
                        decrypt(notification); 
                        process(notification); 
                    }   
                    buffer.clear(); 
                }
            }
        }

        /**
         * Pass a DeviceCommandWrapper to the out stream. 
         * @see DeviceCommandWrapper for an explanation of why this expects and writes a Wrapper  
         */
        public void sendCommand(DeviceCommandWrapper command)
        {
            try
            {  
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(gson.toJson(command));
                ByteBuffer count = ByteBuffer.allocate(4); 
                count.putInt(bytes.limit()); 
                count.position(0); 
                while(count.hasRemaining())
                {
                    out.write(count);
                }
                while(bytes.hasRemaining())
                {
                    out.write(bytes);
                }   
            }
            catch(Exception e)
            {
                System.out.println(e.getMessage());
            }
        }

        /**
        * Load the public key of a device from the key store
        */
        private void loadDeviceCert(String deviceId)
        {
            try
            {
                Certificate deviceCert = store.getCertificate(deviceId);
                assertNotNull(deviceCert); 
                devicePK = deviceCert.getPublicKey();  
            }
            catch(Exception e)
            {
                System.out.println("Device Certificate could not be loaded " + e.getMessage()); 
            }
        }


        private void keyExchange() throws Exception
        { 
            DeviceNotificationWrapper notification = new DeviceNotificationWrapper(null,null,null,null,null,null); 
            int bytesRead = 0;  
            while((bytesRead = in.read(buffer)) > 0)
            {
                String s = new String(java.util.Arrays.copyOfRange(buffer.array(), 0 , bytesRead));
                buffer.clear();  
                notification = gson.fromJson(s.trim(), DeviceNotificationWrapper.class); 
                if(notification.getNotification().equals("$keyrequest"))
                {
                    break;
                }
            }

            String timestamp = DateTime.now().toString();  
            List<Parameter> params = new ArrayList<Parameter>(); 
            params.add(new Parameter("timestamp", timestamp)); 

            Signature privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(enclaveSK);
            privateSignature.update(timestamp.getBytes());
            byte[] signature = privateSignature.sign();
            String signedStamp = Base64.getEncoder().encodeToString(signature);
            params.add(new Parameter("signed",signedStamp));
            DeviceCommandWrapper wrapper = new DeviceCommandWrapper("$keyexchange", params); 
            sendCommand(wrapper);   

            while((bytesRead = in.read(buffer)) > 0)
            {
                String s = new String(java.util.Arrays.copyOfRange(buffer.array(), 0 , bytesRead));
                buffer.clear();  
                notification = gson.fromJson(s.trim(), DeviceNotificationWrapper.class); 
                if(notification.getNotification().equals("$keyexchange"))
                {  
                    String deviceId = notification.getDeviceId(); 
                    loadDeviceCert(deviceId);
                    JsonObject parameters = notification.getParameters();
                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(devicePK);

                    Cipher decrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                    decrypt.init(Cipher.PRIVATE_KEY, enclaveSK);
 
                    byte[] signatureBytes = decoder.decode(parameters.get("signed").getAsString());
                    String sigString = parameters.get("key").getAsString() + parameters.get("macKey").getAsString() + timestamp; 
                    publicSignature.update(sigString.getBytes());
                    Boolean verified = publicSignature.verify(signatureBytes);
                    if(verified)
                    { 
                        byte[] decodedKey = decrypt.doFinal(decoder.decode(parameters.get("key").getAsString()));
                        byte[] decodedMacKey = decrypt.doFinal(decoder.decode(parameters.get("macKey").getAsString()));
                        messageKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
                        macKey = new SecretKeySpec(decodedMacKey, 0, decodedKey.length, "HmacSHA512");
                        return; 
                    }
                    else
                    {
                        System.out.println("Invalid Signature. Exiting."); 
                        System.exit(1); 

                    }
                } 
            }
        
        }
}
