package net.tapi.handynotes;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

public class NoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
    private Context context;
    private int appWidgetId;
    private NotesDbAdapter db;
    private List<String> noteLines;

    public NoteViewsFactory(Context context, Intent intent) {
        this.context = context;
        this.appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 
                                               AppWidgetManager.INVALID_APPWIDGET_ID);
        this.noteLines = new ArrayList<String>();
    }

    @Override
    public void onCreate() {
        // Initialize data source
        loadNoteData();
    }

    @Override
    public void onDataSetChanged() {
        // Reload data when widget is updated
        loadNoteData();
    }

    @Override
    public void onDestroy() {
        noteLines.clear();
    }

    @Override
    public int getCount() {
        // Add 1 for the filler item at the end
        return noteLines.size() + 1;
    }

    @Override
    public RemoteViews getViewAt(int position) {
        // Last position is the filler item
        if (position == noteLines.size()) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.note_list_item_filler);
            // Set click intent to open EditNote when filler is clicked
            Intent fillInIntent = new Intent();
            fillInIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            views.setOnClickFillInIntent(R.id.noteListItemText, fillInIntent);
            return views;
        }
        
        if (position < 0 || position >= noteLines.size()) {
            return null;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.note_list_item);
        
        String line = noteLines.get(position);
        if (line == null) {
            line = "";
        }
        
        views.setTextViewText(R.id.noteListItemText, line);

        // Set click intent to open EditNote when item is clicked
        Intent fillInIntent = new Intent();
        fillInIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickFillInIntent(R.id.noteListItemText, fillInIntent);

        return views;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 2; // Regular items and filler item
    }
    
    public int getItemViewType(int position) {
        // Return 0 for regular items, 1 for filler item
        if (position == noteLines.size()) {
            return 1;
        }
        return 0;
    }

    @Override
    public long getItemId(int position) {
        // Use negative ID for filler item to distinguish it
        if (position == noteLines.size()) {
            return -1;
        }
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    private void loadNoteData() {
        noteLines.clear();
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return;
        }

        db = new NotesDbAdapter(context);
        db.open();
        
        try {
            String fileUriString = db.getFileUri(appWidgetId);
            String text = "";
            
            if (fileUriString != null && !fileUriString.isEmpty()) {
                text = db.readFileContent(fileUriString);
            }
            
            // Split text into lines
            if (text != null && !text.isEmpty()) {
                String[] lines = text.split("\n", -1); // -1 to include trailing empty lines
                for (String line : lines) {
                    noteLines.add(line);
                }
            }
            
            // If text is empty, add at least one empty line so widget shows something
            if (noteLines.isEmpty()) {
                noteLines.add("");
            }
        } finally {
            db.close();
        }
    }
}


