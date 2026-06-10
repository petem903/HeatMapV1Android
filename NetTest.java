import java.net.*;
import java.io.*;
import javax.net.ssl.*;

public class NetTest {
    public static void main(String[] args) {
        String url = "https://dl.google.com/dl/android/maven2/com/android/application/com.android.application.gradle.plugin/8.2.2/com.android.application.gradle.plugin-8.2.2.pom";
        try {
            System.out.println("preferIPv4=" + System.getProperty("java.net.preferIPv4Stack"));
            System.out.println("https.protocols=" + System.getProperty("https.protocols"));
            HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.connect();
            System.out.println("HTTP " + c.getResponseCode() + " proto=" + c.getCipherSuite());
            c.disconnect();
        } catch (Exception e) {
            System.out.println("FAILED: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}
