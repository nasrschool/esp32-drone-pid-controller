package com.tp.maven;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class BluetoothUI {

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
