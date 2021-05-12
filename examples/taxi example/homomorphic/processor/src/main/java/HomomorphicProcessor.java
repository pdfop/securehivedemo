import java.io.*; 
import java.net.*;


import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.Gson.*;
import com.google.gson.*; 
import org.joda.time.DateTime;

import com.github.devicehive.client.service.DeviceCommand;
import com.github.devicehive.client.model.DeviceNotification;
import com.github.devicehive.client.model.FailureData;
import com.github.devicehive.client.model.Parameter;
import com.github.devicehive.client.service.Device;


import java.util.Collections;
import java.util.List;
import java.util.ArrayList; 
import java.util.Arrays; 
import java.util.Random; 

import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.security.*;
import java.security.cert.Certificate; 
import javax.crypto.*; 
import javax.crypto.Mac;

import java.io.*; 
import static org.junit.Assert.*; 
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.cert.CertificateException; 
import java.nio.charset.StandardCharsets;
import java.util.Base64; 
import javax.crypto.spec.SecretKeySpec; 
import javax.crypto.spec.IvParameterSpec; 
import java.nio.channels.*; 
import java.nio.charset.*;
import java.nio.ByteBuffer;

import java.math.BigInteger;
import java.security.KeyPair;

import com.google.gson.*; 
import com.google.gson.JsonObject;
import com.google.gson.JsonElement; 
import java.util.Base64; 

import com.n1analytics.paillier.*;
import com.google.common.hash.BloomFilter; 
import com.google.common.hash.Funnels;

import java.nio.charset.StandardCharsets;

public class HomomorphicProcessor
{
    // mutual auth 
    private PublicKey devicePK;   
    private PrivateKey enclaveSK;
    private SecretKey messageKey;  
    private SecretKey macKey; 
    private BloomFilter<String> nonces; 
    // TODO: get this as user input rather than hardcode it 
    private KeyStore store; 
    private char[] pwdArray = "testpw".toCharArray(); 

    // encoding 
    private Base64.Decoder decoder = Base64.getDecoder(); 
    private Base64.Encoder encoder = Base64.getEncoder(); 
 
    // homomorphic encryption 
    private PaillierPublicKey pubKey;
    protected PaillierContext cipher; 

    protected Device device; 

    public HomomorphicProcessor(Device device) throws Exception
    {
        this.device = device;
        nonces = BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 2500, 0.01d); 
        init(); 
        keyExchange(); 
    }

    private void init() throws Exception
    {
        // load KeyStore from file system 
        FileInputStream fis = null; 
        try 
        {
            store = KeyStore.getInstance("pkcs12");
            fis = new FileInputStream("SecureHive.pkcs12"); 
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
            Certificate deviceCert = store.getCertificate("deviceCert");
            assertNotNull(deviceCert); 
            KeyStore.PasswordProtection storePass = new KeyStore.PasswordProtection(pwdArray);
            enclaveSK = (PrivateKey) store.getKey("enclaveCert", pwdArray);
            assertNotNull(enclaveSK);  
            devicePK = deviceCert.getPublicKey(); 
        }
        catch(KeyStoreException | AssertionError | NoSuchAlgorithmException | UnrecoverableKeyException e)
        {
            System.out.println("Failed to recover all needed Secrets. Verify KeyStore contents."); 
        }     
    }

    private void keyExchange() throws Exception
    {

        DeviceNotification notification = null;
        List<DeviceNotification> notifications; 
        while(true)
        {
            notifications = device.getNotifications(DateTime.now().minusSeconds(1), DateTime.now()); 
            for(DeviceNotification notif : notifications)
            {
                if(notif.getNotification().equals("$keyrequest"))
                {
                    notification = notif; 
                    break; 
                }
            }
            if(notification != null)
            {
                break; 
            }
            Thread.sleep(500); 
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
        device.sendCommand("$keyexchange", params);   

        notification = null; 
        while(true)
        {
            notifications = device.getNotifications(DateTime.now().minusSeconds(1), DateTime.now()); 
            for(DeviceNotification notif : notifications)
            {
                if(notif.getNotification().equals("$keyexchange"))
                {  
                    JsonObject parameters = notif.getParameters();
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
                    }
                    else
                    {
                        System.out.println("Invalid Signature. Exiting."); 
                        System.exit(1); 

                    }
                } 
                if(notif.getNotification().equals("$HEK"))
                {
                    notification = notif; 
                    break; 
                }
            }
            if(notification != null)
            {
                JsonObject parameters = notification.getParameters();  
                Cipher decryptAES = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
                IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
                decryptAES.init(Cipher.DECRYPT_MODE, messageKey, iv);
                BigInteger modulus = new BigInteger(decryptAES.doFinal(decoder.decode(parameters.get("key").getAsString()))); 
                pubKey = new PaillierPublicKey(modulus); 
                cipher = pubKey.createSignedContext(); 
                return; 
            }
            Thread.sleep(500); 
        }         
    }

    public List<Parameter> encryptAndMac(List<Parameter> parameters) throws Exception
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
             if(param.getKey().contains("$") && !param.getKey().contains("exponent"))
             {
                 // encrypt parameter key and value
                 Parameter n = new Parameter(encoder.encodeToString(encrypt.doFinal(param.getKey().getBytes())), param.getValue());
                 encparameters.add(n); 
                 sb.append(n.getKey()); 
                 sb.append(n.getValue());
                 
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
         return encparameters; 
    }

    public JsonObject decryptAndVerify(JsonObject parameters) throws Exception
    {
        JsonObject decryptedParameters = new JsonObject(); 
         // extract IV and prepare decryption cipher
         Cipher decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); 
         IvParameterSpec iv = new IvParameterSpec(decoder.decode(parameters.get("iv").getAsString()));
         decrypt.init(Cipher.DECRYPT_MODE, messageKey, iv);
         // initialize message authentication
         Mac mac = Mac.getInstance("HmacSHA512");
         mac.init(macKey);
         StringBuilder sb = new StringBuilder(400); 
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
         for(java.util.Map.Entry<String, JsonElement> entry : parameters.entrySet())
         {
             sb.append(entry.getKey()); 
             sb.append(entry.getValue().getAsString()); 
             String key = new String(decrypt.doFinal(decoder.decode(entry.getKey()))); 
             if(key.contains("$"))
             {
                decryptedParameters.addProperty(key, entry.getValue().getAsBigInteger()); 
             }
             else
             {
                decryptedParameters.addProperty(key, entry.getValue().getAsString()); 
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

    /**
     * Generate a 64bit nonce to ensure message freshness
     */
    private byte[] generateNonce()
    {
        byte[] array = new byte[8];
        new Random().nextBytes(array);
        return array;    
    } 

    private IvParameterSpec generateIV()
    {
        byte[] iv = new byte[16];
        Arrays.fill(iv, (byte) 1); 
        return new IvParameterSpec(iv);
    }

    public void process(DeviceNotification notification) throws Exception
    {   
        
    }

}