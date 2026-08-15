package com.hiddentreasures.labelprinter;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Locale;

/**
 * v1.3.3 targeted fixes layered on the approved v1.3.2 app:
 * - user-adjustable label width/height
 * - printer-native black-on-white HT brand mark (no raster black block)
 * - quantity resets to 1 after a successful print
 */
public class MainActivityV133 extends MainActivity {
    private EditText widthInput;
    private EditText heightInput;
    private TextView previewSize;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        widthInput = findViewById(R.id.widthInput);
        heightInput = findViewById(R.id.heightInput);
        previewSize = findViewById(R.id.previewSize);

        TextWatcher sizeWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSizePreview();
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        widthInput.addTextChangedListener(sizeWatcher);
        heightInput.addTextChangedListener(sizeWatcher);
        updateSizePreview();

        Button printButton = findViewById(R.id.printButton);
        printButton.setOnClickListener(v -> printLabelV133());
    }

    private void updateSizePreview() {
        String width = widthInput == null ? "1.25" : widthInput.getText().toString().trim();
        String height = heightInput == null ? "1.00" : heightInput.getText().toString().trim();
        if (width.isEmpty()) width = "1.25";
        if (height.isEmpty()) height = "1.00";
        if (previewSize != null) {
            previewSize.setText("Label Size: " + width + "” × " + height + "”");
        }
    }

    private void printLabelV133() {
        TextView statusText = findViewById(R.id.statusText);
        Switch autoConnectSwitch = findViewById(R.id.autoConnectSwitch);
        EditText priceInput = findViewById(R.id.priceInput);
        TextView quantityText = findViewById(R.id.quantityText);

        BluetoothSocket activeSocket = getParentField("socket", BluetoothSocket.class);
        OutputStream activeOutput = getParentField("outputStream", OutputStream.class);

        if (activeOutput == null || activeSocket == null || !activeSocket.isConnected()) {
            Toast.makeText(this, "Printer is not connected yet.", Toast.LENGTH_SHORT).show();
            if (autoConnectSwitch != null && autoConnectSwitch.isChecked()) {
                invokeParent("loadPairedPrinters", new Class<?>[]{boolean.class}, true);
            }
            return;
        }

        String rawPrice = priceInput.getText().toString().trim();
        if (rawPrice.isEmpty()) {
            priceInput.setError("Enter a price");
            return;
        }

        final double price;
        final double widthIn;
        final double heightIn;
        final int copiesToPrint;
        try {
            price = Double.parseDouble(rawPrice.replace("$", ""));
            widthIn = Double.parseDouble(widthInput.getText().toString().trim());
            heightIn = Double.parseDouble(heightInput.getText().toString().trim());
            copiesToPrint = Math.max(1, Integer.parseInt(quantityText.getText().toString().trim()));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Check the price, label size, and quantity.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (price < 0) {
            Toast.makeText(this, "Enter a valid price.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (widthIn <= 0 || heightIn <= 0) {
            Toast.makeText(this, "Label width and height must be greater than 0.", Toast.LENGTH_SHORT).show();
            return;
        }

        String formattedPrice = "$" + new DecimalFormat("0.00").format(price);
        byte[] command = buildTsplLabelBytes(widthIn, heightIn, formattedPrice, copiesToPrint);
        statusText.setText(copiesToPrint == 1
                ? "Printing 1 label…"
                : "Printing " + copiesToPrint + " labels…");

        new Thread(() -> {
            try {
                activeOutput.write(command);
                activeOutput.flush();
                runOnUiThread(() -> {
                    statusText.setText(copiesToPrint == 1
                            ? "1 label sent to printer"
                            : copiesToPrint + " labels sent to printer");
                    resetQuantityToOne();
                });
            } catch (IOException e) {
                invokeParent("closeConnection", new Class<?>[0]);
                runOnUiThread(() -> {
                    invokeParent("updateConnectionUi", new Class<?>[]{boolean.class}, false);
                    statusText.setText("Print connection was lost. Auto Connect will retry when available.\n" + e.getMessage());
                    if (autoConnectSwitch != null && autoConnectSwitch.isChecked()) {
                        invokeParent("loadPairedPrinters", new Class<?>[]{boolean.class}, true);
                    }
                });
            }
        }).start();
    }

    /**
     * Uses printer-native TEXT and BAR commands for the HT mark instead of a BITMAP.
     * That makes the mark black ink on the white label and removes the solid-black
     * raster background seen on the POS-9220-L.
     */
    private byte[] buildTsplLabelBytes(double widthIn, double heightIn, String price, int copies) {
        double widthMm = widthIn * 25.4;
        double heightMm = heightIn * 25.4;
        int widthDots = Math.max(120, (int) Math.round(widthMm * 8.0));
        int heightDots = Math.max(120, (int) Math.round(heightMm * 8.0));

        int htMul = widthDots >= 220 ? 2 : 1;
        int htBaseCharWidth = 24;
        int htEstimatedWidth = 2 * htBaseCharWidth * htMul;
        int htX = Math.max(2, (widthDots - htEstimatedWidth) / 2);
        int htY = 5;

        int barY = htY + (12 * htMul);
        int availableSide = Math.max(0, (widthDots - htEstimatedWidth) / 2 - 14);
        int barWidth = Math.min(52, availableSide);
        int leftBarX = 8;
        int rightBarX = Math.max(8, widthDots - 8 - barWidth);

        String nameFont = widthDots >= 205 ? "2" : "1";
        int nameCharWidth = widthDots >= 205 ? 12 : 8;
        int nameWidth = "HIDDEN TREASURES".length() * nameCharWidth;
        int nameX = Math.max(2, (widthDots - nameWidth) / 2);
        int nameY = htY + (28 * htMul);

        String priceFont = widthDots >= 180 ? "3" : "2";
        int priceMul = widthDots >= 220 ? 2 : 1;
        int priceCharWidth = (priceFont.equals("3") ? 16 : 12) * priceMul;
        int priceWidth = price.length() * priceCharWidth;
        int priceX = Math.max(2, (widthDots - priceWidth) / 2);
        int minimumPriceY = nameY + 28;
        int preferredPriceY = (int) Math.round(heightDots * 0.57);
        int priceY = Math.max(minimumPriceY, preferredPriceY);
        priceY = Math.min(priceY, Math.max(minimumPriceY, heightDots - (42 * priceMul)));

        StringBuilder tspl = new StringBuilder();
        tspl.append(String.format(Locale.US,
                "SIZE %.2f mm,%.2f mm\r\n" +
                "GAP 2 mm,0 mm\r\n" +
                "DENSITY 8\r\n" +
                "DIRECTION 1\r\n" +
                "CLS\r\n",
                widthMm, heightMm));

        if (barWidth >= 10) {
            tspl.append(String.format(Locale.US, "BAR %d,%d,%d,3\r\n", leftBarX, barY, barWidth));
            tspl.append(String.format(Locale.US, "BAR %d,%d,%d,3\r\n", rightBarX, barY, barWidth));
        }

        tspl.append(String.format(Locale.US,
                "TEXT %d,%d,\"4\",0,%d,%d,\"HT\"\r\n",
                htX, htY, htMul, htMul));
        tspl.append(String.format(Locale.US,
                "TEXT %d,%d,\"%s\",0,1,1,\"HIDDEN TREASURES\"\r\n",
                nameX, nameY, nameFont));
        tspl.append(String.format(Locale.US,
                "TEXT %d,%d,\"%s\",0,%d,%d,\"%s\"\r\n",
                priceX, priceY, priceFont, priceMul, priceMul,
                price.replace("\"", "")));
        tspl.append(String.format(Locale.US, "PRINT 1,%d\r\n", Math.max(1, copies)));

        return tspl.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private void resetQuantityToOne() {
        try {
            Field quantityField = MainActivity.class.getDeclaredField("quantity");
            quantityField.setAccessible(true);
            quantityField.setInt(this, 1);
        } catch (Exception ignored) {}

        TextView quantityText = findViewById(R.id.quantityText);
        TextView quantitySummary = findViewById(R.id.quantitySummary);
        if (quantityText != null) quantityText.setText("1");
        if (quantitySummary != null) quantitySummary.setText("Total: 1 label");
    }

    private <T> T getParentField(String name, Class<T> type) {
        try {
            Field field = MainActivity.class.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(this);
            if (value == null) return null;
            return type.cast(value);
        } catch (Exception e) {
            return null;
        }
    }

    private void invokeParent(String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = MainActivity.class.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            method.invoke(this, args);
        } catch (Exception ignored) {}
    }
}
