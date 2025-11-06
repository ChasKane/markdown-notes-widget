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
        
        // If no widget ID (launched from app drawer), show info message
        if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Toast.makeText(this, "Please add widget from home screen widget picker", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
		setContentView(R.layout.new_note);

        statusText = (EditText) findViewById(R.id.newNoteText);
        if (statusText != null) {
            statusText.setFocusable(false);
            statusText.setClickable(false);
            statusText.setText("Click 'Select Markdown File' to choose a markdown or text file (Obsidian vaults, cloud sync folders, local files)");
        }
        
        selectFileButton = (Button) findViewById(R.id.add_button);
        if (selectFileButton != null) {
            selectFileButton.setText("Select Markdown File");
            selectFileButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    pickMarkdownFile();
                }
            });
        }
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
					
					// Update UI to show selected file
					if (statusText != null) {
						String fileName = getFileName(uri);
						statusText.setText("Selected: " + fileName + "\n\nClick 'Add Widget' to finish");
					}
					
					// Change button to add widget
					if (selectFileButton != null) {
						selectFileButton.setText("Add Widget");
						selectFileButton.setOnClickListener(new View.OnClickListener() {
							public void onClick(View v) {
								saveNote();
								addWidget();
							}
						});
					}
				}
			}
		}
	}
	
	private void pickMarkdownFile() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.setType("*/*");
		
		// Filter for markdown files
		String[] mimeTypes = {"text/markdown", "text/plain", "application/octet-stream"};
		intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
		
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
	
	private void addWidget() {
		if (selectedFileUri == null) {
			Toast.makeText(this, "Please select a file first", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (widgetId == null || widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
			Toast.makeText(this, "Invalid widget ID", Toast.LENGTH_SHORT).show();
			return;
		}
		
		// Save the note first
		saveNote();
		
		Context context = getBaseContext();
		NotesDbAdapter db = new NotesDbAdapter(context);
		db.open();
		
		String fileUriString = db.getFileUri(widgetId);
		if (fileUriString == null || fileUriString.isEmpty()) {
			Toast.makeText(this, "Failed to save file URI", Toast.LENGTH_SHORT).show();
			db.close();
			return;
		}
		
		db.close();
		
		AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
		
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.show_note);

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
		
		// Also set click intent for the whole widget container
		views.setOnClickPendingIntent(R.id.showNote, pendingIntent);

		appWidgetManager.updateAppWidget(widgetId, views);
		
		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}
}
