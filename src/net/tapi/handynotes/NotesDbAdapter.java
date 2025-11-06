package net.tapi.handynotes;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;
import java.io.InputStream;

public class NotesDbAdapter {
    
	private final Context context;
	
    public static final String KEY_ROWID = "_id";
    public static final String KEY_FILE_URI = "file_uri";

    private static final String TAG = "NotesDbAdapter";
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    
    private static final String DATABASE_CREATE =
        "create table notes (_id integer primary key, "
        + "file_uri text not null);";
    
    private static final String DATABASE_NAME = "data";
    private static final String DATABASE_TABLE = "notes";
    private static final int DATABASE_VERSION = 2;
	
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        
        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL(DATABASE_CREATE);
        }
        
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion);
            if (oldVersion < 2) {
                // Migrate from body-based storage to file URI-based storage
                db.execSQL("DROP TABLE IF EXISTS notes");
                onCreate(db);
            }
        }
    }
    
    public NotesDbAdapter(Context c) {
        this.context = c;
    }
    
    public NotesDbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(context);
        mDb = mDbHelper.getWritableDatabase();
        return this;
    }

    public void close() {
        mDbHelper.close();
    }
    
    public long createNote(int wId, String fileUri) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_ROWID, wId);
        initialValues.put(KEY_FILE_URI, fileUri);

        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }
    
    public boolean deleteNote(int wId) {

        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + wId, null) > 0;
    }
    
    public String getFileUri(int wId) throws SQLException {
        String result = "";

    	Cursor mCursor =
            mDb.query(true, DATABASE_TABLE, new String[] {KEY_ROWID, KEY_FILE_URI}, KEY_ROWID + "=" + wId, null,
                    null, null, null, null);

    	if (mCursor != null) {
        	mCursor.moveToFirst();

        	if (mCursor.getCount() > 0) {
            		result = mCursor.getString(mCursor.getColumnIndex(KEY_FILE_URI));
        	}
        	mCursor.close();
    	}

    	return result;
    }
    
    public boolean updateNote(int wId, String fileUri) {
        ContentValues args = new ContentValues();
        args.put(KEY_FILE_URI, fileUri);

        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + wId, null) > 0;
    }
    
    public String readFileContent(String fileUriString) {
        if (fileUriString == null || fileUriString.isEmpty()) {
            return "";
        }
        
        try {
            Uri fileUri = Uri.parse(fileUriString);
            InputStream inputStream = context.getContentResolver().openInputStream(fileUri);
            if (inputStream == null) {
                return "";
            }
            
            // Read character by character to preserve exact content including trailing newlines
            // This is more reliable than readLine() which strips line terminators
            StringBuilder stringBuilder = new StringBuilder();
            int ch;
            while ((ch = inputStream.read()) != -1) {
                stringBuilder.append((char) ch);
            }
            
            inputStream.close();
            
            // Return content as-is, preserving all whitespace including trailing newlines
            return stringBuilder.toString();
        } catch (Exception e) {
            Log.e(TAG, "Error reading file: " + e.getMessage());
            return "";
        }
    }
    
    public boolean writeFileContent(String fileUriString, String content) {
        if (fileUriString == null || fileUriString.isEmpty()) {
            Log.e(TAG, "writeFileContent: fileUriString is null or empty");
            return false;
        }
        
        try {
            Uri fileUri = Uri.parse(fileUriString);
            android.content.ContentResolver resolver = context.getContentResolver();
            
            // Use ParcelFileDescriptor for writing - "wt" mode truncates and writes text
            android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(fileUri, "wt");
            if (pfd == null) {
                Log.e(TAG, "writeFileContent: failed to open file descriptor");
                return false;
            }
            
            java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(pfd.getFileDescriptor());
            byte[] contentBytes = content.getBytes("UTF-8");
            fileOutputStream.write(contentBytes);
            fileOutputStream.flush();
            fileOutputStream.close();
            pfd.close();
            
            Log.d(TAG, "writeFileContent: successfully wrote " + contentBytes.length + " bytes");
            return true;
        } catch (java.io.FileNotFoundException e) {
            Log.e(TAG, "writeFileContent: FileNotFoundException - " + e.getMessage());
            // Try with "w" mode instead of "wt"
            try {
                Uri fileUri = Uri.parse(fileUriString);
                android.content.ContentResolver resolver = context.getContentResolver();
                android.os.ParcelFileDescriptor pfd = resolver.openFileDescriptor(fileUri, "w");
                if (pfd != null) {
                    java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(pfd.getFileDescriptor());
                    byte[] contentBytes = content.getBytes("UTF-8");
                    fileOutputStream.write(contentBytes);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    pfd.close();
                    Log.d(TAG, "writeFileContent: successfully wrote " + contentBytes.length + " bytes (retry with 'w' mode)");
                    return true;
                }
            } catch (Exception e2) {
                Log.e(TAG, "writeFileContent: retry also failed - " + e2.getMessage());
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "writeFileContent: Exception - " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
