package py.com.sodep.mobileforms.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import io.github.jokoframework.chake.R;

public class SplashActivity extends Activity {

	private static final int delayMillis = 2500;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.splash);

		new Handler().postDelayed(new Runnable() {

			@Override
			public void run() {
				Intent i = new Intent(SplashActivity.this, LoginActivity.class);
				startActivity(i);
				overridePendingTransition(R.anim.anim_in, R.anim.anim_out);
				finish();
			}
		}, delayMillis);
	}

}
