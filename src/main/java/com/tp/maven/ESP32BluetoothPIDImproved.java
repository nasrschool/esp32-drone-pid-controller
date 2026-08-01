package com.tp.maven;

import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Drone PID / thrust controller.
 *
 * Wire format sent to the ESP32 (little-endian floats), matching the firmware struct:
 *
 *   struct Data {
 *       float P;
 *       float I;
 *       float D;
 *       float thrust;        // "base" value, counters gravity
 *       float desiredAngle;
 *   };
 *
 * Controls:
 *   - P / I / D / desired angle are typed into the GUI fields and applied with "Apply PID".
 *   - W increases thrust (hold it down, OS key-repeat ramps it up smoothly).
 *   - S decreases thrust.
 *   - N toggles the kill switch. While engaged, thrust is ramped DOWN to 0 at a steady,
 *     tunable rate on every tick instead of being cut instantly, so the drone settles
 *     down instead of free-falling. Press N again to disengage and resume manual control.
 *
 * A background thread streams the current state to the ESP32 at a fixed rate (20 Hz)
 * regardless of what the user is doing right now - this is what actually drives the
 * kill-switch ramp, and it also fixes the original version, which only sent a packet
 * when you typed a line and hit Enter (not real-time at all).
 *
 * Dependency note: javax.microedition.io (JSR-82) needs a real implementation on the
 * classpath, e.g. BlueCove (net.sf.bluecove:bluecove). BlueCove is old and unmaintained
 * and mostly only works on Windows. If it gives you grief, an often easier route for an
 * already-paired ESP32 SPP device is to talk to it as a plain virtual serial port
 * (a COM* port on Windows, /dev/rfcomm0 on Linux) using a library like jSerialComm
 * instead of JSR-82 - same idea, much less fragile stack underneath.
 *
 * Safety note: this app can only help while the Bluetooth link is actually up. Add a
 * watchdog on the ESP32 side that zeroes thrust if no packet has arrived for ~300-500ms,
 * so a dropped connection doesn't leave the last thrust value "stuck" on.
 */
public class ESP32BluetoothPIDImproved {

    public static void main(String[] args) {
        String defaultAddress = args.length > 0 ? args[0] : "F4650B592456";

        DroneState state = new DroneState();
        BluetoothLink link = new BluetoothLink();

        SwingUtilities.invokeLater(() -> {
            ControlFrame frame = new ControlFrame(state, link, defaultAddress);
            frame.setVisible(true);
        });
    }
}

/**
 * Thread-safe holder for everything that gets sent to the drone, plus the tuning knobs.
 * P/I/D/desiredAngle have a single writer (the EDT), so plain volatile is enough for them.
 * Thrust has two writers - the EDT (W/S) and the sender thread (kill-switch ramp) - so its
 * mutations go through a lock to avoid lost updates between the two.
 */
class DroneState {

    private volatile float p = 0f;
    private volatile float i = 0f;
    private volatile float d = 0f;
    private volatile float desiredAngle = 0f;

    private final Object thrustLock = new Object();
    private volatile float thrust = 0f;

    private volatile boolean killSwitchActive = false;

    // Tuning knobs, editable live from the GUI; sensible defaults below.
    private volatile float maxThrust = 100f;
    private volatile float thrustStep = 0.5f;
    private volatile float killSwitchDecayPerSecond = 15f;

    float getP() { return p; }
    void setP(float v) { p = v; }

    float getI() { return i; }
    void setI(float v) { i = v; }

    float getD() { return d; }
    void setD(float v) { d = v; }

    float getDesiredAngle() { return desiredAngle; }
    void setDesiredAngle(float v) { desiredAngle = v; }

    float getThrust() {
        synchronized (thrustLock) {
            return thrust;
        }
    }

    void increaseThrust() {
        synchronized (thrustLock) {
            thrust = Math.min(maxThrust, thrust + thrustStep);
        }
    }

    void decreaseThrust() {
        synchronized (thrustLock) {
            thrust = Math.max(0f, thrust - thrustStep);
        }
    }

    /** Called every tick by the sender thread while the kill switch is engaged. */
    void decayThrust(double dtSeconds) {
        synchronized (thrustLock) {
            float drop = (float) (killSwitchDecayPerSecond * dtSeconds);
            thrust = Math.max(0f, thrust - drop);
        }
    }

    void setThrustImmediate(float v) {
        synchronized (thrustLock) {
            thrust = Math.max(0f, Math.min(maxThrust, v));
        }
    }

