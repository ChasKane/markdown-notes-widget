package net.tapi.handynotes;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class WidgetOptionsActivity extends Activity {
    private Integer widgetId;
    private CheckBox showTitleCheckbox;
    private String selectedBgColor = "#1e1e2e";
    private String selectedPaddingColor = "#1e1e2e";
    private TextView bgColorPreview;
    private TextView paddingColorPreview;
    
    // Predefined color options
    private static final String[] COLOR_OPTIONS = {
        "#1e1e2e", // Default dark
        "#16161e", // Darker
        "#2d2d3f", // Border color
        "#000000", // Black
        "#ffffff", // White
        "#1a1a2e", // Dark blue
        "#16213e", // Darker blue
        "#0f3460", // Navy
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            widgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, 
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        
        if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, "Invalid widget ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Load existing settings
        NotesDbAdapter db = new NotesDbAdapter(this);
        db.open();
        boolean showTitle = db.getShowTitle(widgetId);
        selectedBgColor = db.getBgColor(widgetId);
        selectedPaddingColor = db.getPaddingColor(widgetId);
        db.close();
        
        setContentView(createLayout(showTitle));
    }
    
    private View createLayout(boolean showTitle) {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(32, 32, 32, 32);
        mainLayout.setBackgroundColor(Color.parseColor("#1e1e2e"));
        
        // Title
        TextView titleText = new TextView(this);
        titleText.setText("Widget Options");
        titleText.setTextColor(Color.parseColor("#dcddde"));
        titleText.setTextSize(20);
        titleText.setPadding(0, 0, 0, 24);
        mainLayout.addView(titleText);
        
        // Show title checkbox
        showTitleCheckbox = new CheckBox(this);
        showTitleCheckbox.setText("Show note title on widget");
        showTitleCheckbox.setTextColor(Color.parseColor("#dcddde"));
        showTitleCheckbox.setChecked(showTitle);
        showTitleCheckbox.setPadding(0, 0, 0, 32);
        mainLayout.addView(showTitleCheckbox);
        
        // Background color section
        TextView bgLabel = new TextView(this);
        bgLabel.setText("Background Color");
        bgLabel.setTextColor(Color.parseColor("#dcddde"));
        bgLabel.setTextSize(16);
        bgLabel.setPadding(0, 0, 0, 8);
        mainLayout.addView(bgLabel);
        
        bgColorPreview = new TextView(this);
        bgColorPreview.setText("Selected: " + selectedBgColor);
        bgColorPreview.setTextColor(Color.parseColor("#9ca0a4"));
        bgColorPreview.setPadding(0, 0, 0, 16);
        mainLayout.addView(bgColorPreview);
        
        LinearLayout bgColorLayout = createColorSelector(selectedBgColor, new ColorSelectionListener() {
            @Override
            public void onColorSelected(String color) {
                selectedBgColor = color;
                bgColorPreview.setText("Selected: " + color);
                bgColorPreview.setTextColor(Color.parseColor(color));
            }
        });
        mainLayout.addView(bgColorLayout);
        
        // Padding color section
        TextView paddingLabel = new TextView(this);
        paddingLabel.setText("Padding Color");
        paddingLabel.setTextColor(Color.parseColor("#dcddde"));
        paddingLabel.setTextSize(16);
        paddingLabel.setPadding(0, 24, 0, 8);
        mainLayout.addView(paddingLabel);
        
        paddingColorPreview = new TextView(this);
        paddingColorPreview.setText("Selected: " + selectedPaddingColor);
        paddingColorPreview.setTextColor(Color.parseColor("#9ca0a4"));
        paddingColorPreview.setPadding(0, 0, 0, 16);
        mainLayout.addView(paddingColorPreview);
        
        LinearLayout paddingColorLayout = createColorSelector(selectedPaddingColor, new ColorSelectionListener() {
            @Override
            public void onColorSelected(String color) {
                selectedPaddingColor = color;
                paddingColorPreview.setText("Selected: " + color);
                paddingColorPreview.setTextColor(Color.parseColor(color));
            }
        });
        mainLayout.addView(paddingColorLayout);
        
        // Save button
        Button saveButton = new Button(this);
        saveButton.setText("Save");
        saveButton.setPadding(16, 16, 16, 16);
        saveButton.setTextSize(16);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });
        saveButton.setPadding(0, 32, 0, 0);
        mainLayout.addView(saveButton);
        
        return mainLayout;
    }
    
    private android.graphics.drawable.Drawable createColorDrawableWithBorder(String color, String borderColor) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor(color));
        drawable.setStroke(4, Color.parseColor(borderColor));
        return drawable;
    }
    
    private LinearLayout createColorSelector(String selectedColor, ColorSelectionListener listener) {
        LinearLayout colorLayout = new LinearLayout(this);
        colorLayout.setOrientation(LinearLayout.HORIZONTAL);
        
        for (final String color : COLOR_OPTIONS) {
            View colorView = new View(this);
            int size = 60;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(0, 0, 16, 0);
            colorView.setLayoutParams(params);
            if (color.equals(selectedColor)) {
                colorView.setBackground(createColorDrawableWithBorder(color, "#7c6fef"));
            } else {
                colorView.setBackgroundColor(Color.parseColor(color));
            }
            
            colorView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onColorSelected(color);
                    // Update borders
                    updateColorSelection(colorLayout, color);
                }
            });
            
            colorLayout.addView(colorView);
        }
        
        return colorLayout;
    }
    
    private void updateColorSelection(LinearLayout colorLayout, String selectedColor) {
        for (int i = 0; i < colorLayout.getChildCount(); i++) {
            View child = colorLayout.getChildAt(i);
            String color = COLOR_OPTIONS[i];
            // Add a border effect by wrapping in a colored frame
            if (color.equals(selectedColor)) {
                // Selected: add accent color border
                child.setBackground(createColorDrawableWithBorder(color, "#7c6fef"));
            } else {
                // Not selected: just the color
                child.setBackgroundColor(Color.parseColor(color));
            }
        }
    }
    
    private interface ColorSelectionListener {
        void onColorSelected(String color);
    }
    
    private void saveSettings() {
        NotesDbAdapter db = new NotesDbAdapter(this);
        db.open();
        
        // Check if widget exists in database
        String fileUri = db.getFileUri(widgetId);
        if (fileUri == null || fileUri.isEmpty()) {
            Toast.makeText(this, "Widget not found in database", Toast.LENGTH_SHORT).show();
            db.close();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        
        // Update settings
        boolean success = db.updateWidgetSettings(widgetId, showTitleCheckbox.isChecked(), 
                selectedBgColor, selectedPaddingColor);
        db.close();
        
        if (!success) {
            Toast.makeText(this, "Failed to save settings", Toast.LENGTH_SHORT).show();
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        
        // Immediately update the widget with new settings
        updateWidget();
        
        // Return success
        setResult(RESULT_OK);
        finish();
    }
    
    private void updateWidget() {
        // Update widget directly using AppWidgetManager
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        NotesDbAdapter db = new NotesDbAdapter(this);
        db.open();
        
        // Get settings
        boolean showTitle = db.getShowTitle(widgetId);
        String bgColor = db.getBgColor(widgetId);
        String paddingColor = db.getPaddingColor(widgetId);
        String fileUriString = db.getFileUri(widgetId);
        db.close();
        
        android.widget.RemoteViews views = new android.widget.RemoteViews(getPackageName(), R.layout.show_note);
        
        // Set background color using ImageView color filter
        try {
            int bgColorInt = android.graphics.Color.parseColor(bgColor);
            views.setInt(R.id.widgetBgOverlay, "setColorFilter", bgColorInt);
            views.setInt(R.id.widgetBgOverlay, "setImageResource", R.drawable.transparent);
            android.util.Log.d("WidgetOptions", "Setting background color: " + bgColor + " = " + bgColorInt);
        } catch (Exception e) {
            android.util.Log.e("WidgetOptions", "Error setting background color: " + e.getMessage());
        }
        
        // Set padding color (for the ListView background) using ImageView color filter
        try {
            int paddingColorInt = android.graphics.Color.parseColor(paddingColor);
            views.setInt(R.id.widgetPaddingOverlay, "setColorFilter", paddingColorInt);
            views.setInt(R.id.widgetPaddingOverlay, "setImageResource", R.drawable.transparent);
            android.util.Log.d("WidgetOptions", "Setting padding color: " + paddingColor + " = " + paddingColorInt);
        } catch (Exception e) {
            android.util.Log.e("WidgetOptions", "Error setting padding color: " + e.getMessage());
        }
        
        // Set up title if enabled
        if (showTitle && fileUriString != null && !fileUriString.isEmpty()) {
            String fileName = getFileName(android.net.Uri.parse(fileUriString));
            if (fileName != null && !fileName.isEmpty()) {
                views.setTextViewText(R.id.widgetTitle, fileName);
                views.setViewVisibility(R.id.widgetTitle, android.view.View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.widgetTitle, android.view.View.GONE);
            }
        } else {
            views.setViewVisibility(R.id.widgetTitle, android.view.View.GONE);
        }
        
        // Set up the RemoteViewsService intent to bind to the ListView
        Intent serviceIntent = new Intent(this, NoteViewsService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        serviceIntent.setData(android.net.Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        
        // Bind the remote adapter to the ListView
        views.setRemoteAdapter(R.id.showNoteList, serviceIntent);
        
        // Set up the pending intent template for individual items
        Intent editIntent = new Intent(this, EditNote.class);
        editIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        
        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= android.app.PendingIntent.FLAG_IMMUTABLE;
        }
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this, widgetId, editIntent, flags);
        views.setPendingIntentTemplate(R.id.showNoteList, pendingIntent);
        
        // Set click intent for the whole widget container
        views.setOnClickPendingIntent(R.id.showNote, pendingIntent);
        
        // Set click intent on title if visible
        views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent);
        
        // Update the widget
        appWidgetManager.updateAppWidget(widgetId, views);
    }
    
    private int getDrawableIdForColor(String color) {
        // Map color strings to drawable resource IDs
        if ("#1e1e2e".equals(color)) return R.drawable.widget_bg_1;
        if ("#16161e".equals(color)) return R.drawable.widget_bg_2;
        if ("#2d2d3f".equals(color)) return R.drawable.widget_bg_3;
        if ("#000000".equals(color)) return R.drawable.widget_bg_4;
        if ("#ffffff".equals(color)) return R.drawable.widget_bg_5;
        if ("#1a1a2e".equals(color)) return R.drawable.widget_bg_6;
        if ("#16213e".equals(color)) return R.drawable.widget_bg_7;
        if ("#0f3460".equals(color)) return R.drawable.widget_bg_8;
        return 0; // No matching drawable
    }
    
    private String getFileName(android.net.Uri uri) {
        String result = null;
        if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        if (result == null && uri != null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}

