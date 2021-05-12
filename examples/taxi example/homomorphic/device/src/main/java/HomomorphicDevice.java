
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;
import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.DHResponse; 
import com.github.devicehive.client.model.NotificationFilter; 
import com.github.devicehive.client.model.CommandFilter;
import com.github.devicehive.client.model.DeviceCommandsCallback;
import com.github.devicehive.client.model.DeviceNotificationsCallback;
import javax.crypto.spec.IvParameterSpec; 
import java.security.SecureRandom; 
import java.io.FileInputStream; 
import java.io.IOException; 
import java.security.cert.Certificate;
import java.security.cert.CertificateException; 
import static org.junit.Assert.*;  
import java.security.*;
import javax.crypto.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.Mac;

import org.joda.time.DateTime;
import com.google.gson.JsonObject; 

import java.io.IOException;
import java.math.BigInteger;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays; 
import java.util.Random; 
import java.security.KeyPair;

import com.google.gson.*; 
import com.google.gson.JsonObject;
import com.google.gson.JsonElement; 
import java.util.Base64; 

import com.n1analytics.paillier.*;
import com.google.common.hash.BloomFilter; 
import com.google.common.hash.Funnels;

import java.nio.charset.StandardCharsets;

public class HomomorphicDevice
{   
    private Device device; 
    // mutual authentication 
    private PublicKey enclavePK; 
    private PrivateKey deviceSK; 
    private SecretKey messageKey;  
    private SecretKey macKey; 
    private BloomFilter<String> nonces;  

    private KeyStore store;
    private final char[] pwdArray = "testpw".toCharArray();     
    private final String storePath = "SecureHive.pkcs12";   

    // homomorphic encryption 
    private PaillierPrivateKey privKey;
    private PaillierPublicKey pubKey;
    private PaillierContext cipher; 
    // string encoding 
    private Base64.Decoder decoder = Base64.getDecoder(); 
    private Base64.Encoder encoder = Base64.getEncoder();  

