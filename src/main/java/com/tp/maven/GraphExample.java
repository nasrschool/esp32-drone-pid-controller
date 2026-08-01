package com.tp.maven;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

class GraphPanel extends JPanel {

    ArrayList<Float> values = new ArrayList<>();

    public void addValue(float v){

        values.add(v);

        // keep only latest 200 values
        if(values.size() > 200){
            values.remove(0);
        }

        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;

        int w = getWidth();
        int h = getHeight();

        // background
        g2.setColor(Color.BLACK);
        g2.fillRect(0,0,w,h);

        // center line
        g2.setColor(Color.GRAY);
        g2.drawLine(0,h/2,w,h/2);

        // graph
        g2.setColor(Color.GREEN);

        if(values.size() < 2)
            return;

        float max = 200f;

        for(int i = 1; i < values.size(); i++){

            int x1 = (i - 1) * w / 200;
            int x2 = i * w / 200;

            int y1 = (int)(h/2 - (values.get(i - 1) / max) * (h/2));
            int y2 = (int)(h/2 - (values.get(i) / max) * (h/2));

            g2.drawLine(x1,y1,x2,y2);
        }
    }
}

public class GraphExample {
    public static void main(String[] args) {

        JFrame frame = new JFrame();

        GraphPanel graph = new GraphPanel();

        frame.add(graph);

        frame.setSize(800,400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // fake realtime data
        new Thread(() -> {

            float t = 0;

            while(true){

                float value =
                        (float)(Math.sin(t) * 30);

                graph.addValue(value);

                t += 0.1f;

                try{
                    Thread.sleep(30);
                }
                catch(Exception _){}
            }

        }).start();
    }
}