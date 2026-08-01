package com.tp.maven;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class BluetoothUI2 {

    public static void main(String[] args) throws Exception{
        JFrame frame = new JFrame();
        frame.setResizable(false);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000,1000);
        frame.setLayout(new BorderLayout());

        JPanel upperPanel = new JPanel();


        JPanel lowerPanel = new JPanel();
        GraphPanel evenLowerPanel = new GraphPanel();

        upperPanel.setLayout(new GridLayout(6,2));
        lowerPanel.setLayout(new GridLayout());


        upperPanel.setBackground(Color.gray);
        lowerPanel.setBackground(Color.lightGray);

        upperPanel.setPreferredSize(new Dimension(100,300));
        lowerPanel.setPreferredSize(new Dimension(100,300));
        evenLowerPanel.setPreferredSize(new Dimension(100,300));


        JLabel pLabel = new JLabel();
        JTextField p = new JTextField();
        p.setText("0");
        pLabel.setText("enter the P value:");
        upperPanel.add(pLabel);
        upperPanel.add(p);

        JLabel iLabel = new JLabel();
        JTextField i = new JTextField();
        i.setText("0");
        iLabel.setText("enter the i value:");
        upperPanel.add(iLabel);
        upperPanel.add(i);

        JLabel dLabel = new JLabel();
        JTextField d = new JTextField();
        d.setText("0");
        dLabel.setText("enter the d value:");
        upperPanel.add(dLabel);
        upperPanel.add(d);

        JLabel baseLabel = new JLabel();
        JTextField base = new JTextField();
        base.setText("0");
        baseLabel.setText("enter the base value: ");
        upperPanel.add(baseLabel);
        upperPanel.add(base);

        JLabel angleLabel = new JLabel();
        JTextField angle = new JTextField();
        angle.setText("0");
        angleLabel.setText("enter the angle value: ");
        upperPanel.add(angleLabel);
        upperPanel.add(angle);

        JButton button = new JButton();
        button.setText("send");
        upperPanel.add(button);

        JButton button2 = new JButton();
        button2.setText("disarm");
        upperPanel.add(button2);


        //lower part

        JLabel label1 = new JLabel();
        JLabel label2 = new JLabel();
        JLabel label3 = new JLabel();
        JLabel label4 = new JLabel();
        JLabel labelAngle = new JLabel();
        JLabel labelPID = new JLabel();

        lowerPanel.add(label1);
        lowerPanel.add(label2);
        lowerPanel.add(label3);
        lowerPanel.add(label4);
        lowerPanel.add(labelAngle);
        lowerPanel.add(labelPID);


        // bluetooth

        String address = "441D64F6E15E"; // ESP32 MAC address
        String url = "btspp://" + address + ":1"; // channel 1 usually
        System.out.println("1");
        StreamConnection connection = (StreamConnection) Connector.open(url);
        System.out.println("2");

        InputStream in = connection.openInputStream();
        OutputStream out = connection.openOutputStream();
        System.out.println("3");

        button.addActionListener((event)->{
            float[] values = new float[5];
            values[0] = Float.parseFloat(p.getText());
            values[1] = Float.parseFloat(i.getText());
            values[2] = Float.parseFloat(d.getText());
            values[3] = Float.parseFloat(base.getText());
            values[4] = Float.parseFloat(angle.getText());

            ByteBuffer buffer = ByteBuffer.allocate(4 * 5);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            for(int j = 0; j < values.length;j++){
                buffer.put(convert(values[j]));
                System.out.print(Arrays.toString(convert(values[j])));
            }
            System.out.println(buffer.array());
            try {
                out.write(buffer.array());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        button2.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                float[] values = new float[5];
                values[0] = Float.parseFloat(p.getText());
                values[1] = Float.parseFloat(i.getText());
                values[2] = Float.parseFloat(d.getText());
                values[3] = 1100;
                values[4] = 0;
                

                ByteBuffer buffer = ByteBuffer.allocate(4 * 5);
                buffer.order(ByteOrder.LITTLE_ENDIAN);

                for(int j = 0; j < values.length;j++){
                    buffer.put(convert(values[j]));
                    System.out.print(Arrays.toString(convert(values[j])));
                }
                System.out.println(buffer.array());
                try {
                    out.write(buffer.array());
                } catch (IOException e2) {
                    throw new RuntimeException(e2);
                }
            }
        });


        // === Manual base control (W/S) and auto-landing toggle (N) ===
        // Pure additions: nothing above or below this block is touched.
        // W/S nudge the base (thrust) value and immediately send the same 20-byte
        // P/I/D/base/angle packet the "send" button already sends.
        // N toggles an auto-landing routine that overrides W/S and manual "send"
        // presses, ramping the base value down gradually instead of cutting it
        // instantly, so the drone settles down rather than dropping.

        final float BASE_STEP = 10f;          // how much w/s changes the base value per key press
        final float BASE_MIN = 1100f;         // matches the ESP32 idle threshold (Ip/Ir reset below this)
        final float BASE_MAX = 1900f;         // matches the firmware's motor microsecond clamp

        final float LANDING_STEP = 5f;        // how much the base value drops per landing tick
        final int LANDING_INTERVAL_MS = 150;  // how often the base value drops during auto-landing

        final java.util.Set<JTextField> controlFields = new java.util.HashSet<>(Arrays.asList(p, i, d, base, angle));
        final boolean[] landingActive = {false};
        final javax.swing.Timer[] landingTimer = {null};

        // sends the current P/I/D/base/angle fields, identical packet layout to the existing "send" button
        Runnable sendCurrentPacket = () -> {
            try {
                float[] values = new float[5];
                values[0] = Float.parseFloat(p.getText());
                values[1] = Float.parseFloat(i.getText());
                values[2] = Float.parseFloat(d.getText());
                values[3] = Float.parseFloat(base.getText());
                values[4] = Float.parseFloat(angle.getText());

                ByteBuffer buffer = ByteBuffer.allocate(4 * 5);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                for (float v : values) {
                    buffer.put(convert(v));
                }
                out.write(buffer.array());
            } catch (NumberFormatException nfe) {
                System.out.println("Skipped send: a control field does not contain a valid number.");
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        };

        Runnable stopLanding = () -> {
            landingActive[0] = false;
            if (landingTimer[0] != null) {
                landingTimer[0].stop();
            }
        };

        Runnable startLanding = () -> {
            if (landingTimer[0] != null) {
                landingTimer[0].stop();
            }
            landingActive[0] = true;
            landingTimer[0] = new javax.swing.Timer(LANDING_INTERVAL_MS, ev -> {
                try {
                    float currentBase = Float.parseFloat(base.getText());
                    float nextBase = currentBase - LANDING_STEP;
                    boolean reachedFloor = nextBase <= BASE_MIN;
                    if (reachedFloor) {
                        nextBase = BASE_MIN;
                    }
                    base.setText(String.valueOf(nextBase));
                    sendCurrentPacket.run();
                    if (reachedFloor) {
                        stopLanding.run();
                    }
                } catch (NumberFormatException nfe) {
                    stopLanding.run();
                }
            });
            landingTimer[0].start();
        };

        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher((e) -> {
            if (e.getID() != KeyEvent.KEY_PRESSED) {
                return false;
            }
            // don't hijack keys while the user is typing into one of the text fields
            if (controlFields.contains(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner())) {
                return false;
            }

            char key = Character.toLowerCase(e.getKeyChar());

            if (key == 'n') {
                if (landingActive[0]) {
                    stopLanding.run();
                } else {
                    startLanding.run();
                }
                return true;
            }

            if (landingActive[0]) {
                // auto-landing overrides manual base changes until toggled off again
                return false;
            }

            if (key == 'w') {
                try {
                    float val = Math.min(BASE_MAX, Float.parseFloat(base.getText()) + BASE_STEP);
                    base.setText(String.valueOf(val));
                    sendCurrentPacket.run();
                } catch (NumberFormatException nfe) {
                    System.out.println("Base field does not contain a valid number.");
                }
                return true;
            }

            if (key == 's') {
                try {
                    float val = Math.max(BASE_MIN, Float.parseFloat(base.getText()) - BASE_STEP);
                    base.setText(String.valueOf(val));
                    sendCurrentPacket.run();
                } catch (NumberFormatException nfe) {
                    System.out.println("Base field does not contain a valid number.");
                }
                return true;
            }

            return false;
        });


        byte[] bytes = new byte[4 * 6];
        (new Thread(()->{
            try {
                while(true){
                    while(in.read() != 0xAA){
                    }
                    in.read(bytes);

                    ByteBuffer buffer = ByteBuffer.wrap(bytes);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);

                    label1.setText("M1: " + buffer.getFloat() + "\t");
                    label2.setText("M2: " + buffer.getFloat() +"\t");
                    label3.setText("M3: " + buffer.getFloat() + "\t");
                    label4.setText("M4: " + buffer.getFloat()+"\t");
                    //System.out.println( buffer.getFloat());
                    float PID = buffer.getFloat();
                    evenLowerPanel.addValue(PID);
                    labelAngle.setText("angle: " + PID +"\t");
                    labelPID.setText("PID: " + buffer.getFloat()+"\t");

                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        })).start();


        frame.add(upperPanel, BorderLayout.NORTH);
        frame.add(lowerPanel, BorderLayout.CENTER);
        frame.add(evenLowerPanel,BorderLayout.SOUTH);
        frame.setVisible(true);
    }

    public static byte[] convert(float n) {
        int intBits =  Float.floatToIntBits(n);
        return new byte[] {
                (byte) intBits,
                (byte)(intBits >> 8),
                (byte)(intBits >> 16),
                (byte)(intBits >> 24)
        };
    }
}