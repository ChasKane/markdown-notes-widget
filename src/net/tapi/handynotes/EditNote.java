package net.tapi.handynotes;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class EditNote extends Activity {
	private Integer widgetId;
	private EditText editText;
	private NotesDbAdapter db;
	private String fileUriString;
	private Handler saveHandler;
	private Runnable saveRunnable;
	private Handler refreshHandler;
	private Runnable refreshRunnable;
	private String lastKnownContent;
	private long lastSaveTime = 0;
	private static final int AUTO_SAVE_DELAY_MS = 1000; // Save 1 second after user stops typing
	private static final int REFRESH_INTERVAL_MS = 5000; // Check for changes every 5 seconds
	private static final int SAVE_COOLDOWN_MS = 2000; // Don't check for external changes within 2 seconds of save
	
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
     
		setContentView(R.layout.edit_note);

        editText = (EditText) findViewById(R.id.editNoteText);
        
	    db = new NotesDbAdapter(this);

        db.open();
        fileUriString = db.getFileUri(widgetId);
        
        if (fileUriString != null && !fileUriString.isEmpty()) {
            String text = db.readFileContent(fileUriString);
            lastKnownContent = text;
            editText.setText(text);
        } else {
            Toast.makeText(this, "No file associated with this widget", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        db.close();
        
        // Set up auto-save
        saveHandler = new Handler(Looper.getMainLooper());
        saveRunnable = new Runnable() {
            @Override
            public void run() {
                saveNote();
                updateWidget();
            }
        };
        
        // Set up periodic refresh to detect external changes (e.g., from cloud sync or other apps)
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                checkForExternalChanges();
                // Schedule next check
                refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
            }
        };
        
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not used
            }
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancel any pending saves
                saveHandler.removeCallbacks(saveRunnable);
                // Schedule a new save after the delay
                saveHandler.postDelayed(saveRunnable, AUTO_SAVE_DELAY_MS);
            }
            
            @Override
            public void afterTextChanged(Editable s) {
                // Not used
            }
        });
        
        // Handle Enter key to auto-continue list markers
        editText.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    handleEnterKey();
                    return true; // Consume the event
                }
                return false; // Let other keys pass through
            }
        });
        
        Button button = (Button) findViewById(R.id.updateButton);
        if (button != null) {
            button.setText("Delete Line");
            button.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    deleteCurrentLine();
                }
            });
        }
  
	}
	
	private void saveNote() {
		if (fileUriString == null || fileUriString.isEmpty()) {
			android.util.Log.e("EditNote", "saveNote: fileUriString is null or empty");
			Toast.makeText(this, "No file to save to", Toast.LENGTH_SHORT).show();
			return;
		}
		
		if (editText == null) {
			android.util.Log.e("EditNote", "saveNote: editText is null");
			return;
		}
		
		String content = editText.getText().toString();
		android.util.Log.d("EditNote", "saveNote: saving content length " + content.length() + " to " + fileUriString);
		
		db.open();
		boolean success = db.writeFileContent(fileUriString, content);
		db.close();
		
		if (!success) {
			android.util.Log.e("EditNote", "saveNote: writeFileContent returned false");
			Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show();
		} else {
			android.util.Log.d("EditNote", "saveNote: successfully saved");
			// Update lastKnownContent after successful save
			lastKnownContent = content;
			// Record save time to prevent checkForExternalChanges from running too soon
			lastSaveTime = System.currentTimeMillis();
		}
	}
	
	private void updateWidget() {
		Intent intent = new Intent(this, HandyNotes.class);
		intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
		
		int[] ids = {widgetId};
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
		sendBroadcast(intent);
	}
	
	private void handleEnterKey() {
		if (editText == null) {
			return;
		}
		
		String content = editText.getText().toString();
		int cursorPosition = editText.getSelectionStart();
		
		if (cursorPosition < 0) {
			cursorPosition = 0;
		}
		
		// Find the start of the current line
		int lineStart = cursorPosition;
		while (lineStart > 0 && content.charAt(lineStart - 1) != '\n') {
			lineStart--;
		}
		
		// Get the current line content
		int lineEnd = cursorPosition;
		while (lineEnd < content.length() && content.charAt(lineEnd) != '\n') {
			lineEnd++;
		}
		
		String currentLine = content.substring(lineStart, lineEnd);
		
		// Check if line starts with *, >, or - (with optional whitespace)
		String prefix = null;
		if (currentLine.startsWith("* ")) {
			prefix = "* ";
		} else if (currentLine.startsWith("> ")) {
			prefix = "> ";
		} else if (currentLine.startsWith("- ")) {
			prefix = "- ";
		} else if (currentLine.startsWith("*")) {
			prefix = "*";
		} else if (currentLine.startsWith(">")) {
			prefix = ">";
		} else if (currentLine.startsWith("-")) {
			prefix = "-";
		}
		
		// Insert newline and prefix if found
		if (prefix != null) {
			Editable editable = editText.getText();
			editable.insert(cursorPosition, "\n" + prefix);
			
			// Move cursor to end of inserted prefix
			int newCursorPosition = cursorPosition + 1 + prefix.length();
			editText.setSelection(newCursorPosition);
		} else {
			// Just insert a newline
			Editable editable = editText.getText();
			editable.insert(cursorPosition, "\n");
			
			// Move cursor to next line
			int newCursorPosition = cursorPosition + 1;
			editText.setSelection(newCursorPosition);
		}
	}
	
	private void deleteCurrentLine() {
		if (editText == null) {
			return;
		}
		
		String content = editText.getText().toString();
		int cursorPosition = editText.getSelectionStart();
		
		if (cursorPosition < 0) {
			cursorPosition = 0;
		}
		
		// Find the start of the current line
		int lineStart = cursorPosition;
		while (lineStart > 0 && content.charAt(lineStart - 1) != '\n') {
			lineStart--;
		}
		
		// Find the end of the current line
		int lineEnd = cursorPosition;
		while (lineEnd < content.length() && content.charAt(lineEnd) != '\n') {
			lineEnd++;
		}
		
		// Include the newline character if we're not at the end of the file
		if (lineEnd < content.length()) {
			lineEnd++;
		}
		
		// Build new content without the current line
		StringBuilder newContent = new StringBuilder();
		if (lineStart > 0) {
			newContent.append(content.substring(0, lineStart));
		}
		if (lineEnd < content.length()) {
			newContent.append(content.substring(lineEnd));
		}
		
		// Calculate new cursor position (end of line above)
		int newCursorPosition = lineStart;
		if (lineStart > 0) {
			// Find end of previous line
			int prevLineEnd = lineStart - 1;
			while (prevLineEnd > 0 && content.charAt(prevLineEnd - 1) != '\n') {
				prevLineEnd--;
			}
			// Find end of that line
			while (prevLineEnd < lineStart && prevLineEnd < content.length() && content.charAt(prevLineEnd) != '\n') {
				prevLineEnd++;
			}
			newCursorPosition = prevLineEnd;
		}
		
		// Update text
		editText.setText(newContent.toString());
		
		// Set cursor position
		if (newCursorPosition > newContent.length()) {
			newCursorPosition = newContent.length();
		}
		editText.setSelection(newCursorPosition);
		
		// Trigger auto-save
		saveHandler.removeCallbacks(saveRunnable);
		saveHandler.postDelayed(saveRunnable, AUTO_SAVE_DELAY_MS);
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		// Check for external changes when activity resumes
		checkForExternalChanges();
		// Start periodic refresh
		if (refreshHandler != null && refreshRunnable != null) {
			refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
		}
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		// Stop periodic refresh
		if (refreshHandler != null && refreshRunnable != null) {
			refreshHandler.removeCallbacks(refreshRunnable);
		}
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		// Cancel any pending saves
		if (saveHandler != null && saveRunnable != null) {
			saveHandler.removeCallbacks(saveRunnable);
		}
		// Cancel refresh
		if (refreshHandler != null && refreshRunnable != null) {
			refreshHandler.removeCallbacks(refreshRunnable);
		}
		// Final save on exit
		if (fileUriString != null && !fileUriString.isEmpty()) {
			saveNote();
			updateWidget();
		}
	}
	
	private void checkForExternalChanges() {
		if (fileUriString == null || fileUriString.isEmpty() || editText == null) {
			return;
		}
		
		// Don't check for external changes if we just saved (avoid race condition)
		long timeSinceLastSave = System.currentTimeMillis() - lastSaveTime;
		if (timeSinceLastSave < SAVE_COOLDOWN_MS) {
			android.util.Log.d("EditNote", "Skipping external change check - too soon after save");
			return;
		}
		
		// Get current content from file
		db.open();
		String fileContent = db.readFileContent(fileUriString);
		db.close();
		
		// Compare with what we have in the editor
		String editorContent = editText.getText().toString();
		
		// If file has changed and editor content matches what we last saved, update editor
		if (fileContent != null && !fileContent.equals(editorContent)) {
			// Only update if editor content matches last known content (user hasn't made local changes)
			if (lastKnownContent != null && editorContent.equals(lastKnownContent)) {
				// File was changed externally (e.g., from cloud sync or other apps)
				// Preserve cursor position before updating text
				int cursorPosition = editText.getSelectionStart();
				if (cursorPosition < 0) {
					cursorPosition = 0;
				}
				
				editText.setText(fileContent);
				
				// Restore cursor position, but clamp to new content length
				int newCursorPosition = cursorPosition;
				if (newCursorPosition > fileContent.length()) {
					newCursorPosition = fileContent.length();
				}
				editText.setSelection(newCursorPosition);
				
				lastKnownContent = fileContent;
				updateWidget();
				android.util.Log.d("EditNote", "Detected external change, updated editor");
			} else if (!editorContent.equals(lastKnownContent)) {
				// User has made local changes, update lastKnownContent for next check
				lastKnownContent = editorContent;
			}
		}
	}
}
