package net.tapi.handynotes;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.Toast;

public class NewNote extends Activity {
	private static final int REQUEST_CODE_PICK_FILE = 1;
	
	private Integer widgetId;
	private Uri selectedFileUri;
	private EditText statusText;
	private Button selectFileButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) { 
        super.onCreate(savedInstanceState);
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null && extras.containsKey(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
            widgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, 
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        } else {
            widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
        }
        
        // If no widget ID (launched from app drawer), just finish
        if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, "Add widget from home screen widget picker", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        // Go directly to file picker for widget configuration
        pickMarkdownFile();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK) {
			if (data != null) {
				Uri uri = data.getData();
				if (uri != null) {
					selectedFileUri = uri;
					
					// Grant persistable URI permission
					getContentResolver().takePersistableUriPermission(uri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION | 
						Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					
					// Save file URI first
					saveNote();
					
					// Then show options screen
					Intent optionsIntent = new Intent(this, WidgetOptionsActivity.class);
					optionsIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
					startActivityForResult(optionsIntent, 2);
				}
			} else {
				// User cancelled file selection
				setResult(RESULT_CANCELED);
				finish();
			}
		} else if (requestCode == 2 && resultCode == RESULT_OK) {
			// Options screen completed, initialize widget properly
			initializeWidget();
			
			// Finish widget configuration
			Intent resultValue = new Intent();
			resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
			setResult(RESULT_OK, resultValue);
			finish();
		} else if (requestCode == REQUEST_CODE_PICK_FILE) {
			// User cancelled file selection
			setResult(RESULT_CANCELED);
			finish();
		}
	}
	
	private void pickMarkdownFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		
		// Filter for markdown files
		String[] mimeTypes = {"text/markdown", "text/plain", "application/octet-stream"};
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		
		// Single file selection - no checkmarks, just tap to select
		intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
		
		// Request persistable URI permissions
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		
		startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
	}
	
	private String getFileName(Uri uri) {
		String result = null;
		if (uri.getScheme().equals("content")) {
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
		if (result == null) {
			result = uri.getPath();
			int cut = result.lastIndexOf('/');
			if (cut != -1) {
				result = result.substring(cut + 1);
			}
		}
		return result;
	}
	
	private void saveNote() {
		if (selectedFileUri == null) {
			Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Toast.makeText(this, "Invalid widget ID", Toast.LENGTH_SHORT).show();
			return;
		}
		
	    NotesDbAdapter db = new NotesDbAdapter(this);
        db.open();
        db.createNote(widgetId, selectedFileUri.toString());
        db.close();
	}
	
	private void initializeWidget() {
		if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Toast.makeText(this, "Invalid widget ID", Toast.LENGTH_SHORT).show();
			return;
		}
		
		Context context = getBaseContext();
		NotesDbAdapter db = new NotesDbAdapter(context);
		db.open();
		
		String fileUriString = db.getFileUri(widgetId);
		if (fileUriString == null || fileUriString.isEmpty()) {
			Toast.makeText(this, "Widget file URI not found", Toast.LENGTH_SHORT).show();
			db.close();
			return;
		}
		
		// Get per-widget settings
		boolean showTitle = db.getShowTitle(widgetId);
		String bgColor = db.getBgColor(widgetId);
		String paddingColor = db.getPaddingColor(widgetId);
		
		db.close();
		
		try {
		
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.show_note);
		
		// Set background color using ImageView color filter
		try {
			int bgColorInt = android.graphics.Color.parseColor(bgColor);
			views.setInt(R.id.widgetBgOverlay, "setColorFilter", bgColorInt);
			views.setInt(R.id.widgetBgOverlay, "setImageResource", R.drawable.transparent);
			android.util.Log.d("NewNote", "Setting background color: " + bgColor + " = " + bgColorInt);
		} catch (Exception e) {
			android.util.Log.e("NewNote", "Error setting background color: " + e.getMessage());
		}
		
		// Set padding color (for the ListView background) using ImageView color filter
		try {
			int paddingColorInt = android.graphics.Color.parseColor(paddingColor);
			views.setInt(R.id.widgetPaddingOverlay, "setColorFilter", paddingColorInt);
			views.setInt(R.id.widgetPaddingOverlay, "setImageResource", R.drawable.transparent);
			android.util.Log.d("NewNote", "Setting padding color: " + paddingColor + " = " + paddingColorInt);
		} catch (Exception e) {
			android.util.Log.e("NewNote", "Error setting padding color: " + e.getMessage());
		}
		
		// Set up title if enabled
		if (showTitle) {
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
		Intent serviceIntent = new Intent(context, NoteViewsService.class);
		serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
		serviceIntent.setData(android.net.Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
		
		// Bind the remote adapter to the ListView
		views.setRemoteAdapter(R.id.showNoteList, serviceIntent);
		
		// Set up the pending intent template for individual items
		Intent editIntent = new Intent(context, EditNote.class);
		editIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);

		int flags = PendingIntent.FLAG_UPDATE_CURRENT;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}
		PendingIntent pendingIntent = PendingIntent.getActivity(context, widgetId, editIntent, flags);
		views.setPendingIntentTemplate(R.id.showNoteList, pendingIntent);
		
		// Set click intent for the whole widget container (handles empty space clicks)
		views.setOnClickPendingIntent(R.id.showNote, pendingIntent);
		
		// Set click intent on title if visible
		views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent);

		appWidgetManager.updateAppWidget(widgetId, views);
		} catch (Exception e) {
			android.util.Log.e("NewNote", "Error initializing widget: " + e.getMessage(), e);
			Toast.makeText(this, "Error initializing widget: " + e.getMessage(), Toast.LENGTH_LONG).show();
		}
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
}
