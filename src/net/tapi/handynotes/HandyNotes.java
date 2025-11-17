package net.tapi.handynotes;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class HandyNotes extends AppWidgetProvider {
    private NotesDbAdapter db;

	@Override
	public void onUpdate(Context context,
			             AppWidgetManager appWidgetManager,
			             int[] appWidgetIds) {
		
        db = new NotesDbAdapter(context);
        db.open();
		
		for (int appWidgetId : appWidgetIds) {
			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.show_note);
			
			// Get per-widget settings
			boolean showTitle = db.getShowTitle(appWidgetId);
			String bgColor = db.getBgColor(appWidgetId);
			String paddingColor = db.getPaddingColor(appWidgetId);
			
			// Set background color using ImageView color filter
			try {
				int bgColorInt = android.graphics.Color.parseColor(bgColor);
				views.setInt(R.id.widgetBgOverlay, "setColorFilter", bgColorInt);
				views.setInt(R.id.widgetBgOverlay, "setImageResource", R.drawable.transparent);
				android.util.Log.d("HandyNotes", "Setting background color: " + bgColor + " = " + bgColorInt);
			} catch (Exception e) {
				android.util.Log.e("HandyNotes", "Error setting background color: " + e.getMessage());
			}
			
			// Set padding color (for the ListView background) using ImageView color filter
			try {
				int paddingColorInt = android.graphics.Color.parseColor(paddingColor);
				views.setInt(R.id.widgetPaddingOverlay, "setColorFilter", paddingColorInt);
				views.setInt(R.id.widgetPaddingOverlay, "setImageResource", R.drawable.transparent);
				android.util.Log.d("HandyNotes", "Setting padding color: " + paddingColor + " = " + paddingColorInt);
			} catch (Exception e) {
				android.util.Log.e("HandyNotes", "Error setting padding color: " + e.getMessage());
			}
			
			// Set up title if enabled
			if (showTitle) {
				String fileUriString = db.getFileUri(appWidgetId);
				if (fileUriString != null && !fileUriString.isEmpty()) {
					String fileName = getFileName(context, android.net.Uri.parse(fileUriString));
					if (fileName != null && !fileName.isEmpty()) {
						views.setTextViewText(R.id.widgetTitle, fileName);
						views.setViewVisibility(R.id.widgetTitle, android.view.View.VISIBLE);
					} else {
						views.setViewVisibility(R.id.widgetTitle, android.view.View.GONE);
					}
				} else {
					views.setViewVisibility(R.id.widgetTitle, android.view.View.GONE);
				}
			} else {
				views.setViewVisibility(R.id.widgetTitle, android.view.View.GONE);
			}
			
			// Set up the RemoteViewsService intent to bind to the ListView
			Intent serviceIntent = new Intent(context, NoteViewsService.class);
			serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
			serviceIntent.setData(android.net.Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
			
			// Bind the remote adapter to the ListView
			views.setRemoteAdapter(R.id.showNoteList, serviceIntent);
			
			// Set up the pending intent template for individual items
		Intent editIntent = new Intent(context, EditNote.class);
		editIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
		int flags = PendingIntent.FLAG_UPDATE_CURRENT;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
			flags |= PendingIntent.FLAG_IMMUTABLE;
		}
		PendingIntent pendingIntent = PendingIntent.getActivity(context, appWidgetId, editIntent, flags);
			views.setPendingIntentTemplate(R.id.showNoteList, pendingIntent);
			
			// Set click intent for the whole widget container (handles empty space clicks)
			views.setOnClickPendingIntent(R.id.showNote, pendingIntent);
			
			// Set click intent on title if visible
			views.setOnClickPendingIntent(R.id.widgetTitle, pendingIntent);

			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
		
		db.close();
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
	
	private String getFileName(Context context, android.net.Uri uri) {
		String result = null;
		if (uri != null && uri.getScheme() != null && uri.getScheme().equals("content")) {
			android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
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

	@Override
	public void onDeleted(Context context,
			             int[] appWidgetIds) {
		
        db = new NotesDbAdapter(context);
        db.open();
        
		for (int appWidgetId : appWidgetIds) {
			db.deleteNote(appWidgetId);
		}
        
		db.close();
	}
}
