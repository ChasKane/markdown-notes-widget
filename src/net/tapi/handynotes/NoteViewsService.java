package net.tapi.handynotes;

import android.content.Intent;
import android.widget.RemoteViewsService;

public class NoteViewsService extends RemoteViewsService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new NoteViewsFactory(this.getApplicationContext(), intent);
    }
}


