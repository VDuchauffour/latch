package app.latch.fakeapps;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Every placeholder launcher entry opens this screen, which just shows the entry's label. */
public class FakeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView view = new TextView(this);
        view.setText(getTitle());
        view.setTextSize(32);
        view.setGravity(android.view.Gravity.CENTER);
        setContentView(view);
    }
}
