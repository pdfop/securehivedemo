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


public class ThreadedSecureProcessor
{
    // proxy connection
    private Gson gson; 
    private ServerSocketChannel server;  
    // storing cerfiticates 
    private KeyStore store; 
    private char[] pwdArray = "testpw".toCharArray(); 
    private final String storePath = "resources/SecureHiveClient.pkcs12";  
    // mutual authentication 
    private PrivateKey enclaveSK; 
    // encoding 
    private static Base64.Decoder decoder = Base64.getDecoder(); 
    private static Base64.Encoder encoder = Base64.getEncoder(); 

    public void init(int port) throws Exception
    { 
        initSecurity(); 
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

        server = ServerSocketChannel.open();
        server.socket().setReuseAddress(true);  
        server.socket().bind(new InetSocketAddress(6767)); 
	    System.out.println("Processor initialized. Waiting for connection...");
        while(true)
        {

            SocketChannel out = server.accept();
            SocketChannel in = server.accept();
            System.out.println("Connected."); 
            startThread(in,out); 
        }
    }

    protected void startThread(SocketChannel in, SocketChannel out)
    {
        Processor p = new Processor(in,out); 
        p.start(); 
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

    protected class Processor extends Thread
    {
        // proxy connection
        private SocketChannel in; 
        private SocketChannel out; 
        private ByteBuffer buffer = ByteBuffer.allocate(8192);
        private SecretKey messageKey; 

        // mutual authentication
        private PublicKey devicePK; 

        public Processor(SocketChannel in, SocketChannel out)
        {
            this.in = in; 
            this.out = out; 
        }

        public void run()
        {
            try
            {
                keyExchange(); 
                receiveNotifications(); 
            }
            catch(Exception e)
            {
                System.out.println("Failed to establish proxy connection " + e.getMessage()); 
                e.printStackTrace(System.out); 
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
                    publicSignature.update(timestamp.getBytes());
                    byte[] signatureBytes = decoder.decode(parameters.get("signed").getAsString());
                    Boolean verified = publicSignature.verify(signatureBytes);
                    if(verified)
                    { 
                        Cipher decrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        decrypt.init(Cipher.PRIVATE_KEY, enclaveSK);
                        byte[] decodedKey = decrypt.doFinal(decoder.decode(parameters.get("key").getAsString()));
                        messageKey = new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"); 
                        return; 
                    }
                } 
            }        
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
            Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
            IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
            decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);
            java.util.Set<java.util.Map.Entry<java.lang.String,JsonElement>> entries = parameters.entrySet(); 
            for(java.util.Map.Entry<String, JsonElement> entry : entries)
            {
                if(entry.getKey().equals("iv"))
                {
                    continue;
                }
                decryptedParameters.addProperty(new String(decrypt.doFinal(decoder.decode(entry.getKey()))),
                    new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString())))
                ); 
            } 
            notification.setParameters(decryptedParameters); 
        }

        /**
         * Create a DeviceCommandWrapper with encrypted parameter names and values 
         * @see DeviceCommandWrapper for an explanation of why this returns a Wrapper 
         * Adds additional parameters "iv" and "nonce" to the command to allow for decryption and verification of the freshness of the command.
         */
        public DeviceCommandWrapper encryptedCommand(String commandName, List<Parameter> parameters) throws Exception
        {
            Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
            IvParameterSpec iv = generateIV();
            encrypt.init(Cipher.ENCRYPT_MODE, messageKey, iv); 

            for(Parameter param : parameters)
            {
                // encrypt parameter key and value
                param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
                param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));
            }
            // add IV and Nonce 
            parameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 
            parameters.add(new Parameter("nonce", encoder.encodeToString(encrypt.doFinal(generateNonce().getBytes()))));   
            return new DeviceCommandWrapper(commandName, parameters); 
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
         * Generate an Initialization Vector for the AES encryption
         */
        private IvParameterSpec generateIV() 
        {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            return new IvParameterSpec(iv);
        }

        /**
         * Generate a time stamp as a String to be used as a nonce for a secureCommand
         */
        private String generateNonce()
        {
            byte[] array = new byte[15];
            new Random().nextBytes(array);
            return new String(array, Charset.forName("UTF-8"));    
        }
    }
}
