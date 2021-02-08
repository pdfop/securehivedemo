package com.github.devicehive.client.service;


import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.Gson.*;
import org.joda.time.DateTime;

import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.model.DeviceCommandWrapper; 

import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Hashtable;
import java.util.Map;
import java.util.Base64; 


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


import static org.junit.Assert.*; 


public class SecureProcessor
{
    // proxy connection
    private ServerSocket serverSocket; 
    private Map<String, Server> servers = new Hashtable<String, Server>();  
    // storing cerfiticates 
    private KeyStore store; 
    private char[] pwdArray = "testpw".toCharArray(); 
    private final String storePath = "resources/SecureHiveClient.pkcs12";  
    // mutual authentication 
    private PrivateKey enclaveSK; 
    // message encryption
    private Map<String,SecretKey> messageKeys = new Hashtable<String, SecretKey>(); 

    // encoding 
    private static Base64.Decoder decoder = Base64.getDecoder(); 
    private static Base64.Encoder encoder = Base64.getEncoder(); 

    private List<String> deviceIds = new ArrayList<String>();  
 
    public void init(int port) throws Exception
    { 
        System.out.println("init server");
        initSecurity(); 
        serverSocket = new ServerSocket(port);
        while(true)
        {
            new Server(serverSocket.accept()).start();  
            System.out.println("Connected");
        }  
    }

    /**
     * This method must be overriden by any specific enclave processor. 
     * The logic depending on the DeviceNotification is to be implemented here. 
     * This is the equivalent of the onSuccess method of the DeviceNotificationsCallback for regular DeviceHive subscriptions 
     */
    public void process(DeviceNotification notification) throws Exception
    {
        throw new RuntimeException("Method process must be Overriden for any SecureProcessor Instance");
    }

    /**
     * Create a DeviceCommandWrapper with encrypted parameter names and values 
     * @see DeviceCommandWrapper for an explanation of why this returns a Wrapper 
     * Adds additional parameters "iv" and "nonce" to the command to allow for decryption and verification of the freshness of the command.
     */
    public DeviceCommandWrapper encryptedCommand(String commandName, List<Parameter> parameters, String deviceId) throws Exception
    {
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec iv = generateIV();
        encrypt.init(Cipher.ENCRYPT_MODE, messageKeys.get(deviceId), iv); 

        for(Parameter param : parameters)
        {
            // encrypt parameter key and value
            param.setValue(encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
            param.setKey(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())));
        }
        // add IV and Nonce 
        parameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 
        parameters.add(new Parameter("nonce", encoder.encodeToString(encrypt.doFinal(generateNonce().getBytes()))));   
        return new DeviceCommandWrapper(commandName, parameters, deviceId); 
    }

    /**
     * Overloaded encryption method for SecureProcessors that only handle a single Device
     */
    public DeviceCommandWrapper encryptedCommand(String commandName, List<Parameter> parameters) throws Exception
    {
        return encryptedCommand(commandName, parameters, deviceIds.get(0));
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
     * Generate a time stamp as a String to be used as a nonce for a secureCommand
     */
    private String generateNonce()
    {
        DateTime nonce = DateTime.now(); 
        return nonce.toString();     
    }

    /**
     * Decrypt the names and values of the parameters of a DeviceNotification. 
     * Expects the Parameters to include a key "iv" to be used to initialize the decryption cipher
     * @returns a JsonObject of the decrypted parameters of a DeviceNotification, equivalent to DeviceNotification.getParameters() 
     */
    public JsonObject getDecryptedParameters(DeviceNotification notification, String deviceId) throws Exception
    {
        JsonObject parameters = notification.getParameters();  
        JsonObject decryptedParameters = new JsonObject(); 
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
        decrypt.init(Cipher.DECRYPT_MODE, messageKeys.get(deviceId), iv);
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
        return decryptedParameters; 
    }

    /**
     * Overloaded decryption method for SecureProcessors that only handle a single device 
     */
    public JsonObject getDecryptedParameters(DeviceNotification notification) throws Exception
    {
        return getDecryptedParameters(notification, deviceIds.get(0)); 
    }
    
    public Map<String, Server> getServers()
    {
        return this.servers;
    }

    protected class Server extends Thread
    {
        private String deviceId; 
        // proxy connection
        private Socket clientSocket; 
        private ObjectInputStream in; 
        private ObjectOutputStream out;
        // mutual authentication
        private PublicKey devicePK; 

        public Server(Socket clientSocket)
        {
            this.clientSocket = clientSocket; 
        }

        public void run()
        {
            try
            {
                out = new ObjectOutputStream(new BufferedOutputStream(clientSocket.getOutputStream())); 
                out.flush();
                in = new ObjectInputStream(new BufferedInputStream(clientSocket.getInputStream())); 
                DeviceNotification notification; 
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
            DeviceNotification notification; 
            while((notification = (DeviceNotification) in.readObject()) != null)
            {
                process(notification); 
            }
        }

        /**
         * Pass a DeviceCommandWrapper to the out stream. 
         * @see DeviceCommandWrapper for an explanation of why this expects and writes a Wrapper  
         */
        public void sendCommand(DeviceCommandWrapper command) throws IOException
        {
            try
            {  
                out.writeObject(command);  
                out.flush();  
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
            DeviceNotification notification;
            String timeStamp = DateTime.now().toString();       
            while((notification = ((DeviceNotification) in.readObject())) != null)
            {
                if(notification.getNotification().equals("$keyrequest"))
                {
                    // add device to processor 
                    deviceId = notification.getDeviceId(); 
                    servers.put(deviceId,this);
                    deviceIds.add(deviceId);  
                    // load enclave cerfiticate 
                    loadDeviceCert(deviceId); 
                    // sign a time stamp  
                    List<Parameter> params = new ArrayList<Parameter>(); 
                    params.add(new Parameter("timestamp", timeStamp)); 
                    Signature privateSignature = Signature.getInstance("SHA256withRSA");
                    privateSignature.initSign(enclaveSK);
                    privateSignature.update(timeStamp.getBytes());
                    byte[] signature = privateSignature.sign();
                    String signedStamp = Base64.getEncoder().encodeToString(signature);
                    params.add(new Parameter("signed",signedStamp));
                    DeviceCommandWrapper wrapper = new DeviceCommandWrapper("$keyexchange", params, null); 
                    // send signed and plaintext stamp as a means of authentication
                    sendCommand(wrapper);
                }
    
                if(notification.getNotification().equals("$keyexchange"))
                {
                    // extract signed timestamp and verify that is what signed by the secret key belonging to devicePK 
                    JsonObject parameters = notification.getParameters(); 
                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(devicePK);
                    publicSignature.update(timeStamp.getBytes());
                    byte[] signatureBytes = decoder.decode(parameters.get("signed").getAsString());
                    Boolean verified = publicSignature.verify(signatureBytes);

                    // authentication successful, accept the key as new message key 
                    if(verified)
                    { 
                        Cipher decrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        decrypt.init(Cipher.PRIVATE_KEY, enclaveSK);
                        byte[] decodedKey = decrypt.doFinal(decoder.decode(parameters.get("key").getAsString())); 
                        messageKeys.put(deviceId, new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES"));
                        // sent to resolve blocking wait for a command after the keyexchange notification 
                        sendCommand(new DeviceCommandWrapper("ignore", null, null));
                        return; 
                    }
                }
            }          
        }

    }

}