    public HomomorphicDevice(Device device) throws Exception
    {
        this.device = device;
        nonces = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 2500, 0.01d);
        init();  
        keyExchange(); 

    }

    public DHResponse<DeviceNotification> sendEncryptedNotification(String notification, List<Parameter> parameters) throws Exception
    {

        // initialize encryption cipher 
        Cipher encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        IvParameterSpec iv = generateIV();
        encrypt.init(Cipher.ENCRYPT_MODE, messageKey, iv); 
        
        // initialize message authentication 
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(macKey);
        StringBuilder sb = new StringBuilder(400); 
        List<Parameter> encparameters = new ArrayList<>(); 
        for(Parameter param : parameters)
        {   
            if(param.getKey().contains("$"))
            {
                // encrypt parameter key and value
                EncodedNumber e = cipher.getEncodingScheme().encode(Double.parseDouble(param.getValue())); 
                EncryptedNumber c = cipher.encrypt(e); 
                Parameter n = new Parameter(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())), c.calculateCiphertext().toString());
                Parameter exp = new Parameter(encoder.encodeToString(encrypt.doFinal((param.getKey() + "exponent").getBytes())),String.valueOf(c.getExponent()));
                encparameters.add(n); 
                encparameters.add(exp); 
                sb.append(n.getKey()); 
                sb.append(n.getValue());
                sb.append(exp.getKey()); 
                sb.append(exp.getValue());
                
            }
            else 
            {
                Parameter p = new Parameter(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())), encoder.encodeToString(encrypt.doFinal(param.getValue().getBytes())));
                encparameters.add(p);
                sb.append(p.getKey()); 
                sb.append(p.getValue());  
            }

        }
        mac.update(sb.toString().getBytes()); 
        // add IV and Nonce 
        encparameters.add(new Parameter("iv", encoder.encodeToString(iv.getIV()))); 
        encparameters.add(new Parameter("nonce", encoder.encodeToString(encrypt.doFinal(generateNonce()))));
        encparameters.add(new Parameter("mac", encoder.encodeToString(mac.doFinal())));
        return device.sendNotification(notification, encparameters); 
    }

    public JsonObject getDecryptedParameters(DeviceCommand command) throws Exception
    { 
        JsonObject parameters = command.getParameters(); 
        if(parameters == null)
        {
            return null; 
        } 
        JsonObject decryptedParameters = new JsonObject(); 
        // extract IV and prepare decryption cipher
        Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
        IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
        decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);
        // initialize message authentication
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(macKey);
        StringBuilder sb = new StringBuilder(200); 
        String serverMAC = parameters.get("mac").getAsString(); 
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

        JsonObject keyDecrypt = new JsonObject(); 
        for(java.util.Map.Entry<String, JsonElement> entry : parameters.entrySet())
        {
            sb.append(entry.getKey()); 
            sb.append(entry.getValue().getAsString());
            keyDecrypt.addProperty(new String(decrypt.doFinal(decoder.decode(entry.getKey()))), entry.getValue().getAsString()); 
        }

        for(java.util.Map.Entry<String, JsonElement> entry : keyDecrypt.entrySet())
        {
            if(entry.getKey().contains("exponent"))
            {
                continue; 
            }
            if(entry.getKey().contains("$"))
            {
                decryptedParameters.addProperty(entry.getKey(),
                String.valueOf(
                    privKey.decrypt(
                        new EncryptedNumber(cipher, entry.getValue().getAsBigInteger(),
                        Integer.parseInt(new String(decrypt.doFinal(decoder.decode(keyDecrypt.get(entry.getKey() + "exponent").getAsString().getBytes()))))))
                        .decodeDouble()));
            }
            else 
            {
              decryptedParameters.addProperty(entry.getKey(), new String(decrypt.doFinal(decoder.decode(entry.getValue().getAsString().getBytes())))); 
            }     
        }
        mac.update(sb.toString().getBytes()); 
        String clientMAC = encoder.encodeToString(mac.doFinal()); 
        if(clientMAC.equals(serverMAC))
        {
            return decryptedParameters; 
        }
        else
        {   
            System.out.println("Message Authentication Failed"); 
            return new JsonObject(); 
        }
    }

    private void init() throws Exception
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
            Certificate enclaveCert = store.getCertificate("enclaveCert");
            assertNotNull(enclaveCert); 
            deviceSK = (PrivateKey) store.getKey("deviceCert", pwdArray);
            assertNotNull(deviceSK);  
            enclavePK = enclaveCert.getPublicKey(); 
        }
        catch(KeyStoreException | AssertionError e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents."); 
        }         

    }

    /**
     * Generate a 256 bit AES Key to be used for message encryption and a 256 bit for message Authentication
     */
    private void generateKey() throws Exception
    {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            messageKey = keyGen.generateKey(); 
            KeyGenerator macGen = KeyGenerator.getInstance("HmacSHA512");
            macGen.init(256); 
            macKey = macGen.generateKey(); 
    }

    private IvParameterSpec generateIV()
    {
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 1);
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

    private void generateKeys() throws Exception
    {
        privKey = PaillierPrivateKey.create(1024); 
        pubKey = privKey.getPublicKey(); 
        cipher = pubKey.createSignedContext(); 
    }

    private void keyExchange() throws Exception
    {
        Boolean sent = false;
        while(true)
        {
            if(sent)
            {
                break;
            }
            device.sendNotification("$keyrequest", null); 

            List<DeviceCommand> comms = device.getCommands(DateTime.now().minusSeconds(5), DateTime.now(), 50);
            for(DeviceCommand command : comms)
            {
                if(command.getCommandName().equals("$keyexchange"))
                {
                    JsonObject parameters = command.getParameters(); 
                    String signed = parameters.get("signed").getAsString(); 
                    String timestamp = parameters.get("timestamp").getAsString(); 

                    Signature publicSignature = Signature.getInstance("SHA256withRSA");
                    publicSignature.initVerify(enclavePK);
                    publicSignature.update(timestamp.getBytes());
                    byte[] signatureBytes = decoder.decode(signed);
                    Boolean verified = publicSignature.verify(signatureBytes);
                    if(verified)
                    {
                        generateKey(); 
                        generateKeys();                        
                        Cipher encrypt=Cipher.getInstance("RSA/ECB/PKCS1Padding");
                        encrypt.init(Cipher.PUBLIC_KEY, enclavePK);
                        byte[] encryptedKey = encrypt.doFinal(messageKey.getEncoded());
                        String keyString = encoder.encodeToString(encryptedKey);

                        // encrypt the MAC key using the enclave public key
                        byte[] encryptedMacKey = encrypt.doFinal(macKey.getEncoded()); 
                        String macKeyString = encoder.encodeToString(encryptedMacKey);

                        Signature privateSignature = Signature.getInstance("SHA256withRSA");
                        privateSignature.initSign(deviceSK);
                        // combine key and nonce to prevent key replacement
                        String keyNonce = keyString + macKeyString + timestamp; 
                        privateSignature.update(keyNonce.getBytes());
                        // sign using the device private key
                        byte[] signature = privateSignature.sign();
                        String sigString = encoder.encodeToString(signature); 
                        // build and send notification
                        List<Parameter> notifParams = new ArrayList<Parameter>(); 
                        notifParams.add(new Parameter("key", keyString)); 
                        notifParams.add(new Parameter("macKey", macKeyString)); 
                        notifParams.add(new Parameter("signed", sigString)); 
                        device.sendNotification("$keyexchange", notifParams);



                        Cipher encryptAES = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        IvParameterSpec iv = generateIV();
                        encryptAES.init(Cipher.ENCRYPT_MODE, messageKey, iv); 

                        String heKey = encoder.encodeToString(encryptAES.doFinal(pubKey.getModulus().toByteArray())); 
                        List<Parameter> heParams = new ArrayList<Parameter>(); 
                        heParams.add(new Parameter("key", heKey));
                        heParams.add(new Parameter("iv", encoder.encodeToString(iv.getIV())));  
                        device.sendNotification("$HEK", heParams); 
                        sent = true; 
                        break; 
                    }                   
                }
            }
            Thread.sleep(1000);  

        }
    }

    public void save()
    {
        device.save(); 
    }

    public List<DeviceCommand> getCommands(DateTime startTimestamp, DateTime endTimestamp, int maxNumber)
    {
        return device.getCommands(startTimestamp, endTimestamp, maxNumber); 
    }

    public List<DeviceNotification> getNotifications(DateTime startTimestamp, DateTime endTimestamp)
    {
        return device.getNotifications(startTimestamp, endTimestamp); 
    }

    public DHResponse<DeviceCommand> sendCommand(String command, List<Parameter> parameters)
    {
        return device.sendCommand(command, parameters); 
    }

    public DHResponse<DeviceNotification> sendNotification(String notification, List<Parameter> parameters)
    {
        return device.sendNotification(notification, parameters); 
    }

    public void subscribeCommands(CommandFilter commandFilter, DeviceCommandsCallback commandsCallback)
    {
        device.subscribeCommands(commandFilter, commandsCallback); 
    }

    public void subscribeNotifications(NotificationFilter notificationFilter, DeviceNotificationsCallback notificationCallback)
    {
        device.subscribeNotifications(notificationFilter, notificationCallback); 
    }
    public void unsubscribeCommands(CommandFilter commandFilter)
    {
        device.unsubscribeCommands(commandFilter); 
    }

    public void unsubscribeNotifications(NotificationFilter notificationFilter)
    {
        device.unsubscribeNotifications(notificationFilter); 
    }

}