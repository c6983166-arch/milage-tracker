package com.hiddentreasures.labelprinter;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
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
    private static final double LABEL_WIDTH_IN = 1.25;
    private static final double LABEL_HEIGHT_IN = 1.00;
    private static final String PREFS = "ht_label_printer_prefs";
    private static final String PREF_LAST_PRINTER = "last_printer_address";
    private static final String PREF_AUTO_CONNECT = "auto_connect";

    private EditText priceInput;
    private Spinner printerSpinner;
    private TextView statusText;
    private TextView connectionBadge;
    private TextView previewPrice;
    private TextView quantityText;
    private TextView quantitySummary;
    private Button connectButton;
    private Switch autoConnectSwitch;

    private BluetoothAdapter bluetoothAdapter;
    private final List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private SharedPreferences prefs;
    private int quantity = 1;
    private volatile boolean connecting = false;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver bluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                if (state == BluetoothAdapter.STATE_ON) {
                    loadPairedPrinters(true);
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    closeConnection();
                    updateConnectionUi(false);
                    statusText.setText("Bluetooth is off");
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_main);

        View root = findViewById(R.id.rootContent);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        priceInput = findViewById(R.id.priceInput);
        printerSpinner = findViewById(R.id.printerSpinner);
        statusText = findViewById(R.id.statusText);
        connectionBadge = findViewById(R.id.connectionBadge);
        previewPrice = findViewById(R.id.previewPrice);
        quantityText = findViewById(R.id.quantityText);
        quantitySummary = findViewById(R.id.quantitySummary);
        autoConnectSwitch = findViewById(R.id.autoConnectSwitch);

        Button refreshButton = findViewById(R.id.refreshButton);
        connectButton = findViewById(R.id.connectButton);
        Button printButton = findViewById(R.id.printButton);
        Button minusButton = findViewById(R.id.minusButton);
        Button plusButton = findViewById(R.id.plusButton);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        autoConnectSwitch.setChecked(prefs.getBoolean(PREF_AUTO_CONNECT, true));
        autoConnectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(PREF_AUTO_CONNECT, isChecked).apply();
            if (isChecked && !isConnected()) {
                loadPairedPrinters(true);
            }
        });

        TextWatcher previewWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePreview(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        priceInput.addTextChangedListener(previewWatcher);

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

        refreshButton.setOnClickListener(v -> loadPairedPrinters(false));
        connectButton.setOnClickListener(v -> togglePrinterConnection());
        printButton.setOnClickListener(v -> printLabel());

        updateQuantityUi();
        updatePreview();
        updateConnectionUi(false);
        registerBluetoothReceiver();
        requestBluetoothPermissionsIfNeeded();
        loadPairedPrinters(true);

        priceInput.clearFocus();
    }

    private void registerBluetoothReceiver() {
        if (receiverRegistered) return;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bluetoothStateReceiver, filter);
        }
        receiverRegistered = true;
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
                loadPairedPrinters(true);
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

    private void loadPairedPrinters(boolean attemptAutoConnect) {
        if (bluetoothAdapter == null) {
            statusText.setText("Bluetooth is not available on this device");
            return;
        }
        if (!hasBluetoothConnectPermission()) {
            requestBluetoothPermissionsIfNeeded();
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            statusText.setText("Turn Bluetooth on. Auto Connect will resume when Bluetooth is available.");
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

        int selectedIndex = choosePreferredPrinterIndex();
        if (selectedIndex >= 0 && selectedIndex < pairedDevices.size()) {
            printerSpinner.setSelection(selectedIndex);
        }

        if (attemptAutoConnect && autoConnectSwitch.isChecked() && selectedIndex >= 0 && !isConnected() && !connecting) {
            connectDevice(pairedDevices.get(selectedIndex), true);
        }
    }

    private int choosePreferredPrinterIndex() {
        String savedAddress = prefs.getString(PREF_LAST_PRINTER, "");
        if (!savedAddress.isEmpty()) {
            for (int i = 0; i < pairedDevices.size(); i++) {
                if (savedAddress.equalsIgnoreCase(pairedDevices.get(i).getAddress())) {
                    return i;
                }
            }
        }
        for (int i = 0; i < pairedDevices.size(); i++) {
            String name = safeDeviceName(pairedDevices.get(i));
            if (name.toUpperCase(Locale.US).contains("9220")) {
                return i;
            }
        }
        return pairedDevices.isEmpty() ? -1 : 0;
    }

    private boolean isConnected() {
        BluetoothSocket active;
        synchronized (this) {
            active = socket;
        }
        return active != null && active.isConnected();
    }

    private void togglePrinterConnection() {
        if (isConnected()) {
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
        connectDevice(pairedDevices.get(pos), false);
    }

    private void connectDevice(BluetoothDevice device, boolean automatic) {
        if (connecting || isConnected()) return;
        connecting = true;
        String prefix = automatic ? "Auto connecting to " : "Connecting to ";
        statusText.setText(prefix + safeDeviceName(device) + "…");
        connectionBadge.setText(automatic ? "Auto Connecting…" : "Connecting…");
        connectionBadge.setTextColor(Color.parseColor("#8A6A00"));

        new Thread(() -> {
            closeConnection();
            cancelDiscoverySafely();

            List<String> errors = new ArrayList<>();
            boolean connected = tryConnect(device, ConnectionMethod.INSECURE_SPP, errors) ||
                    tryConnect(device, ConnectionMethod.SECURE_SPP, errors) ||
                    tryConnect(device, ConnectionMethod.DIRECT_CHANNEL_1, errors);

            connecting = false;
            if (connected) {
                prefs.edit().putString(PREF_LAST_PRINTER, device.getAddress()).apply();
                runOnUiThread(() -> {
                    updateConnectionUi(true);
                    statusText.setText(autoConnectSwitch.isChecked()
                            ? "Auto Connect is ON • Connected and ready to print"
                            : "Connected and ready to print");
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
            connectionBadge.setText("Connected and ready");
            connectionBadge.setTextColor(Color.parseColor("#0A8F2D"));
            connectButton.setText("DISCONNECT");
            connectButton.setTextColor(Color.parseColor("#E53935"));
            connectButton.setBackgroundResource(R.drawable.button_disconnect);
        } else {
            connectionBadge.setText("Not connected");
            connectionBadge.setTextColor(Color.parseColor("#666666"));
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
    }

    private void printLabel() {
        BluetoothSocket activeSocket;
        OutputStream activeOutput;
        synchronized (this) {
            activeSocket = socket;
            activeOutput = outputStream;
        }

        if (activeOutput == null || activeSocket == null || !activeSocket.isConnected()) {
            Toast.makeText(this, "Printer is not connected yet.", Toast.LENGTH_SHORT).show();
            if (autoConnectSwitch.isChecked()) {
                loadPairedPrinters(true);
            }
            return;
        }

        String rawPrice = priceInput.getText().toString().trim();
        if (rawPrice.isEmpty()) {
            priceInput.setError("Enter a price");
            return;
        }

        double price;
        try {
            price = Double.parseDouble(rawPrice.replace("$", ""));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Check the price.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (price < 0) {
            Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show();
            return;
        }

        String formattedPrice = "$" + new DecimalFormat("0.00").format(price);
        byte[] command = buildTsplLabelBytes(LABEL_WIDTH_IN, LABEL_HEIGHT_IN, formattedPrice, quantity);
        statusText.setText(quantity == 1 ? "Printing 1 label…" : "Printing " + quantity + " labels…");

        new Thread(() -> {
            try {
                activeOutput.write(command);
                activeOutput.flush();
                runOnUiThread(() -> statusText.setText(quantity == 1
                        ? "1 label sent to printer"
                        : quantity + " labels sent to printer"));
            } catch (IOException e) {
                closeConnection();
                runOnUiThread(() -> {
                    updateConnectionUi(false);
                    statusText.setText("Print connection was lost. Auto Connect will retry when available.\n" + e.getMessage());
                    if (autoConnectSwitch.isChecked()) {
                        loadPairedPrinters(true);
                    }
                });
            }
        }).start();
    }

    private byte[] buildTsplLabelBytes(double widthIn, double heightIn, String price, int copies) {
        try {
            double widthMm = widthIn * 25.4;
            double heightMm = heightIn * 25.4;
            int widthDots = Math.max(120, (int) Math.round(widthMm * 8.0));
            int heightDots = Math.max(120, (int) Math.round(heightMm * 8.0));

            Bitmap logo = createMonochromeBrandLogo(Math.min(widthDots - 18, 224), 76);
            byte[] logoData = bitmapToTsplMono(logo);
            int logoBytesPerRow = (logo.getWidth() + 7) / 8;
            int logoX = Math.max(2, (widthDots - logo.getWidth()) / 2);
            int logoY = 5;

            String priceFont = price.length() <= 7 ? "3" : "2";
            int priceMul = price.length() <= 7 ? 2 : 2;
            int estimatedCharWidth = price.length() <= 7 ? 32 : 24;
            int priceWidth = price.length() * estimatedCharWidth;
            int priceX = Math.max(2, (widthDots - priceWidth) / 2);
            int priceY = Math.min(heightDots - 62, 112);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeAscii(out, String.format(Locale.US,
                    "SIZE %.2f mm,%.2f mm\r\n" +
                    "GAP 2 mm,0 mm\r\n" +
                    "DENSITY 8\r\n" +
                    "DIRECTION 1\r\n" +
                    "CLS\r\n",
                    widthMm, heightMm));

            writeAscii(out, String.format(Locale.US,
                    "BITMAP %d,%d,%d,%d,0,",
                    logoX, logoY, logoBytesPerRow, logo.getHeight()));
            out.write(logoData);
            writeAscii(out, "\r\n");

            writeAscii(out, String.format(Locale.US,
                    "TEXT %d,%d,\"%s\",0,%d,%d,\"%s\"\r\n" +
                    "PRINT 1,%d\r\n",
                    priceX, priceY, priceFont, priceMul, priceMul,
                    price.replace("\"", ""), Math.max(1, copies)));
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to build label", e);
        }
    }

    private Bitmap createMonochromeBrandLogo(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.CENTER);

        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextSize(43f);
        Paint.FontMetrics htMetrics = paint.getFontMetrics();
        float htBaseline = 5f - htMetrics.top;
        canvas.drawText("HT", width / 2f, htBaseline, paint);

        float barY = 28f;
        paint.setStrokeWidth(4f);
        canvas.drawLine(10f, barY, width * 0.27f, barY, paint);
        canvas.drawLine(width * 0.73f, barY, width - 10f, barY, paint);

        paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        paint.setTextSize(15f);
        Paint.FontMetrics nameMetrics = paint.getFontMetrics();
        float nameBaseline = height - 7f - nameMetrics.bottom;
        canvas.drawText("HIDDEN TREASURES", width / 2f, nameBaseline, paint);

        return bitmap;
    }

    private byte[] bitmapToTsplMono(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int bytesPerRow = (width + 7) / 8;
        byte[] data = new byte[bytesPerRow * height];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = bitmap.getPixel(x, y);
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);
                int luminance = (r * 299 + g * 587 + b * 114) / 1000;
                if (luminance < 160) {
                    int index = y * bytesPerRow + (x / 8);
                    data[index] |= (byte) (0x80 >> (x % 8));
                }
            }
        }
        return data;
    }

    private void writeAscii(ByteArrayOutputStream out, String value) throws IOException {
        out.write(value.getBytes(StandardCharsets.US_ASCII));
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
        if (receiverRegistered) {
            try {
                unregisterReceiver(bluetoothStateReceiver);
            } catch (Exception ignored) {}
            receiverRegistered = false;
        }
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
