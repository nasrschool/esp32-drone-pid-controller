package com.tp.maven;

import com.fazecast.jSerialComm.SerialPort;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import java.io.InputStream;
import java.io.OutputStream;

public class TestESP32 {

    // Replace COM_PORT with the RFCOMM channel if necessary
    private static final String URL = "btspp://F4650B592456:1";

    public static void main(String[] args) {
        try {
            SerialPort port = SerialPort.getCommPort("COM9");
            port.setBaudRate(115200);

            if (port.openPort()) {
                System.out.println("Connected!");
            } else {
                System.out.println("Failed.");
            }


            System.out.println("Connecting...");

            //StreamConnection conn = (StreamConnection) Connector.open(URL);

            InputStream in = port.getInputStream();
            OutputStream out = port.getOutputStream();

            System.out.println("Connected!");

            out.write("Hello ESP32\n".getBytes());
            out.flush();

            while (true) {
                if (in.available() > 0) {
                    int c = in.read();
                    System.out.print((char) c);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}