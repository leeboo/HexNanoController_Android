package com.hexairbot.hexmini;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;

import com.hexairbot.hexmini.HexMiniApplication.AppStage;
import com.hexairbot.hexmini.ble.BleConnectinManager;
import com.hexairbot.hexmini.gestures.EnhancedGestureDetector;
import com.hexairbot.hexmini.modal.ApplicationSettings;
import com.hexairbot.hexmini.modal.Channel;
import com.hexairbot.hexmini.modal.OSDCommon;
import com.hexairbot.hexmini.modal.Transmitter;
import com.hexairbot.hexmini.sensors.DeviceOrientationChangeDelegate;
import com.hexairbot.hexmini.sensors.DeviceOrientationManager;
import com.hexairbot.hexmini.sensors.DeviceSensorManagerWrapper;
import com.hexairbot.hexmini.ui.Button;
import com.hexairbot.hexmini.ui.ToggleButton;
import com.hexairbot.hexmini.ui.Image;
import com.hexairbot.hexmini.ui.Indicator;
import com.hexairbot.hexmini.ui.Sprite;
import com.hexairbot.hexmini.ui.Text;
import com.hexairbot.hexmini.ui.UIRenderer;
import com.hexairbot.hexmini.ui.Image.SizeParams;
import com.hexairbot.hexmini.ui.Sprite.Align;
import com.hexairbot.hexmini.ui.joystick.AcceleratorJoystick;
import com.hexairbot.hexmini.ui.joystick.AnalogueJoystick;
import com.hexairbot.hexmini.ui.joystick.JoystickBase;
import com.hexairbot.hexmini.ui.joystick.JoystickFactory;
import com.hexairbot.hexmini.ui.joystick.JoystickFactory.JoystickType;
import com.hexairbot.hexmini.ui.joystick.JoystickListener;
import com.hexairbot.hexmini.util.FontUtils;