    boolean isKillSwitchActive() { return killSwitchActive; }
    void toggleKillSwitch() { killSwitchActive = !killSwitchActive; }
    void setKillSwitchActive(boolean v) { killSwitchActive = v; }

    float getMaxThrust() { return maxThrust; }
    void setMaxThrust(float v) { maxThrust = Math.max(0f, v); }

    float getThrustStep() { return thrustStep; }
    void setThrustStep(float v) { thrustStep = Math.max(0f, v); }

    float getKillSwitchDecayPerSecond() { return killSwitchDecayPerSecond; }
    void setKillSwitchDecayPerSecond(float v) { killSwitchDecayPerSecond = Math.max(0f, v); }
}

/** Owns the JSR-82 connection and the wire format. */
class BluetoothLink {

    private volatile StreamConnection connection;
    private volatile OutputStream out;
    private volatile InputStream in;

    synchronized void connect(String address) throws IOException {
        close(); // tidy up any previous connection first
        String url = "btspp://" + address + ":1";
        connection = (StreamConnection) Connector.open(url);
        out = connection.openOutputStream();
        in = connection.openInputStream();
    }

    boolean isConnected() {
        return connection != null && out != null;
    }

    /** Packs P, I, D, thrust, desiredAngle as little-endian floats and writes them. */
    synchronized void sendState(DroneState state) throws IOException {
        if (!isConnected()) {
            throw new IOException("Not connected");
        }
        ByteBuffer buffer = ByteBuffer.allocate(4 * 5);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(state.getP());
        buffer.putFloat(state.getI());
        buffer.putFloat(state.getD());
        buffer.putFloat(state.getThrust());
        buffer.putFloat(state.getDesiredAngle());
        out.write(buffer.array());
        out.flush();
    }

    synchronized void close() {
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (connection != null) connection.close(); } catch (IOException ignored) {}
        out = null;
        in = null;
        connection = null;
    }
}

/** GUI + key bindings + the background streaming/decay loop. */
class ControlFrame extends JFrame {

    private final DroneState state;
    private final BluetoothLink link;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private JTextField addressField, pField, iField, dField, angleField;
    private JTextField maxThrustField, thrustStepField, decayRateField;
    private JLabel statusLabel, liveValuesLabel, killSwitchLabel;
    private JButton connectButton;

