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
			
			// Also set click intent for the whole widget container
			views.setOnClickPendingIntent(R.id.showNote, pendingIntent);

			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
		
		db.close();
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