public class HudViewController extends ViewController
	implements OnTouchListener,
			   OnGestureListener,
			   SettingsViewControllerDelegate, DeviceOrientationChangeDelegate
{
	private static final String TAG = "HudViewController";
	
	private static final int JOY_ID_LEFT         = 1;
	private static final int JOY_ID_RIGHT        = 2;
	private static final int MIDLLE_BG_ID        = 3;
	private static final int TOP_BAR_ID          = 4;
	private static final int BOTTOM_BAR_ID       = 5;
	private static final int TAKE_OFF_BTN_ID     = 6;
	private static final int STOP_BTN_ID         = 7;
	private static final int SETTINGS_BTN_ID     = 8;
	private static final int ALT_HOLD_TOGGLE_BTN = 9;
	private static final int STATE_TEXT_VIEW     = 10;
	
	private Image bottomBarBg;

	private Button stopBtn;
	private Button takeOffBtn;
	private Button settingsBtn;
	private ToggleButton altHoldToggleBtn;
	
	private boolean isAltHoldMode;
	private boolean isAccMode;
	
	private Button[] buttons;
	
	private Indicator batteryIndicator;
	
	private Text txtBatteryStatus;
	
	private GLSurfaceView glView;
	
	private JoystickBase[] joysticks;   //[0]roll and pitch, [1]rudder and throttle
	private float joypadOpacity;
	private GestureDetector gestureDetector;
	
	private UIRenderer renderer;
	
    private HudViewControllerDelegate delegate;
    
    private boolean isLeftHanded;
    private JoystickListener rollPitchListener;
    private JoystickListener rudderThrottleListener;
    
    
    
    private ApplicationSettings settings;
    
    private Channel aileronChannel;
    private Channel elevatorChannel;
    private Channel rudderChannel;
    private Channel throttleChannel;
    private Channel aux1Channel;
    private Channel aux2Channel;
    private Channel aux3Channel;
    private Channel aux4Channel;
    
    private DeviceOrientationManager deviceOrientationManager;
    private static final float ACCELERO_TRESHOLD = (float) Math.PI / 180.0f * 2.0f;
    private static final int PITCH = 1;
    private static final int ROLL = 2;
    private float pitchBase;
    private float rollBase;
    private boolean rollAndPitchJoystickPressed;
    
	public HudViewController(Activity context, HudViewControllerDelegate delegate)
	{
		this.delegate = delegate;
		this.context = context;
		Transmitter.sharedTransmitter().setBleConnectionManager(new BleConnectinManager(context));      
		
		settings = ((HexMiniApplication)context.getApplication()).getAppSettings();
		
	    joypadOpacity = settings.getInterfaceOpacity();
	    isLeftHanded  = settings.isLeftHanded();
	    
		this.context = context;
		gestureDetector = new EnhancedGestureDetector(context, this);
		
		joysticks = new JoystickBase[2];

		glView = new GLSurfaceView(context);
		glView.setEGLContextClientVersion(2);
		
		context.setContentView(glView);
		
		renderer = new UIRenderer(context, null);
	
		initGLSurfaceView();

		Resources res = context.getResources();

		Image topBarBg = new Image(res, R.drawable.bar_top, Align.TOP_CENTER);
		topBarBg.setSizeParams(SizeParams.FILL_SCREEN, SizeParams.NONE);  //Width水平伸缩至全屏，height保持不边
		topBarBg.setAlphaEnabled(false);
		
		bottomBarBg = new Image(res, R.drawable.bar_bottom, Align.BOTTOM_CENTER);
		bottomBarBg.setSizeParams(SizeParams.FILL_SCREEN, SizeParams.NONE);
		bottomBarBg.setAlphaEnabled(false);
		
		Image middleBg = new Image(res, R.drawable.middle_bg, Align.CENTER);
		middleBg.setSizeParams(SizeParams.FILL_SCREEN, SizeParams.FILL_SCREEN);  //Width水平伸缩至全屏，height保持不边
		middleBg.setAlphaEnabled(false);
		
		settingsBtn = new Button(res, R.drawable.btn_settings_normal, R.drawable.btn_settings_hl, Align.TOP_RIGHT);
		settingsBtn.setMargin(0, (int)res.getDimension(R.dimen.hud_btn_settings_margin_right), 0, 0);
			
		takeOffBtn = new Button(res, R.drawable.btn_take_off_normal, R.drawable.btn_take_off_hl, Align.BOTTOM_CENTER);		
		stopBtn = new Button(res, R.drawable.btn_stop_normal, R.drawable.btn_stop_hl, Align.TOP_CENTER);

		Text stateTextView = new Text(context, "已连接", Align.TOP_LEFT);
		stateTextView.setMargin((int)res.getDimension(R.dimen.hud_state_text_margin_top), 0, 0, (int)res.getDimension(R.dimen.hud_state_text_margin_left));
		stateTextView.setTextColor(Color.WHITE);
		stateTextView.setTypeface(FontUtils.TYPEFACE.Helvetica(context));
		stateTextView.setTextSize(res.getDimensionPixelSize(R.dimen.hud_state_text_size));
		
		altHoldToggleBtn = new ToggleButton(res, R.drawable.btn_off_normal, R.drawable.btn_off_hl, 
                R.drawable.btn_on_normal, R.drawable.btn_on_hl,
                R.drawable.btn_on_normal, Align.TOP_LEFT);
		
		altHoldToggleBtn.setMargin(0, 0, 0, res.getDimensionPixelOffset(R.dimen.hud_alt_hold_toggle_btn_margin_left));
		altHoldToggleBtn.setChecked(settings.isAltHoldMode());
		
		buttons = new Button[4];
		buttons[0] = settingsBtn;
		buttons[1] = takeOffBtn;
		buttons[2] = stopBtn;
		buttons[3] = altHoldToggleBtn;
		
		renderer.addSprite(MIDLLE_BG_ID, middleBg);
		renderer.addSprite(TOP_BAR_ID, topBarBg);
		renderer.addSprite(BOTTOM_BAR_ID, bottomBarBg);
		renderer.addSprite(TAKE_OFF_BTN_ID, takeOffBtn);
		renderer.addSprite(STOP_BTN_ID, stopBtn);
		renderer.addSprite(SETTINGS_BTN_ID, settingsBtn);
		renderer.addSprite(ALT_HOLD_TOGGLE_BTN, altHoldToggleBtn);
		renderer.addSprite(STATE_TEXT_VIEW, stateTextView);
		
		isAccMode = settings.isAccMode();
		deviceOrientationManager = new DeviceOrientationManager(new DeviceSensorManagerWrapper(this.context), this);
		deviceOrientationManager.onCreate();
		
		initJoystickListeners();
		
		if (isAccMode) {
			initJoysticks(JoystickType.ACCELERO);
		}
		else{
			initJoysticks(JoystickType.ANALOGUE);
		}
		
		
		initListeners();
		
		initChannels();
		
		if (settings.isHeadFreeMode()) {
			aux1Channel.setValue(1);
		}
		else {
			aux1Channel.setValue(-1);
		}
		
		if (settings.isAltHoldMode()) {
			aux2Channel.setValue(1);
		}
		else{
			aux2Channel.setValue(-1);
		}
	}
	
	private void initChannels() {
	    aileronChannel  = settings.getChannel(Channel.CHANNEL_NAME_AILERON);
	    elevatorChannel = settings.getChannel(Channel.CHANNEL_NAME_ELEVATOR);
	    rudderChannel   = settings.getChannel(Channel.CHANNEL_NAME_RUDDER);
	    throttleChannel = settings.getChannel(Channel.CHANNEL_NAME_THROTTLE);
	    aux1Channel     = settings.getChannel(Channel.CHANNEL_NAME_AUX1);
	    aux2Channel     = settings.getChannel(Channel.CHANNEL_NAME_AUX2);
	    aux3Channel     = settings.getChannel(Channel.CHANNEL_NAME_AUX3);
	    aux4Channel     = settings.getChannel(Channel.CHANNEL_NAME_AUX4);
	    
	    aileronChannel.setValue(0.0f);
	    elevatorChannel.setValue(0.0f);
	    rudderChannel.setValue(0.0f);
	    throttleChannel.setValue(-1);
	}
	
	private void initJoystickListeners()
    {
	        rollPitchListener = new JoystickListener()
	        {
	            public void onChanged(JoystickBase joy, float x, float y)
	            {
	            	if(HexMiniApplication.sharedApplicaion().getAppStage() == AppStage.SETTINGS){
	            		Log.e(TAG, "AppStage.SETTINGS ignore rollPitchListener onChanged");
	            		return;
	            	}
	            	
	            	if (isAccMode == false && rollAndPitchJoystickPressed == true) {
		        		Log.e(TAG, "rollPitchListener onChanged x:" + x + "y:" + y);
		                aileronChannel.setValue(x);
		                elevatorChannel.setValue(y);
					}
	            }

	            @Override
	            public void onPressed(JoystickBase joy)
	            {
	            	rollAndPitchJoystickPressed = true;
	            }

	            @Override
	            public void onReleased(JoystickBase joy)
	            {
	            	rollAndPitchJoystickPressed = false;
	            	
	                aileronChannel.setValue(0.0f);
	                elevatorChannel.setValue(0.0f);
	               
	            }
	        };

	        rudderThrottleListener = new JoystickListener()
	        {
	            public void onChanged(JoystickBase joy, float x, float y)
	            {
	            	if(HexMiniApplication.sharedApplicaion().getAppStage() == AppStage.SETTINGS){
	            		Log.e(TAG, "AppStage.SETTINGS ignore rudderThrottleListener onChanged");
	            		return;
	            	}
	            	
	            	
	        		Log.e(TAG, "rudderThrottleListener onChanged x:" + x + "y:" + y);
	        		rudderChannel.setValue(x);
	        		throttleChannel.setValue(y);
	            }

	            @Override
	            public void onPressed(JoystickBase joy)
	            {
	            	
	            }

	            @Override
	            public void onReleased(JoystickBase joy)
	            {
	        		rudderChannel.setValue(0.0f);
	        		
	        		Log.e(TAG, "rudderThrottleListener onReleased"+joy.getYValue());
	        		
	        		throttleChannel.setValue(joy.getYValue());
	            }
	        };
    }
	
	private void initListeners() {
		settingsBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				
				if (delegate != null) {
					delegate.settingsBtnDidClick(arg0);
				}

			}
		});
		
		takeOffBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				Transmitter.sharedTransmitter().transmmitSimpleCommand(OSDCommon.MSPCommnand.MSP_ARM);
			}
		});
		
		stopBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				Transmitter.sharedTransmitter().transmmitSimpleCommand(OSDCommon.MSPCommnand.MSP_DISARM);
			}
		});
		
		
		altHoldToggleBtn.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				isAltHoldMode = !isAltHoldMode;
				settings.setIsAltHoldMode(isAltHoldMode);
				settings.save();
				
				altHoldToggleBtn.setChecked(isAltHoldMode);
				
				if (isAltHoldMode) {
					aux2Channel.setValue(1);
				}
				else{
					aux2Channel.setValue(-1);
				}
			}
		});
	}
	
	private void initGLSurfaceView() {
		if (glView != null) {
			glView.setEGLConfigChooser(8 , 8, 8, 8, 16, 0);
			glView.setRenderer(renderer);
			glView.setOnTouchListener(this);
		}
	}
	
	 private void initJoysticks(JoystickType rollAndPitchType)
	    {
	        JoystickBase rollAndPitchJoystick      = getRollAndPitchJoystick(); 
	        JoystickBase rudderAndThrottleJoystick = getRudderAndThrottleJoystick();
	        
	        if (rollAndPitchType == JoystickType.ANALOGUE) {
	            if (rollAndPitchJoystick == null || !(rollAndPitchJoystick instanceof AnalogueJoystick)) {
	            	rollAndPitchJoystick = JoystickFactory.createAnalogueJoystick(this.getContext(), false, rollPitchListener, true);
	            	rollAndPitchJoystick.setXDeadBand(settings.getAileronDeadBand());
	            	rollAndPitchJoystick.setYDeadBand(settings.getElevatorDeadBand());
	            } 
	            else {
	            	rollAndPitchJoystick.setOnAnalogueChangedListener(rollPitchListener);
	            }
			}
	        else if(rollAndPitchType == JoystickType.ACCELERO){
	            if (rollAndPitchJoystick == null || !(rollAndPitchJoystick instanceof AcceleratorJoystick)) {
	            	rollAndPitchJoystick = JoystickFactory.createAcceleroJoystick(this.getContext(), false, rollPitchListener, true);
	            	//rollAndPitchJoystick.setXDeadBand(settings.getAileronDeadBand());
	            	//rollAndPitchJoystick.setYDeadBand(settings.getElevatorDeadBand());
	            } 
	            else {
	            	rollAndPitchJoystick.setOnAnalogueChangedListener(rollPitchListener);
	            }
	        }
	        
	        if (rudderAndThrottleJoystick == null || !(rudderAndThrottleJoystick instanceof AnalogueJoystick)) {
	        	rudderAndThrottleJoystick = JoystickFactory.createAnalogueJoystick(this.getContext(), false, rudderThrottleListener, false);
	        	rudderAndThrottleJoystick.setXDeadBand(settings.getRudderDeadBand());
	        } 
	        else {
	        	rudderAndThrottleJoystick.setOnAnalogueChangedListener(rudderThrottleListener);
	        }
	        
	        rollAndPitchJoystick.setIsRollPitchJoystick(true);
	        rudderAndThrottleJoystick.setIsRollPitchJoystick(false);
	        
	        joysticks[0] = rollAndPitchJoystick;
	        joysticks[1] = rudderAndThrottleJoystick;
	        
	        setJoysticks();
	        
	        getRudderAndThrottleJoystick().setYValue(-1);
	    }
	
	public void setJoysticks()
	{
		JoystickBase rollAndPitchJoystick = joysticks[0];
		JoystickBase rudderAndThrottleJoystick = joysticks[1];
		
		if (rollAndPitchJoystick != null) 
		{
			if (isLeftHanded) {
			    joysticks[0].setAlign(Align.BOTTOM_RIGHT);
			    joysticks[0].setAlpha(joypadOpacity);
			}else{
				joysticks[0].setAlign(Align.BOTTOM_LEFT);
				joysticks[0].setAlpha(joypadOpacity);
			}
			
			rollAndPitchJoystick.setNeedsUpdate();
		}
	
		if (rudderAndThrottleJoystick != null)	{
			if (isLeftHanded) {
			    joysticks[1].setAlign(Align.BOTTOM_LEFT);
			    joysticks[1].setAlpha(joypadOpacity);
			}else{
			    joysticks[1].setAlign(Align.BOTTOM_RIGHT);
			    joysticks[1].setAlpha(joypadOpacity);
			}
			
			rudderAndThrottleJoystick.setNeedsUpdate();
		}
		
		for (int i=0; i<joysticks.length; ++i) {
		    JoystickBase joystick = joysticks[i];
		    
			if (joystick != null) {
				joystick.setInverseYWhenDraw(true);

				int margin = context.getResources().getDimensionPixelSize(R.dimen.hud_joy_margin);
				
				joystick.setMargin(0, margin, bottomBarBg.getHeight() + margin, margin);
			}
		}
		
		renderer.removeSprite(JOY_ID_LEFT);
		renderer.removeSprite(JOY_ID_RIGHT);

		if (rollAndPitchJoystick != null) {
			if (isLeftHanded) {
				renderer.addSprite(JOY_ID_RIGHT, rollAndPitchJoystick);
			}
			else{
				renderer.addSprite(JOY_ID_LEFT, rollAndPitchJoystick);
			}
		}
		
		if (rudderAndThrottleJoystick != null) {
			if (isLeftHanded) {
				renderer.addSprite(JOY_ID_LEFT, rudderAndThrottleJoystick);
			}
			else{
				renderer.addSprite(JOY_ID_RIGHT, rudderAndThrottleJoystick);
			}
		}
	}
	
	public JoystickBase getRollAndPitchJoystick()
	{
		return joysticks[0];
	}
	
	public JoystickBase getRudderAndThrottleJoystick()
	{
			return joysticks[1];
	}
	
	public void setInterfaceOpacity(float opacity)
	{
		if (opacity < 0 || opacity > 100.0f) {
			Log.w(TAG, "Can't set interface opacity. Invalid value: " + opacity);
			return;
		}
		
		joypadOpacity = opacity / 100f;
		
		Sprite joystick = renderer.getSprite(JOY_ID_LEFT);
		joystick.setAlpha(joypadOpacity);
		
		joystick = renderer.getSprite(JOY_ID_RIGHT);
		joystick.setAlpha(joypadOpacity);
	}

	public void setBatteryValue(final int percent)
	{
		if (percent > 100 || percent < 0) {
			Log.w(TAG, "Can't set battery value. Invalid value " + percent);
			return;
		}
				
		int imgNum = Math.round((float) percent / 100.0f * 3.0f);

		txtBatteryStatus.setText(percent + "%");
		
		if (imgNum < 0)
			imgNum = 0;
		
		if (imgNum > 3) 
			imgNum = 3;

		if (batteryIndicator != null) {
			batteryIndicator.setValue(imgNum);
		}
	}
	
	public void setSettingsButtonEnabled(boolean enabled)
	{
		settingsBtn.setEnabled(enabled);
	}
	
	public void setDoubleTapClickListener(OnDoubleTapListener listener) 
	{
		gestureDetector.setOnDoubleTapListener(listener);	
	}
	
	public void onPause()
	{
		if (glView != null) {
			glView.onPause();
		}
		
		deviceOrientationManager.pause();
	}
	
	public void onResume()
	{
		if (glView != null) {
			glView.onResume();
		}
		
		deviceOrientationManager.resume();
	}

    //glView onTouch Event handler
	public boolean onTouch(View v, MotionEvent event)
	{
		boolean result = false;
		
		for (int i=0; i<buttons.length; ++i) {
			if (buttons[i].processTouch(v, event)) {
				result = true;
				break;
			}
		}
		
		if (result != true) {	
			gestureDetector.onTouchEvent(event);
			
			for (int i=0; i<joysticks.length; ++i) {
				JoystickBase joy = joysticks[i];
				if (joy != null) {
					if (joy.processTouch(v, event)) {
						result = true;
					}
				}
			}
		}
		
		return result;
	}
	
	public void onDestroy()
	{
	    renderer.clearSprites();
	    deviceOrientationManager.destroy();
	}

	public boolean onDown(MotionEvent e) 
	{
		return false;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) 
	{
		return false;
	}

	public void onLongPress(MotionEvent e) 
	{
    	// Left unimplemented	
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) 
	{
		return false;
	}

	public void onShowPress(MotionEvent e) 
	{
	    // Left unimplemented
	}

	public boolean onSingleTapUp(MotionEvent e) 
	{
		return false;
	}
	
	public View getRootView()
	{
	    if (glView != null) {
	        return glView;
	    }
	    
	    Log.w(TAG, "Can't find root view");
	    return null;
	}

	@Override
	public void interfaceOpacityValueDidChange(float newValue) {
		setInterfaceOpacity(newValue);
	}

	@Override
	public void leftHandedValueDidChange(boolean isLeftHanded) {
		this.isLeftHanded = isLeftHanded;

		setJoysticks();
		
		Log.e(TAG, "THRO:" + throttleChannel.getValue());
		
		getRudderAndThrottleJoystick().setYValue(throttleChannel.getValue());
	}

	@Override
	public void accModeValueDidChange(boolean isAccMode) {
		this.isAccMode = isAccMode;
		
		initJoystickListeners();
		
		if (isAccMode) {
			initJoysticks(JoystickType.ACCELERO);
		}
		else{
			initJoysticks(JoystickType.ANALOGUE);
		}
	}
	
    
	@Override
	public void headfreeModeValueDidChange(boolean isHeadfree) {
		if (settings.isHeadFreeMode()) {
			aux1Channel.setValue(1);
		}
		else {
			aux1Channel.setValue(-1);
		}
	}
	
	@Override
	public void aileronAndElevatorDeadBandValueDidChange(float newValue) {
	    JoystickBase rollAndPitchJoyStick  = getRollAndPitchJoystick();
        
	    rollAndPitchJoyStick.setXDeadBand(newValue);
	    rollAndPitchJoyStick.setYDeadBand(newValue);
	}

	@Override
	public void rudderDeadBandValueDidChange(float newValue) {
	    JoystickBase rudderAndThrottleStick  = getRudderAndThrottleJoystick();
        
	    rudderAndThrottleStick.setXDeadBand(newValue);
	}

	@Override
	public void onDeviceOrientationChanged(float[] orientation,
			float magneticHeading, int magnetoAccuracy) {
		  if (rollAndPitchJoystickPressed == false) {
	            pitchBase = orientation[PITCH];
	            rollBase = orientation[ROLL];
                aileronChannel.setValue(0.0f);
                elevatorChannel.setValue(0.0f);
                
                Log.d(TAG, "before pressed ROLL:" + rollBase + ",PITCH:" + pitchBase);
	      }
		  else {
	            float x = (orientation[PITCH] - pitchBase);
	            float y = (orientation[ROLL] - rollBase);

	            if (isAccMode) {
					Log.d(TAG, "ROLL:" + (-x) + ",PITCH:" + y);
					
					if (Math.abs(x) > ACCELERO_TRESHOLD || Math.abs(y) > ACCELERO_TRESHOLD) {
		                aileronChannel.setValue(-x);
		                elevatorChannel.setValue(y);
					}
				}
	        }
	}
}