    ControlFrame(DroneState state, BluetoothLink link, String defaultAddress) {
        super("ESP32 Drone PID Controller");
        this.state = state;
        this.link = link;

        buildUI(defaultAddress);
        setupKeyBindings();
        startBackgroundLoop();

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private void buildUI(String defaultAddress) {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel connectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionPanel.add(new JLabel("Bluetooth address:"));
        addressField = new JTextField(defaultAddress, 14);
        connectionPanel.add(addressField);
        connectButton = new JButton("Connect");
        connectButton.addActionListener(e -> attemptConnect());
        connectionPanel.add(connectButton);
        root.add(connectionPanel);

        JPanel pidPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pField = new JTextField("0", 5);
        iField = new JTextField("0", 5);
        dField = new JTextField("0", 5);
        angleField = new JTextField("0", 5);
        pidPanel.add(new JLabel("P:")); pidPanel.add(pField);
        pidPanel.add(new JLabel("I:")); pidPanel.add(iField);
        pidPanel.add(new JLabel("D:")); pidPanel.add(dField);
        pidPanel.add(new JLabel("Desired angle:")); pidPanel.add(angleField);
        JButton applyPidButton = new JButton("Apply PID");
        applyPidButton.addActionListener(e -> applyPidFields());
        pidPanel.add(applyPidButton);
        root.add(pidPanel);

        JPanel tuningPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        maxThrustField = new JTextField(String.valueOf(state.getMaxThrust()), 5);
        thrustStepField = new JTextField(String.valueOf(state.getThrustStep()), 5);
        decayRateField = new JTextField(String.valueOf(state.getKillSwitchDecayPerSecond()), 5);
        tuningPanel.add(new JLabel("Max thrust:")); tuningPanel.add(maxThrustField);
        tuningPanel.add(new JLabel("Thrust step/press:")); tuningPanel.add(thrustStepField);
        tuningPanel.add(new JLabel("Kill-switch decay (units/sec):")); tuningPanel.add(decayRateField);
        JButton applyTuningButton = new JButton("Apply tuning");
        applyTuningButton.addActionListener(e -> applyTuningFields());
        tuningPanel.add(applyTuningButton);
        root.add(tuningPanel);

        statusLabel = new JLabel("Not connected.");
        liveValuesLabel = new JLabel(" ");
        killSwitchLabel = new JLabel(" ");
        killSwitchLabel.setForeground(new Color(0, 130, 0));
        root.add(statusLabel);
        root.add(liveValuesLabel);
        root.add(killSwitchLabel);

        JLabel helpLabel = new JLabel(
                "<html>W = increase thrust &nbsp; S = decrease thrust &nbsp; "
                        + "N = toggle kill switch (ramps thrust to 0 instead of cutting it)</html>");
        root.add(Box.createVerticalStrut(8));
        root.add(helpLabel);

        setContentPane(root);
    }

    private void setupKeyBindings() {
        JComponent rootPane = getRootPane();
        InputMap im = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = rootPane.getActionMap();

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, 0), "increaseThrust");
        am.put("increaseThrust", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!state.isKillSwitchActive()) {
                    state.increaseThrust();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0), "decreaseThrust");
        am.put("decreaseThrust", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!state.isKillSwitchActive()) {
                    state.decreaseThrust();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0), "toggleKillSwitch");
        am.put("toggleKillSwitch", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                state.toggleKillSwitch();
            }
        });
    }

    private void applyPidFields() {
        try {
            float p = Float.parseFloat(pField.getText().trim());
            float i = Float.parseFloat(iField.getText().trim());
            float d = Float.parseFloat(dField.getText().trim());
            float angle = Float.parseFloat(angleField.getText().trim());
            state.setP(p);
            state.setI(i);
            state.setD(d);
            state.setDesiredAngle(angle);
            statusLabel.setText("PID values applied.");
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid PID input - please enter plain numbers.");
        }
    }

    private void applyTuningFields() {
        try {
            state.setMaxThrust(Float.parseFloat(maxThrustField.getText().trim()));
            state.setThrustStep(Float.parseFloat(thrustStepField.getText().trim()));
            state.setKillSwitchDecayPerSecond(Float.parseFloat(decayRateField.getText().trim()));
            statusLabel.setText("Tuning values applied.");
        } catch (NumberFormatException ex) {
            statusLabel.setText("Invalid tuning input - please enter plain numbers.");
        }
    }

    private void attemptConnect() {
        if (!connecting.compareAndSet(false, true)) {
            return; // a connection attempt is already in flight
        }
        String address = addressField.getText().trim();
        connectButton.setEnabled(false);
        statusLabel.setText("Connecting to " + address + "...");

        Thread connectThread = new Thread(() -> {
            try {
                link.connect(address);
                SwingUtilities.invokeLater(() -> statusLabel.setText("Connected to " + address));
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Connection failed: " + ex.getMessage()));
            } finally {
                connecting.set(false);
                SwingUtilities.invokeLater(() -> connectButton.setEnabled(true));
            }
        }, "bt-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    /** Background heartbeat: drives the kill-switch ramp and streams state at a fixed rate. */
    private void startBackgroundLoop() {
        Thread sender = new Thread(() -> {
            long lastTick = System.nanoTime();
            while (running.get()) {
                long now = System.nanoTime();
                double dt = (now - lastTick) / 1_000_000_000.0;
                lastTick = now;

                if (state.isKillSwitchActive()) {
                    state.decayThrust(dt);
                }

                if (link.isConnected()) {
                    try {
                        link.sendState(state);
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() ->
                                statusLabel.setText("Lost connection: " + ex.getMessage()));
                    }
                }

                refreshLabels();

                try {
                    Thread.sleep(50); // 20 Hz
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "drone-sender");
        sender.setDaemon(true);
        sender.start();
    }

    private void refreshLabels() {
        boolean killActive = state.isKillSwitchActive();
        String values = String.format(
                "P=%.2f  I=%.2f  D=%.2f  Thrust=%.2f  Angle=%.2f",
                state.getP(), state.getI(), state.getD(), state.getThrust(), state.getDesiredAngle());
        String killText = killActive
                ? "KILL SWITCH ENGAGED - descending (press N to disengage)"
                : "Kill switch off.";
        SwingUtilities.invokeLater(() -> {
            liveValuesLabel.setText(values);
            killSwitchLabel.setText(killText);
            killSwitchLabel.setForeground(killActive ? new Color(180, 0, 0) : new Color(0, 130, 0));
        });
    }

    private void shutdown() {
        running.set(false);
        state.setKillSwitchActive(false);
        state.setThrustImmediate(0f);
        try {
            if (link.isConnected()) {
                link.sendState(state);
            }
        } catch (IOException ignored) {
        } finally {
            link.close();
            dispose();
            System.exit(0);
        }
    }
}