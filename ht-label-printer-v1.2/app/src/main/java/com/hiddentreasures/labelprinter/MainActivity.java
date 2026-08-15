package com.hiddentreasures.labelprinter;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQ_BLUETOOTH_PERMISSIONS = 1001;
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private EditText priceInput;
    private EditText widthInput;
    private EditText heightInput;
    private Spinner printerSpinner;
    private TextView statusText;
    private TextView connectionBadge;
    private TextView previewPrice;
    private TextView previewSize;
    private TextView quantityText;
    private TextView quantitySummary;
    private Button connectButton;

    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private int quantity = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        priceInput = findViewById(R.id.priceInput);
        widthInput = findViewById(R.id.widthInput);
        heightInput = findViewById(R.id.heightInput);
        printerSpinner = findViewById(R.id.printerSpinner);
        statusText = findViewById(R.id.statusText);
        connectionBadge = findViewById(R.id.connectionBadge);
        previewPrice = findViewById(R.id.previewPrice);
        previewSize = findViewById(R.id.previewSize);
        quantityText = findViewById(R.id.quantityText);
        quantitySummary = findViewById(R.id.quantitySummary);

        Button refreshButton = findViewById(R.id.refreshButton);
        connectButton = findViewById(R.id.connectButton);
        Button printButton = findViewById(R.id.printButton);
        Button minusButton = findViewById(R.id.minusButton);
        Button plusButton = findViewById(R.id.plusButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        priceInput.addTextChangedListener(previewWatcher);
        widthInput.addTextChangedListener(previewWatcher);
        heightInput.addTextChangedListener(previewWatcher);

        minusButton.setOnClickListener(v -> {
            if (quantity > 1) {
                quantity--;
                updateQuantityUi();
            }
        });
        plusButton.setOnClickListener(v -> {
            if (quantity < 999) {
                quantity++;
                updateQuantityUi();
            }
        });

        refreshButton.setOnClickListener(v -> loadPairedPrinters());
        connectButton.setOnClickListener(v -> togglePrinterConnection());
        printButton.setOnClickListener(v -> printLabel());

        updateQuantityUi();
        updatePreview();
        updateConnectionUi(false);
        requestBluetoothPermissionsIfNeeded();
        loadPairedPrinters();
    }

    private void requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> needed = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (!needed.isEmpty()) {
                requestPermissions(needed.toArray(new String[0]), REQ_BLUETOOTH_PERMISSIONS);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_BLUETOOTH_PERMISSIONS) {
            if (hasBluetoothConnectPermission()) {
                loadPairedPrinters();
            } else {
                statusText.setText("Bluetooth permission is required to connect to the printer");
            }
        }
    }

    private boolean hasBluetoothConnectPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBluetoothScanPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadPairedPrinters() {
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth is not available on this device");
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionsIfNeeded();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            statusText.setText("Turn Bluetooth on, then tap Find Printers");
            return;
        }

        pairedDevices.clear();
        List<String> names = new ArrayList<>();
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : bonded) {
            pairedDevices.add(device);
            String name = safeDeviceName(device);
            names.add(name + " (" + device.getAddress() + ")");
        }

        if (names.isEmpty()) {
            names.add("No paired Bluetooth printers found");
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        printerSpinner.setAdapter(adapter);

        for (int i = 0; i < pairedDevices.size(); i++) {
            String name = safeDeviceName(pairedDevices.get(i));
            if (name.toUpperCase(Locale.US).contains("9220")) {
                printerSpinner.setSelection(i);
                break;
            }
        }
    }

    private void togglePrinterConnection() {
        BluetoothSocket active;
        synchronized (this) {
            active = socket;
        }
        if (active != null && active.isConnected()) {
            closeConnection();
            updateConnectionUi(false);
            statusText.setText("Printer disconnected");
            return;
        }
        connectSelectedPrinter();
    }

    private void connectSelectedPrinter() {
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionsIfNeeded();
            return;
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, "Turn Bluetooth on first.", Toast.LENGTH_LONG).show();
            return;
        }

        int pos = printerSpinner.getSelectedItemPosition();
        if (pairedDevices.isEmpty() || pos < 0 || pos >= pairedDevices.size()) {
            Toast.makeText(this, "Pair the POS-9220-L in Android Bluetooth settings first.", Toast.LENGTH_LONG).show();
            return;
        }

        BluetoothDevice device = pairedDevices.get(pos);
        statusText.setText("Connecting to " + safeDeviceName(device) + "…");
        connectionBadge.setText("Connecting…");
        connectionBadge.setTextColor(Color.parseColor("#1565C0"));

        new Thread(() -> {
            closeConnection();
            cancelDiscoverySafely();

            List<String> errors = new ArrayList<>();
            if (tryConnect(device, ConnectionMethod.INSECURE_SPP, errors) ||
                    tryConnect(device, ConnectionMethod.SECURE_SPP, errors) ||
                    tryConnect(device, ConnectionMethod.DIRECT_CHANNEL_1, errors)) {
                runOnUiThread(() -> {
                    updateConnectionUi(true);
                    statusText.setText("Connected and ready to print");
                });
                return;
            }

            String detail = errors.isEmpty() ? "Unknown Bluetooth error" : errors.get(errors.size() - 1);
            runOnUiThread(() -> {
                updateConnectionUi(false);
                statusText.setText("Connection failed. Confirm the printer is ON and paired, then try again.\n" + detail);
            });
        }).start();
    }

    private void updateConnectionUi(boolean connected) {
        if (connected) {
            connectionBadge.setText("Bluetooth Connected");
            connectionBadge.setTextColor(Color.parseColor("#1565C0"));
            connectButton.setText("DISCONNECT");
            connectButton.setTextColor(Color.parseColor("#E53935"));
            connectButton.setBackgroundResource(R.drawable.button_disconnect);
        } else {
            connectionBadge.setText("Not connected");
            connectionBadge.setTextColor(Color.parseColor("#777777"));
            connectButton.setText("CONNECT");
            connectButton.setTextColor(Color.parseColor("#111111"));
            connectButton.setBackgroundResource(R.drawable.button_outline);
        }
    }

    private boolean tryConnect(BluetoothDevice device, ConnectionMethod method, List<String> errors) {
        BluetoothSocket candidate = null;
        try {
            switch (method) {
                case INSECURE_SPP:
                    candidate = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
                    break;
                case SECURE_SPP:
                    candidate = device.createRfcommSocketToServiceRecord(SPP_UUID);
                    break;
                case DIRECT_CHANNEL_1:
                    Method createRfcommSocket = device.getClass().getMethod("createRfcommSocket", int.class);
                    candidate = (BluetoothSocket) createRfcommSocket.invoke(device, 1);
                    break;
            }

            candidate.connect();
            OutputStream candidateOutput = candidate.getOutputStream();
            synchronized (this) {
                socket = candidate;
                outputStream = candidateOutput;
            }
            return true;
        } catch (Exception e) {
            errors.add(method.label + ": " + cleanErrorMessage(e));
            try {
                if (candidate != null) candidate.close();
            } catch (Exception ignored) {}
            return false;
        }
    }

    private void cancelDiscoverySafely() {
        try {
            if (bluetoothAdapter != null &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || hasBluetoothScanPermission())) {
                bluetoothAdapter.cancelDiscovery();
            }
        } catch (SecurityException ignored) {}
    }

    private String cleanErrorMessage(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = cause.getClass().getSimpleName();
        }
        return message;
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null ? "Bluetooth printer" : name;
        } catch (SecurityException e) {
            return "Bluetooth printer";
        }
    }

    private void updateQuantityUi() {
        quantityText.setText(String.valueOf(quantity));
        quantitySummary.setText(quantity == 1 ? "Total: 1 label" : "Total: " + quantity + " labels");
    }

    private void updatePreview() {
        String raw = priceInput.getText().toString().trim();
        if (raw.isEmpty()) {
            previewPrice.setText("$0.00");
        } else {
            try {
                double price = Double.parseDouble(raw.replace("$", ""));
                previewPrice.setText("$" + new DecimalFormat("0.00").format(price));
            } catch (NumberFormatException e) {
                previewPrice.setText("$0.00");
            }
        }

        String width = widthInput == null ? "1.25" : widthInput.getText().toString().trim();
        String height = heightInput == null ? "1.00" : heightInput.getText().toString().trim();
        if (width.isEmpty()) width = "1.25";
        if (height.isEmpty()) height = "1.00";
        if (previewSize != null) {
            previewSize.setText("Label Size: " + width + "” × " + height + "”");
        }
    }

    private void printLabel() {
        BluetoothSocket activeSocket;
        OutputStream activeOutput;
        synchronized (this) {
            activeSocket = socket;
            activeOutput = outputStream;
        }

        if (activeOutput == null || activeSocket == null || !activeSocket.isConnected()) {
            Toast.makeText(this, "Connect to the printer first.", Toast.LENGTH_SHORT).show();
            return;
        }

        String rawPrice = priceInput.getText().toString().trim();
        if (rawPrice.isEmpty()) {
            priceInput.setError("Enter a price");
            return;
        }

        double price;
        double widthIn;
        double heightIn;
        try {
            price = Double.parseDouble(rawPrice.replace("$", ""));
            widthIn = Double.parseDouble(widthInput.getText().toString().trim());
            heightIn = Double.parseDouble(heightInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Check the price and label size.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (price < 0 || widthIn <= 0 || heightIn <= 0) {
            Toast.makeText(this, "Price and label size must be valid positive values.", Toast.LENGTH_SHORT).show();
            return;
        }

        String formattedPrice = "$" + new DecimalFormat("0.00").format(price);
        String command = buildTsplLabel(widthIn, heightIn, formattedPrice, quantity);
        statusText.setText(quantity == 1 ? "Printing 1 label…" : "Printing " + quantity + " labels…");

        new Thread(() -> {
            try {
                activeOutput.write(command.getBytes(StandardCharsets.US_ASCII));
                activeOutput.flush();
                runOnUiThread(() -> statusText.setText(quantity == 1 ? "1 label sent to printer" : quantity + " labels sent to printer"));
            } catch (IOException e) {
                closeConnection();
                runOnUiThread(() -> {
                    updateConnectionUi(false);
                    statusText.setText("Print connection was lost. Tap Connect and try again.\n" + e.getMessage());
                });
            }
        }).start();
    }

    private String buildTsplLabel(double widthIn, double heightIn, String price, int copies) {
        double widthMm = widthIn * 25.4;
        double heightMm = heightIn * 25.4;
        int widthDots = Math.max(120, (int) Math.round(widthMm * 8.0));
        int heightDots = Math.max(120, (int) Math.round(heightMm * 8.0));

        int htWidth = 48;
        int nameWidth = 192;

        String priceFont;
        int priceXMul;
        int priceYMul;
        int charWidth;
        if (price.length() <= 7) {
            priceFont = "3";
            priceXMul = 2;
            priceYMul = 2;
            charWidth = 32;
        } else {
            priceFont = "4";
            priceXMul = 1;
            priceYMul = 1;
            charWidth = 24;
        }
        int priceWidth = price.length() * charWidth;

        int htX = Math.max(0, (widthDots - htWidth) / 2);
        int nameX = Math.max(0, (widthDots - nameWidth) / 2);
        int priceX = Math.max(2, (widthDots - priceWidth) / 2);

        int htY = Math.max(2, (int) Math.round(heightDots * 0.05));
        int nameY = Math.max(34, (int) Math.round(heightDots * 0.29));
        int priceY = Math.max(72, (int) Math.round(heightDots * 0.55));

        return String.format(Locale.US,
                "SIZE %.2f mm,%.2f mm\r\n" +
                "GAP 2 mm,0 mm\r\n" +
                "DENSITY 8\r\n" +
                "DIRECTION 1\r\n" +
                "CLS\r\n" +
                "TEXT %d,%d,\"4\",0,1,1,\"HT\"\r\n" +
                "TEXT %d,%d,\"2\",0,1,1,\"HIDDEN TREASURES\"\r\n" +
                "TEXT %d,%d,\"%s\",0,%d,%d,\"%s\"\r\n" +
                "PRINT 1,%d\r\n",
                widthMm, heightMm,
                htX, htY,
                nameX, nameY,
                priceX, priceY,
                priceFont, priceXMul, priceYMul,
                price.replace("\"", ""),
                Math.max(1, copies));
    }

    private synchronized void closeConnection() {
        try {
            if (outputStream != null) outputStream.close();
        } catch (Exception ignored) {}
        try {
            if (socket != null) socket.close();
        } catch (Exception ignored) {}
        outputStream = null;
        socket = null;
    }

    @Override
    protected void onDestroy() {
        closeConnection();
        super.onDestroy();
    }

    private enum ConnectionMethod {
        INSECURE_SPP("Insecure SPP"),
        SECURE_SPP("Secure SPP"),
        DIRECT_CHANNEL_1("RFCOMM channel 1");

        final String label;

        ConnectionMethod(String label) {
            this.label = label;
        }
    }
}
