package francescozoccheddu.androidpp;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity {

  static {
    System.loadLibrary("main");
  }

  private TextView label;

  private void updateText() {
    int x = getRandomNumber();
    label.setText(String.format("The answer to life, the universe and everything is %d.", x));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    label = new TextView(this);
    label.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        updateText();
      }
    });
    label.setPadding(20, 20, 20, 20);
    updateText();
    setContentView(label);
  }

  public native int getRandomNumber();

}